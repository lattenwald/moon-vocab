package org.lattenwald.moonvocab.ui.card

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.work.WorkManager
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lattenwald.moonvocab.App
import org.lattenwald.moonvocab.data.db.AppDatabase
import org.lattenwald.moonvocab.data.db.BookEntity
import org.lattenwald.moonvocab.domain.card.CardExtras
import org.lattenwald.moonvocab.domain.card.CardField
import org.lattenwald.moonvocab.domain.card.CardFields
import org.lattenwald.moonvocab.domain.card.CardStore
import org.lattenwald.moonvocab.domain.card.with
import org.lattenwald.moonvocab.domain.cardpage.CardEditState
import org.lattenwald.moonvocab.domain.cardpage.CardEditor
import org.lattenwald.moonvocab.domain.cardpage.CardEffect
import org.lattenwald.moonvocab.domain.cardpage.CardIntent
import org.lattenwald.moonvocab.domain.cardpage.CardMessage
import org.lattenwald.moonvocab.domain.cardpage.CardRetranslator
import org.lattenwald.moonvocab.domain.cardpage.NO_FIELDS
import org.lattenwald.moonvocab.domain.cardpage.reduce
import org.lattenwald.moonvocab.domain.import.CardSender
import org.lattenwald.moonvocab.domain.import.ImportRequest
import org.lattenwald.moonvocab.work.ImportWork

class CardPageViewModel(
    private val cardId: Long,
    private val db: AppDatabase,
    private val editor: CardEditor,
    private val sender: CardSender,
    private val store: CardStore,
    private val retranslator: CardRetranslator,
    private val workManager: WorkManager,
    private val io: CoroutineDispatcher,
    private val handle: SavedStateHandle,
) : ViewModel() {
    // Snapshot state, not a StateFlow: text fields need their value back in the same frame.
    var state by mutableStateOf(restoredDraft())
        private set
    var book by mutableStateOf<BookEntity?>(null)
        private set

    private val messageChannel = Channel<CardMessage>(Channel.BUFFERED)

    val messages: Flow<CardMessage> = messageChannel.receiveAsFlow()

    private var retranslation: Job? = null

    init {
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(ImportWork.NAME)
                .catch { e -> Log.e(TAG, "import state query failed", e) }
                .collect { infos -> dispatch(CardIntent.ImportRunning(ImportWork.state(infos.firstOrNull()).running)) }
        }
        run(CardEffect.Open(null))
    }

    fun dispatch(intent: CardIntent) {
        val step = reduce(state, intent)
        state = step.state
        keepDraft()
        step.message?.let { messageChannel.trySend(it) }
        step.effects.forEach(::run)
        if (intent is CardIntent.Opened) loadBook()
    }

    private fun run(effect: CardEffect) {
        when (effect) {
            CardEffect.CancelRetranslate -> retranslation?.cancel()
            CardEffect.StartImport -> {
                val request = ImportRequest(backup = null, styles = emptySet(), onlyBookFileKey = null, sendToAnki = false)
                ImportWork.enqueue(workManager, ImportWork.input(request))
            }
            else -> {
                val job = viewModelScope.launch {
                    val result = try {
                        withContext(io) { resultOf(effect) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "card action failed", e)
                        CardIntent.Failed(effect, e.message ?: e::class.java.simpleName)
                    }
                    dispatch(result)
                }
                if (effect == CardEffect.Retranslate) retranslation = job
            }
        }
    }

    private suspend fun resultOf(effect: CardEffect): CardIntent = when (effect) {
        is CardEffect.Open -> CardIntent.Opened(editor.open(cardId), effect.after)
        is CardEffect.SaveLocal -> CardIntent.LocalSaved(editor.saveLocal(cardId, effect.fields, effect.extras), effect.then)
        is CardEffect.Save -> CardIntent.SaveDone(editor.save(cardId, effect.baseline, effect.fields, effect.extras))
        CardEffect.Send -> CardIntent.SendDone(sender.send(listOf(cardId)))
        CardEffect.Discard -> CardIntent.DiscardDone(store.discard(listOf(cardId)).isNotEmpty())
        CardEffect.Restore -> {
            store.restore(listOf(cardId))
            CardIntent.RestoreDone
        }
        CardEffect.Delete -> CardIntent.DeleteDone(sender.deleteFromAnki(cardId))
        CardEffect.Retranslate -> CardIntent.Retranslated(retranslator.retranslate(cardId))
        CardEffect.Forget -> CardIntent.Forgotten(store.forgetForRetranslation(listOf(cardId)))
        CardEffect.UndoSend -> {
            val r = sender.undoSend(listOf(cardId))
            r.error?.let { Log.w(TAG, "undo send stopped: $it") }
            CardIntent.UndoDone(r)
        }
        CardEffect.CancelRetranslate, CardEffect.StartImport -> error("$effect runs inline")
    }

    private fun restoredDraft(): CardEditState {
        val form = handle.get<Array<String>>(FORM) ?: return CardEditState()
        val baseline = handle.get<Array<String>>(BASELINE) ?: return CardEditState()
        val extras = handle.get<String>(LEMMA)?.let { CardExtras(it, handle[WIDENED] ?: false) }
        return CardEditState(baseline = fieldsOf(baseline), form = fieldsOf(form), extras = extras, editing = handle[EDITING] ?: false)
    }

    private fun keepDraft() {
        val s = state
        if (!s.dirty) {
            DRAFT_KEYS.forEach { handle.remove<Any>(it) }
            return
        }
        handle[FORM] = s.form.toArray()
        handle[BASELINE] = s.baseline.toArray()
        handle[LEMMA] = s.extras?.lemma
        handle[WIDENED] = s.extras?.widened
        handle[EDITING] = s.editing
    }

    private fun loadBook() {
        val bookId = state.card?.bookId ?: return
        if (book?.id == bookId) return
        viewModelScope.launch {
            book = try {
                withContext(io) { db.books().byId(bookId) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "book read failed", e)
                null
            }
        }
    }

    companion object {
        private const val TAG = "CardPageViewModel"
        private const val FORM = "draftForm"
        private const val BASELINE = "draftBaseline"
        private const val LEMMA = "draftLemma"
        private const val WIDENED = "draftWidened"
        private const val EDITING = "draftEditing"
        private val DRAFT_KEYS = listOf(FORM, BASELINE, LEMMA, WIDENED, EDITING)

        fun factory(cardId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as App
                val c = app.container
                CardPageViewModel(cardId, c.db, c.cardEditor, c.sender, c.cards, c.retranslator, c.workManager, c.io, createSavedStateHandle())
            }
        }
    }
}

private fun CardFields.toArray(): Array<String> = CardField.entries.map { this[it] }.toTypedArray()

private fun fieldsOf(values: Array<String>): CardFields =
    CardField.entries.zip(values).fold(NO_FIELDS) { acc, (field, value) -> acc.with(field, value) }

package org.lattenwald.moonvocab.ui.card

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.work.WorkManager
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lattenwald.moonvocab.App
import org.lattenwald.moonvocab.data.anki.AnkiAvailability
import org.lattenwald.moonvocab.data.db.AppDatabase
import org.lattenwald.moonvocab.data.db.BookEntity
import org.lattenwald.moonvocab.data.db.CardEntity
import org.lattenwald.moonvocab.data.llm.LlmException
import org.lattenwald.moonvocab.domain.card.CardExtras
import org.lattenwald.moonvocab.domain.card.CardField
import org.lattenwald.moonvocab.domain.card.CardFields
import org.lattenwald.moonvocab.domain.card.CardStatus
import org.lattenwald.moonvocab.domain.card.FormResult
import org.lattenwald.moonvocab.domain.card.cardFields
import org.lattenwald.moonvocab.domain.card.mergeAfterConflict
import org.lattenwald.moonvocab.domain.card.parseForm
import org.lattenwald.moonvocab.domain.card.toForm
import org.lattenwald.moonvocab.domain.cardpage.CardEditor
import org.lattenwald.moonvocab.domain.cardpage.CardRetranslator
import org.lattenwald.moonvocab.domain.cardpage.OpenResult
import org.lattenwald.moonvocab.domain.cardpage.RetranslateResult
import org.lattenwald.moonvocab.domain.cardpage.SaveResult
import org.lattenwald.moonvocab.domain.import.CardSender
import org.lattenwald.moonvocab.domain.import.ImportRequest
import org.lattenwald.moonvocab.domain.import.SendResult
import org.lattenwald.moonvocab.domain.inbox.InboxActions
import org.lattenwald.moonvocab.work.ImportWork

sealed interface CardSource {
    data object Fresh : CardSource
    data class Local(val unavailable: AnkiAvailability?) : CardSource
    data object Deleted : CardSource
}

enum class CardDialog { None, ConfirmDelete, ConfirmLeave }

private val NO_FIELDS = CardFields("", "", "", "", "", "")

data class CardPageState(
    val card: CardEntity? = null,
    val book: BookEntity? = null,
    val source: CardSource = CardSource.Fresh,
    val baseline: CardFields = NO_FIELDS,
    val form: CardFields = NO_FIELDS,
    val extras: CardExtras? = null,
    val editing: Boolean = false,
    val invalid: Set<CardField> = emptySet(),
    val busy: Boolean = true,
    val importRunning: Boolean = false,
    val dialog: CardDialog = CardDialog.None,
    val gone: Boolean = false,
) {
    val dirty: Boolean get() = extras != null || form != baseline.toForm()
    val parsed: FormResult get() = parseForm(form, baseline)
}

sealed interface CardPageEvent {
    data object Sent : CardPageEvent
    data object Discarded : CardPageEvent
    data object Saved : CardPageEvent
    data class Conflict(val fields: Set<CardField>) : CardPageEvent
    data object NoteGone : CardPageEvent
    data object NotPending : CardPageEvent
    data object Deleted : CardPageEvent
    data object DeleteRefused : CardPageEvent
    data object Retranslated : CardPageEvent
    data class Stopped(val kind: LlmException.Stop.Kind) : CardPageEvent
    data class Unavailable(val availability: AnkiAvailability) : CardPageEvent
    data class SendFailed(val message: String) : CardPageEvent
    data class MissingFields(val fields: List<String>) : CardPageEvent
    data class UndoKept(val count: Int) : CardPageEvent
    data class Failed(val message: String) : CardPageEvent
}

class CardPageViewModel(
    private val cardId: Long,
    private val db: AppDatabase,
    private val editor: CardEditor,
    private val sender: CardSender,
    private val actions: InboxActions,
    private val retranslator: CardRetranslator,
    private val workManager: WorkManager,
    private val io: CoroutineDispatcher,
) : ViewModel() {
    // Snapshot state, not a StateFlow: text fields need their value back in the same frame.
    var state by mutableStateOf(CardPageState())
        private set

    private val eventChannel = Channel<CardPageEvent>(Channel.BUFFERED)

    val events: Flow<CardPageEvent> = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(ImportWork.NAME)
                .catch { e -> Log.e(TAG, "import state query failed", e) }
                .collect { infos -> state = state.copy(importRunning = ImportWork.state(infos.firstOrNull()).running) }
        }
        launchBusy { load() }
    }

    fun edit(field: CardField, value: String) = setForm(state.form.with(field, value))

    fun startEditing() {
        if (state.card?.status in EDITABLE) state = state.copy(editing = true)
    }

    fun done() {
        val s = state
        val fields = when (val parsed = s.parsed) {
            is FormResult.Invalid -> {
                state = s.copy(invalid = parsed.fields)
                return
            }
            is FormResult.Ok -> parsed.fields
        }
        if (s.card?.status != CardStatus.PENDING || !s.dirty) {
            state = s.copy(editing = false, invalid = emptySet())
            return
        }
        act { saveLocal(fields, s.extras) }
    }

    fun send() = act {
        val s = state
        if (s.dirty) {
            val fields = (s.parsed as? FormResult.Ok)?.fields ?: return@act
            if (!saveLocal(fields, s.extras)) return@act
        }
        sendNow()
    }

    fun sendAsNew() = act { sendNow() }

    fun discard() = act {
        val changed = withContext(io) { actions.discard(listOf(cardId)) }
        load()
        if (changed.isNotEmpty()) eventChannel.send(CardPageEvent.Discarded)
    }

    fun restore() = act {
        withContext(io) { actions.restore(listOf(cardId)) }
        load()
    }

    fun save() = act {
        val s = state
        val fields = when (val parsed = s.parsed) {
            is FormResult.Invalid -> {
                state = s.copy(editing = true, invalid = parsed.fields)
                return@act
            }
            is FormResult.Ok -> parsed.fields
        }
        val event = when (val r = withContext(io) { editor.save(cardId, s.baseline, fields, s.extras) }) {
            is SaveResult.Saved -> {
                show(r.card)
                CardPageEvent.Saved
            }
            is SaveResult.Conflict -> {
                takeFresh(r.card, r.fields)
                CardPageEvent.Conflict(r.fields)
            }
            is SaveResult.Deleted -> {
                show(r.card, CardSource.Deleted)
                CardPageEvent.NoteGone
            }
            is SaveResult.Unavailable -> CardPageEvent.Unavailable(r.availability)
            is SaveResult.Failed ->
                if (r.missingFields.isNotEmpty()) CardPageEvent.MissingFields(r.missingFields) else CardPageEvent.Failed(r.message)
        }
        eventChannel.send(event)
    }

    fun askDelete() {
        state = state.copy(dialog = CardDialog.ConfirmDelete)
    }

    fun dismissDialog() {
        state = state.copy(dialog = CardDialog.None)
    }

    fun deleteFromAnki() {
        dismissDialog()
        act {
            val r = withContext(io) { sender.deleteFromAnki(cardId) }
            load()
            val event = when {
                r.deleted -> CardPageEvent.Deleted
                r.unavailable != null -> CardPageEvent.Unavailable(r.unavailable)
                r.error != null -> CardPageEvent.Failed(r.error)
                else -> CardPageEvent.DeleteRefused
            }
            eventChannel.send(event)
        }
    }

    fun translateAgain(onLeave: () -> Unit) {
        when (state.card?.status) {
            CardStatus.FAILED -> if (!state.importRunning) act { forgetAndImport(onLeave) }
            CardStatus.PENDING, CardStatus.SENT -> act { retranslate() }
            else -> Unit
        }
    }

    fun back(onLeave: () -> Unit) {
        val s = state
        if (!s.dirty) return onLeave()
        val fields = (s.parsed as? FormResult.Ok)?.fields
        if (s.card?.status != CardStatus.PENDING || fields == null) {
            state = s.copy(dialog = CardDialog.ConfirmLeave)
            return
        }
        // Not behind busy: leaving must not wait for a translation to come back.
        viewModelScope.launch {
            val saved = try {
                val saved = withContext(io) { editor.saveLocal(cardId, fields, s.extras) } != null
                if (!saved && keepIfSent()) state = state.copy(dialog = CardDialog.ConfirmLeave)
                saved
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "local save failed", e)
                eventChannel.send(CardPageEvent.Failed(messageOf(e)))
                state = state.copy(dialog = CardDialog.ConfirmLeave)
                return@launch
            }
            if (saved) onLeave()
        }
    }

    fun undo(event: CardPageEvent) {
        when (event) {
            CardPageEvent.Sent -> act { undoSend() }
            CardPageEvent.Discarded -> restore()
            else -> Unit
        }
    }

    private suspend fun load() {
        val opened = withContext(io) { editor.open(cardId) }
        val (card, source) = when (opened) {
            is OpenResult.Fresh -> opened.card to CardSource.Fresh
            is OpenResult.Local -> opened.card to CardSource.Local(opened.unavailable)
            is OpenResult.Deleted -> opened.card to CardSource.Deleted
            OpenResult.Gone -> {
                state = state.copy(gone = true)
                return
            }
        }
        val book = state.book?.takeIf { it.id == card.bookId } ?: withContext(io) { db.books().byId(card.bookId) }
        state = state.copy(book = book)
        show(card, source)
        if (opened is OpenResult.Local && opened.error != null) eventChannel.send(CardPageEvent.Failed(opened.error))
    }

    private fun show(card: CardEntity, source: CardSource = state.source) {
        val fields = card.cardFields()
        state = state.copy(
            card = card, source = source, baseline = fields, form = fields.toForm(), extras = null, editing = false, invalid = emptySet(),
        )
    }

    private fun takeFresh(fresh: CardEntity, conflicting: Set<CardField>) {
        val s = state
        val freshFields = fresh.cardFields()
        state = s.copy(card = fresh, baseline = freshFields)
        setForm(mergeAfterConflict(s.form, s.baseline, freshFields, conflicting))
    }

    private fun setForm(form: CardFields) {
        val s = state.copy(form = form)
        state = s.copy(invalid = (s.parsed as? FormResult.Invalid)?.fields.orEmpty())
    }

    private suspend fun saveLocal(fields: CardFields, extras: CardExtras?): Boolean {
        val saved = withContext(io) { editor.saveLocal(cardId, fields, extras) }
        if (saved == null) keepIfSent() else show(saved)
        return saved != null
    }

    private suspend fun keepIfSent(): Boolean {
        val before = state
        load()
        val now = state
        if (now.gone) return false
        if (now.card?.status != CardStatus.SENT) {
            eventChannel.send(CardPageEvent.NotPending)
            return false
        }
        state = now.copy(extras = before.extras, editing = true)
        setForm(mergeAfterConflict(before.form, before.baseline, now.baseline, emptySet()))
        return true
    }

    private suspend fun sendNow() {
        val event = try {
            sendEvent(withContext(io) { sender.send(listOf(cardId)) })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "send failed", e)
            CardPageEvent.SendFailed(messageOf(e))
        }
        load()
        event?.let { eventChannel.send(it) }
    }

    private fun sendEvent(r: SendResult): CardPageEvent? = when {
        r.unavailable != null -> CardPageEvent.Unavailable(r.unavailable)
        cardId in r.sentIds -> CardPageEvent.Sent
        r.missingFields.isNotEmpty() -> CardPageEvent.MissingFields(r.missingFields)
        r.error != null -> CardPageEvent.SendFailed(r.error)
        else -> null
    }

    private suspend fun undoSend() {
        val r = withContext(io) { sender.undoSend(listOf(cardId)) }
        r.error?.let { Log.w(TAG, "undo send stopped: $it") }
        load()
        when {
            r.unavailable != null -> eventChannel.send(CardPageEvent.Unavailable(r.unavailable))
            r.kept > 0 -> eventChannel.send(CardPageEvent.UndoKept(r.kept))
        }
    }

    private suspend fun retranslate() {
        val event = when (val r = withContext(io) { retranslator.retranslate(cardId) }) {
            is RetranslateResult.Ok -> {
                val t = r.retranslation
                state = state.copy(extras = t.extras, editing = true)
                setForm(state.form.copy(word = t.word, grammar = t.grammar, translation = t.translation, meaning = t.meaning))
                CardPageEvent.Retranslated
            }
            is RetranslateResult.Stopped -> CardPageEvent.Stopped(r.kind)
            is RetranslateResult.Failed -> CardPageEvent.Failed(r.message)
        }
        eventChannel.send(event)
    }

    private suspend fun forgetAndImport(onLeave: () -> Unit) {
        val forgotten = withContext(io) { actions.forgetForRetranslation(listOf(cardId)) }
        if (forgotten == 0) return load()
        val request = ImportRequest(backup = null, styles = emptySet(), onlyBookFileKey = null, sendToAnki = false)
        ImportWork.enqueue(workManager, ImportWork.input(request))
        onLeave()
    }

    private fun act(block: suspend () -> Unit) {
        if (state.busy) return
        state = state.copy(busy = true)
        launchBusy(block)
    }

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "card action failed", e)
                eventChannel.send(CardPageEvent.Failed(messageOf(e)))
            } finally {
                state = state.copy(busy = false)
            }
        }
    }

    private fun messageOf(e: Exception): String = e.message ?: e::class.java.simpleName

    companion object {
        private const val TAG = "CardPageViewModel"

        internal val EDITABLE = setOf(CardStatus.PENDING, CardStatus.SENT)

        fun factory(cardId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as App
                val c = app.container
                CardPageViewModel(cardId, c.db, c.cardEditor, c.sender, c.inboxActions, c.retranslator, c.workManager, c.io)
            }
        }
    }
}

private fun CardFields.with(f: CardField, value: String): CardFields = when (f) {
    CardField.WORD -> copy(word = value)
    CardField.GRAMMAR -> copy(grammar = value)
    CardField.EXCERPT -> copy(excerptHtml = value)
    CardField.TRANSLATION -> copy(translation = value)
    CardField.MEANING -> copy(meaning = value)
    CardField.SENTENCE -> copy(sentenceHtml = value)
}

package org.lattenwald.moonvocab.ui.inbox

import android.util.Log
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lattenwald.moonvocab.App
import org.lattenwald.moonvocab.data.anki.AnkiAvailability
import org.lattenwald.moonvocab.data.anki.AnkiGateway
import org.lattenwald.moonvocab.data.db.AppDatabase
import org.lattenwald.moonvocab.data.settings.Settings
import org.lattenwald.moonvocab.domain.card.CardStore
import org.lattenwald.moonvocab.domain.import.CardSender
import org.lattenwald.moonvocab.domain.import.ImportRequest
import org.lattenwald.moonvocab.domain.import.SendResult
import org.lattenwald.moonvocab.domain.inbox.InboxList
import org.lattenwald.moonvocab.domain.inbox.InboxFilter
import org.lattenwald.moonvocab.domain.inbox.Selection
import org.lattenwald.moonvocab.domain.inbox.dragOver
import org.lattenwald.moonvocab.domain.inbox.endDrag
import org.lattenwald.moonvocab.domain.inbox.inboxList
import org.lattenwald.moonvocab.domain.inbox.retain
import org.lattenwald.moonvocab.domain.inbox.startDrag
import org.lattenwald.moonvocab.domain.inbox.toggle
import org.lattenwald.moonvocab.domain.inbox.toggleAll
import org.lattenwald.moonvocab.domain.inbox.toggleGroup
import org.lattenwald.moonvocab.work.ImportState
import org.lattenwald.moonvocab.work.ImportWork

data class InboxState(
    val list: InboxList = inboxList(emptyList(), InboxFilter.All),
    val selection: Selection = Selection(),
    val import: ImportState = ImportState(),
    val anki: AnkiAvailability? = null,
    val missingFields: List<String> = emptyList(),
    val loaded: Boolean = false,
)

sealed interface InboxEvent {
    data class Sent(val ids: List<Long>, val stayed: Int, val error: String? = null) : InboxEvent
    data class Stayed(val count: Int) : InboxEvent
    data class Discarded(val ids: List<Long>) : InboxEvent
    data class Unavailable(val availability: AnkiAvailability) : InboxEvent
    data class SendFailed(val message: String) : InboxEvent
    data class UndoKept(val count: Int) : InboxEvent
    data class Retranslating(val count: Int) : InboxEvent
    data class Failed(val message: String) : InboxEvent
}

class InboxViewModel(
    private val db: AppDatabase,
    private val settings: Settings,
    private val anki: AnkiGateway,
    private val workManager: WorkManager,
    private val store: CardStore,
    private val sender: CardSender,
    private val io: CoroutineDispatcher,
) : ViewModel(), SelectHandler {
    private val filter = MutableStateFlow<InboxFilter>(InboxFilter.All)
    private val availability = MutableStateFlow<AnkiAvailability?>(null)
    private val selection = MutableStateFlow(Selection())
    override val selecting: Boolean get() = selection.value.active
    private val missingFields = MutableStateFlow<List<String>>(emptyList())
    private val eventChannel = Channel<InboxEvent>(Channel.BUFFERED)

    val events: Flow<InboxEvent> = eventChannel.receiveAsFlow()

    private val rows = db.cards().observeInbox().catch { e ->
        Log.e(TAG, "inbox query failed", e)
        emit(emptyList())
    }

    private val list = combine(rows, filter) { r, f ->
        inboxList(r, f).also { if (it.filter != f) filter.compareAndSet(f, InboxFilter.All) }
    }.onEach { l -> selection.update { it.retain(l.visibleIds.toSet()) } }

    val state: StateFlow<InboxState> = combine(
        list, selection, workManager.getWorkInfosForUniqueWorkFlow(ImportWork.NAME), availability, missingFields,
    ) { l, s, infos, a, m ->
        InboxState(list = l, selection = s, import = ImportWork.state(infos.firstOrNull()), anki = a, missingFields = m, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InboxState())

    private val visibleIds: List<Long> get() = state.value.list.visibleIds

    private val _importSheetRequested = MutableStateFlow(false)
    val importSheetRequested: StateFlow<Boolean> = _importSheetRequested.asStateFlow()

    /** Opens the import sheet when the Inbox next shows. The caller first calls the sheet's `open` or `change`. */
    fun requestImportSheet() {
        _importSheetRequested.value = true
    }

    fun consumeImportSheetRequest() {
        _importSheetRequested.value = false
    }

    fun setFilter(f: InboxFilter) {
        filter.value = f
    }

    fun refreshAvailability() {
        viewModelScope.launch {
            try {
                availability.value = withContext(io) { anki.availability() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AnkiDroid availability check failed", e)
            }
        }
    }

    fun dismissResult() {
        workManager.pruneWork()
    }

    fun retryFailed() {
        if (!state.value.import.running) ImportWork.enqueue(workManager, ImportWork.retryInput())
    }

    override fun startDrag(id: Long) {
        val order = visibleIds
        selection.update { it.startDrag(order, id) }
    }

    // The drag's order is a snapshot: a card that left the Inbox mid-drag must not come back.
    override fun dragOver(id: Long) {
        val visible = visibleIds.toSet()
        selection.update { it.dragOver(id).retain(visible) }
    }

    override fun endDrag() = selection.update { it.endDrag() }

    fun toggle(id: Long) = selection.update { it.toggle(id) }

    override fun toggleGroup(bookId: Long) {
        val ids = state.value.list.groups.firstOrNull { it.bookId == bookId }?.cards?.map { it.id } ?: return
        selection.update { it.toggleGroup(ids) }
    }

    fun toggleAll() {
        val ids = visibleIds
        selection.update { it.toggleAll(ids) }
    }

    fun clearSelection() {
        selection.value = Selection()
    }

    fun send(ids: List<Long>, onStayed: () -> Unit = {}) {
        if (ids.isEmpty()) return
        clearSelection()
        viewModelScope.launch {
            val event = try {
                sendEvent(ids, withContext(io) { sender.send(ids) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "send failed", e)
                InboxEvent.SendFailed(messageOf(e))
            }
            if (event !is InboxEvent.Sent) onStayed()
            eventChannel.send(event)
        }
    }

    private fun sendEvent(ids: List<Long>, r: SendResult): InboxEvent {
        r.unavailable?.let { return unavailable(it) }
        missingFields.value = r.missingFields
        return when {
            r.sentIds.isNotEmpty() -> InboxEvent.Sent(r.sentIds, stayed = r.skipped, error = r.error)
            r.error != null -> InboxEvent.SendFailed(r.error)
            else -> InboxEvent.Stayed(ids.size)
        }
    }

    private fun unavailable(a: AnkiAvailability): InboxEvent {
        availability.value = a
        return InboxEvent.Unavailable(a)
    }

    fun discard(ids: List<Long>, onStayed: () -> Unit = {}) {
        if (ids.isEmpty()) return
        clearSelection()
        viewModelScope.launch {
            val changed = try {
                withContext(io) { store.discard(ids) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "discard failed", e)
                onStayed()
                eventChannel.send(InboxEvent.Failed(messageOf(e)))
                return@launch
            }
            if (changed.isEmpty()) onStayed() else eventChannel.send(InboxEvent.Discarded(changed))
        }
    }

    fun translateAgain(ids: List<Long>) {
        if (ids.isEmpty() || state.value.import.running) return
        clearSelection()
        viewModelScope.launch {
            val forgotten = try {
                withContext(io) { store.forgetForRetranslation(ids) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "translate again failed", e)
                eventChannel.send(InboxEvent.Failed(messageOf(e)))
                return@launch
            }
            if (forgotten == 0) return@launch
            val request = ImportRequest(backup = null, styles = emptySet(), onlyBookFileKey = null, sendToAnki = false)
            ImportWork.enqueue(workManager, ImportWork.input(request))
            eventChannel.send(InboxEvent.Retranslating(forgotten))
        }
    }

    fun undo(event: InboxEvent) {
        viewModelScope.launch {
            when (event) {
                is InboxEvent.Sent -> undoSend(event.ids)?.let { eventChannel.send(it) }
                is InboxEvent.Discarded -> try {
                    withContext(io) { store.restore(event.ids) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "restore failed", e)
                    eventChannel.send(InboxEvent.Failed(messageOf(e)))
                }
                else -> Unit
            }
        }
    }

    private suspend fun undoSend(ids: List<Long>): InboxEvent? = try {
        val r = withContext(io) { sender.undoSend(ids) }
        r.error?.let { Log.w(TAG, "undo send stopped: $it") }
        when {
            r.unavailable != null -> unavailable(r.unavailable)
            r.kept > 0 -> InboxEvent.UndoKept(r.kept)
            else -> null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "undo send failed", e)
        InboxEvent.Failed(messageOf(e))
    }

    private fun messageOf(e: Exception): String = e.message ?: e::class.java.simpleName

    companion object {
        private const val TAG = "InboxViewModel"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as App
                val c = app.container
                InboxViewModel(c.db, c.settings, c.anki, c.workManager, c.cards, c.sender, c.io)
            }
        }
    }
}

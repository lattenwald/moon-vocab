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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lattenwald.moonvocab.App
import org.lattenwald.moonvocab.data.anki.AnkiAvailability
import org.lattenwald.moonvocab.data.anki.AnkiGateway
import org.lattenwald.moonvocab.data.db.AppDatabase
import org.lattenwald.moonvocab.data.settings.Settings
import org.lattenwald.moonvocab.domain.inbox.InboxList
import org.lattenwald.moonvocab.domain.inbox.LanguageFilter
import org.lattenwald.moonvocab.domain.inbox.Selection
import org.lattenwald.moonvocab.domain.inbox.inboxList
import org.lattenwald.moonvocab.work.ImportState
import org.lattenwald.moonvocab.work.ImportWork

data class InboxState(
    val list: InboxList = inboxList(emptyList(), LanguageFilter.All),
    val selection: Selection = Selection(),
    val import: ImportState = ImportState(),
    val anki: AnkiAvailability? = null,
    val missingFields: List<String> = emptyList(),
    val loaded: Boolean = false,
)

class InboxViewModel(
    private val db: AppDatabase,
    private val settings: Settings,
    private val anki: AnkiGateway,
    private val workManager: WorkManager,
    private val io: CoroutineDispatcher,
) : ViewModel() {
    private val filter = MutableStateFlow<LanguageFilter>(LanguageFilter.All)
    private val availability = MutableStateFlow<AnkiAvailability?>(null)

    private val rows = db.cards().observeInbox().catch { e ->
        Log.e(TAG, "inbox query failed", e)
        emit(emptyList())
    }

    val state: StateFlow<InboxState> = combine(
        rows, filter, workManager.getWorkInfosForUniqueWorkFlow(ImportWork.NAME), availability,
    ) { r, f, infos, a ->
        InboxState(list = inboxList(r, f), import = ImportWork.state(infos.firstOrNull()), anki = a, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InboxState())

    fun setFilter(f: LanguageFilter) {
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

    companion object {
        private const val TAG = "InboxViewModel"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as App
                val c = app.container
                InboxViewModel(c.db, c.settings, c.anki, c.workManager, c.io)
            }
        }
    }
}

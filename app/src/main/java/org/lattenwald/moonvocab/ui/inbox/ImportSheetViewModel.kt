package org.lattenwald.moonvocab.ui.inbox

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.lattenwald.moonvocab.App
import org.lattenwald.moonvocab.data.backup.BackupFinder
import org.lattenwald.moonvocab.data.backup.FoundBackup
import org.lattenwald.moonvocab.data.db.AppDatabase
import org.lattenwald.moonvocab.data.settings.Settings
import org.lattenwald.moonvocab.domain.import.ImportRequest
import org.lattenwald.moonvocab.domain.mark.SheetCounts
import org.lattenwald.moonvocab.domain.mark.StyleCombo
import org.lattenwald.moonvocab.domain.mark.defaultStyles
import org.lattenwald.moonvocab.domain.mark.sheetCounts
import org.lattenwald.moonvocab.work.ImportRuns
import org.lattenwald.moonvocab.work.Start

sealed interface SheetState {
    data object Searching : SheetState
    data object NoFolder : SheetState
    data object FolderUnreadable : SheetState
    data class NoBackup(val skipped: List<String>, val pickIncomplete: Boolean = false) : SheetState
    data class Ready(
        val backup: FoundBackup,
        val skipped: List<String>,
        val counts: SheetCounts,
        val selected: Set<StyleCombo>,
        val sendToAnki: Boolean,
        val pickIncomplete: Boolean = false,
        val importRunning: Boolean = false,
    ) : SheetState
    data class Error(val message: String) : SheetState
}

class ImportSheetViewModel(
    private val resolver: ContentResolver,
    private val settings: Settings,
    private val db: AppDatabase,
    private val finder: BackupFinder,
    private val runs: ImportRuns,
    private val io: CoroutineDispatcher,
) : ViewModel() {
    private val lock = Mutex() // actions run in order, so start() follows every queued settings write
    private var search: Job? = null
    private var knownKeys: Set<String> = emptySet()
    private var waiting = 0

    private val mutableState = MutableStateFlow<SheetState>(SheetState.Searching)
    val state: StateFlow<SheetState> = mutableState.asStateFlow()

    fun open() = search {
        val tree = settings.current().backupTree
        if (tree == null) SheetState.NoFolder else find(tree)
    }

    fun openIfIdle() { // a recreated sheet keeps a pending picker result and the shown backup
        if (search == null) open()
    }

    fun chooseFolder(uri: Uri) = search {
        withContext(io) { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        settings.update { it.copy(backupTree = uri) }
        find(uri)
    }

    fun change(uri: Uri) {
        val before = state.value
        search {
            val backup = finder.open(uri)
            when {
                backup != null -> ready(backup, skipped = emptyList())
                before is SheetState.Ready -> before.copy(pickIncomplete = true)
                before is SheetState.NoBackup -> before.copy(pickIncomplete = true)
                else -> SheetState.NoBackup(emptyList(), pickIncomplete = true)
            }
        }
    }

    fun share(uri: Uri) = search { // no fallback as in change: the shown state may be a long-closed session's
        val backup = try {
            finder.open(uri)
        } catch (e: SecurityException) { // a lapsed grant, or a sender that didn't grant read access
            Log.w(TAG, "shared file unreadable", e)
            null
        } catch (e: IOException) {
            Log.w(TAG, "shared file unreadable", e)
            null
        }
        backup?.let { ready(it, skipped = emptyList()) } ?: SheetState.NoBackup(emptyList(), pickIncomplete = true)
    }

    fun toggle(combo: StyleCombo) {
        val ready = state.value as? SheetState.Ready ?: return
        val styles = if (combo in ready.selected) ready.selected - combo else ready.selected + combo
        mutableState.value = ready.copy(selected = styles, counts = counts(ready.backup, styles))
        act { settings.update { it.copy(styles = styles) } }
    }

    fun setSendToAnki(send: Boolean) {
        val ready = state.value as? SheetState.Ready ?: return
        mutableState.value = ready.copy(sendToAnki = send)
        act { settings.update { it.copy(reviewFirst = !send) } }
    }

    fun start(onStarted: () -> Unit) {
        act { // queued behind pending settings writes; a failed one leaves Error
            val ready = state.value as? SheetState.Ready ?: return@act
            val request = ImportRequest(ready.backup.file, ready.selected, onlyBookFileKey = null, sendToAnki = ready.sendToAnki)
            when (runs.start(request)) {
                Start.STARTED -> onStarted()
                Start.ALREADY_RUNNING -> mutableState.value = ready.copy(importRunning = true)
            }
        }
    }

    private suspend fun find(tree: Uri): SheetState {
        val search = try {
            finder.newest(tree)
        } catch (e: SecurityException) {
            Log.w(TAG, "backup folder unreadable", e)
            return SheetState.FolderUnreadable
        } catch (e: IOException) { // a deleted or moved folder can't be listed
            Log.w(TAG, "backup folder unreadable", e)
            return SheetState.FolderUnreadable
        }
        val chosen = search.chosen ?: return SheetState.NoBackup(search.skipped)
        return ready(chosen, search.skipped)
    }

    private suspend fun ready(backup: FoundBackup, skipped: List<String>): SheetState {
        val (keys, count) = withContext(io) { db.marks().keys().toHashSet() to db.marks().countWithoutCard() }
        val s = settings.current()
        val styles = defaultStyles(s.styles, backup.marks.mapTo(HashSet()) { it.combo })
        if (styles != s.styles) settings.update { it.copy(styles = styles) }
        knownKeys = keys
        waiting = count
        return SheetState.Ready(backup, skipped, counts(backup, styles), styles, sendToAnki = !s.reviewFirst)
    }

    private fun counts(backup: FoundBackup, selected: Set<StyleCombo>) = sheetCounts(backup.marks, knownKeys, selected, waiting)

    private fun search(block: suspend () -> SheetState) {
        search?.cancel()
        mutableState.value = SheetState.Searching
        search = act {
            val next = block()
            currentCoroutineContext().ensureActive()
            mutableState.value = next
        }
    }

    private fun act(block: suspend () -> Unit): Job = viewModelScope.launch {
        try {
            lock.withLock { block() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "import sheet action failed", e)
            if (isActive) mutableState.value = SheetState.Error(e.message ?: e::class.java.simpleName)
        }
    }

    companion object {
        private const val TAG = "ImportSheetViewModel"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as App
                val c = app.container
                ImportSheetViewModel(app.contentResolver, c.settings, c.db, c.backupFinder, c.importRuns, c.io)
            }
        }
    }
}

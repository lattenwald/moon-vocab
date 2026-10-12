package org.lattenwald.moonvocab.ui.setup

import android.content.ContentResolver
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lattenwald.moonvocab.App
import org.lattenwald.moonvocab.data.backup.BackupFinder
import org.lattenwald.moonvocab.data.backup.SafBackupFolder
import org.lattenwald.moonvocab.data.db.AppDatabase
import org.lattenwald.moonvocab.data.settings.Settings
import org.lattenwald.moonvocab.data.settings.SettingsSnapshot
import org.lattenwald.moonvocab.domain.mark.defaultStyles

sealed interface BackupScan {
    data object Scanning : BackupScan
    data object NoFolder : BackupScan
    data class Found(val tree: Uri, val count: Int, val newest: Long) : BackupScan
    data class Unreadable(val tree: Uri) : BackupScan
}

sealed interface NewMarks {
    data object Counting : NewMarks
    data class Counted(val count: Int) : NewMarks
    data object Failed : NewMarks
}

class SetupViewModel(
    private val resolver: ContentResolver,
    private val settings: Settings,
    private val db: AppDatabase,
    private val finder: BackupFinder,
    private val io: CoroutineDispatcher,
    private val appScope: CoroutineScope,
    private val onDone: () -> Unit,
) : ViewModel() {
    val snapshot: StateFlow<SettingsSnapshot?> = settings.snapshot
        .flowOn(io)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val scans = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val backups: StateFlow<BackupScan> = scans.transformLatest {
        emit(BackupScan.Scanning)
        emit(scan())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupScan.Scanning)

    private val _newMarks = MutableStateFlow<NewMarks>(NewMarks.Counting)
    val newMarks: StateFlow<NewMarks> = _newMarks.asStateFlow()
    private var counting: Job? = null

    fun rescan() = scans.update { it + 1 }

    private suspend fun scan(): BackupScan {
        val tree = withContext(io) { settings.current() }.backupTree ?: return BackupScan.NoFolder
        return try {
            val backups = withContext(io) { SafBackupFolder(resolver, tree).list() }.filter { it.isBackup }
            BackupScan.Found(tree, backups.size, backups.maxOfOrNull { it.lastModified } ?: 0L)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "backup folder unreadable", e)
            BackupScan.Unreadable(tree)
        }
    }

    fun countNew() {
        counting?.cancel()
        _newMarks.value = NewMarks.Counting
        counting = viewModelScope.launch {
            _newMarks.value = try {
                NewMarks.Counted(newMarkCount())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "newest backup unreadable", e)
                NewMarks.Failed
            }
        }
    }

    private suspend fun newMarkCount(): Int {
        val s = withContext(io) { settings.current() }
        val tree = s.backupTree ?: return 0
        val marks = finder.newest(tree).chosen?.marks ?: return 0
        val known = withContext(io) { db.marks().keys().toHashSet() }
        val styles = defaultStyles(s.styles, marks.mapTo(HashSet()) { it.combo })
        return marks.count { it.combo in styles && it.key !in known }
    }

    // In appScope: the setup page leaves the back stack at once.
    fun finish() {
        onDone()
        appScope.launch {
            try {
                settings.update { it.copy(setupDone = true) }
            } catch (e: IOException) {
                Log.e(TAG, "settings write failed", e)
            }
        }
    }

    companion object {
        private const val TAG = "SetupViewModel"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as App
                val c = app.container
                SetupViewModel(app.contentResolver, c.settings, c.db, c.backupFinder, c.io, c.appScope, app::onSetupDone)
            }
        }
    }
}

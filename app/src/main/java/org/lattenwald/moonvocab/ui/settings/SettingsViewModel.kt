package org.lattenwald.moonvocab.ui.settings

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.lattenwald.moonvocab.App
import org.lattenwald.moonvocab.data.anki.AnkiAvailability
import org.lattenwald.moonvocab.data.settings.Settings
import org.lattenwald.moonvocab.data.settings.SettingsSnapshot
import org.lattenwald.moonvocab.data.settings.ThemeMode

fun ThemeMode.nightMode(): Int = when (this) {
    ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
    ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
}

class SettingsViewModel(
    private val resolver: ContentResolver,
    private val settings: Settings,
    val ankiAvailability: StateFlow<AnkiAvailability?>,
    private val io: CoroutineDispatcher,
    private val appScope: CoroutineScope,
) : ViewModel() {
    val snapshot: StateFlow<SettingsSnapshot?> = settings.snapshot
        .flowOn(io)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _readable = MutableStateFlow<Set<Uri>?>(null)
    val readable: StateFlow<Set<Uri>?> = _readable.asStateFlow()

    private val writes = Mutex()

    fun refresh() {
        viewModelScope.launch { refreshReadable() }
    }

    fun setTheme(mode: ThemeMode) {
        write { it.copy(theme = mode) }
        AppCompatDelegate.setDefaultNightMode(mode.nightMode())
    }

    fun setReviewFirst(on: Boolean) = write { it.copy(reviewFirst = on) }

    fun setBackupTree(uri: Uri, onSaved: () -> Unit = {}) = setTree(uri, onSaved) { it.copy(backupTree = uri) }

    fun setBooksTree(uri: Uri) = setTree(uri, onSaved = {}) { it.copy(booksTree = uri) }

    // Saved even without a persisted permission, so its row says it can't be read.
    private fun setTree(uri: Uri, onSaved: () -> Unit, transform: (SettingsSnapshot) -> SettingsSnapshot) {
        appScope.launch {
            try {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                Log.e(TAG, "no persistable permission for $uri", e)
            }
            write(transform).join()
            refreshReadable()
            onSaved()
        }
    }

    private suspend fun refreshReadable() {
        _readable.value = withContext(io) { resolver.persistedUriPermissions.filter { it.isReadPermission }.mapTo(HashSet()) { it.uri } }
    }

    // Undispatched onto a fair lock: writes land in tap order. appScope: leaving the page never drops one.
    private fun write(transform: (SettingsSnapshot) -> SettingsSnapshot) = appScope.launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            writes.withLock { withContext(io) { settings.update(transform) } }
        } catch (e: IOException) {
            Log.e(TAG, "settings write failed", e)
        }
    }

    companion object {
        private const val TAG = "SettingsViewModel"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as App
                val c = app.container
                SettingsViewModel(app.contentResolver, c.settings, c.anki.state, c.io, c.appScope)
            }
        }
    }
}

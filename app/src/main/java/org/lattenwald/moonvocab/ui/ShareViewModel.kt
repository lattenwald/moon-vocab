package org.lattenwald.moonvocab.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

/** Holds a received share until `AppShell` takes it, across activity recreation. */
class ShareViewModel(private val handle: SavedStateHandle) : ViewModel() {
    val pending: StateFlow<Uri?> = handle.getStateFlow(KEY, null)

    fun offer(uri: Uri) {
        handle[KEY] = uri
    }

    fun clear() {
        handle[KEY] = null
    }

    private companion object {
        const val KEY = "sharedBackup"
    }
}

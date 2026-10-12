package org.lattenwald.moonvocab.ui.library

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.lattenwald.moonvocab.App
import org.lattenwald.moonvocab.data.db.AppDatabase
import org.lattenwald.moonvocab.domain.library.Library
import org.lattenwald.moonvocab.domain.library.library

data class LibraryState(val library: Library = library(emptyList()), val loaded: Boolean = false)

class LibraryViewModel(db: AppDatabase, io: CoroutineDispatcher) : ViewModel() {
    val state: StateFlow<LibraryState> = db.cards().observeLibrary()
        .map { LibraryState(library(it), loaded = true) }
        .flowOn(io)
        .catch { e ->
            Log.e(TAG, "library query failed", e)
            emit(LibraryState(loaded = true))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState())

    companion object {
        private const val TAG = "LibraryViewModel"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as App
                val c = app.container
                LibraryViewModel(c.db, c.io)
            }
        }
    }
}

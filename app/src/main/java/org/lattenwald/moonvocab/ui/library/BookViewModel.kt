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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import org.lattenwald.moonvocab.App
import org.lattenwald.moonvocab.data.db.AppDatabase
import org.lattenwald.moonvocab.data.db.BookEntity
import org.lattenwald.moonvocab.domain.library.BookPage
import org.lattenwald.moonvocab.domain.library.bookPage

data class BookState(val book: BookEntity? = null, val page: BookPage = bookPage(emptyList()))

class BookViewModel(bookId: Long, db: AppDatabase, io: CoroutineDispatcher) : ViewModel() {
    val state: StateFlow<BookState> = combine(flow { emit(db.books().byId(bookId)) }, db.cards().observeBook(bookId)) { book, rows ->
        BookState(book, bookPage(rows))
    }
        .flowOn(io)
        .catch { e ->
            Log.e(TAG, "book query failed", e)
            emit(BookState())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookState())

    companion object {
        private const val TAG = "BookViewModel"

        fun factory(bookId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as App
                val c = app.container
                BookViewModel(bookId, c.db, c.io)
            }
        }
    }
}

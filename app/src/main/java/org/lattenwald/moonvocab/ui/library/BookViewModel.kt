package org.lattenwald.moonvocab.ui.library

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lattenwald.moonvocab.App
import org.lattenwald.moonvocab.data.db.AppDatabase
import org.lattenwald.moonvocab.data.db.BookEntity
import org.lattenwald.moonvocab.domain.card.CardStore
import org.lattenwald.moonvocab.domain.library.BookPage
import org.lattenwald.moonvocab.domain.library.bookPage
import org.lattenwald.moonvocab.work.ImportRuns
import org.lattenwald.moonvocab.work.Start

data class BookState(val book: BookEntity? = null, val page: BookPage = bookPage(emptyList()), val importRunning: Boolean = false)

sealed interface BookEvent {
    data object Redoing : BookEvent
    data object ImportRunning : BookEvent
    data class Failed(val message: String) : BookEvent
}

class BookViewModel(
    private val bookId: Long,
    db: AppDatabase,
    private val store: CardStore,
    private val runs: ImportRuns,
    private val io: CoroutineDispatcher,
) : ViewModel() {
    private val eventChannel = Channel<BookEvent>(Channel.BUFFERED)

    val events: Flow<BookEvent> = eventChannel.receiveAsFlow()

    private val running = runs.running.catch { e ->
        Log.e(TAG, "import state query failed", e)
        emit(false)
    }

    val state: StateFlow<BookState> = combine(flow { emit(db.books().byId(bookId)) }, db.cards().observeBook(bookId), running) { book, rows, r ->
        BookState(book, bookPage(rows), r)
    }
        .flowOn(io)
        .catch { e ->
            Log.e(TAG, "book query failed", e)
            emit(BookState())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookState())

    fun redo() {
        val book = state.value.book ?: return
        if (state.value.importRunning) return
        viewModelScope.launch {
            val event = try {
                withContext(io) { store.forgetUnsent(bookId) }
                when (runs.resume(book.fileKey)) {
                    Start.STARTED -> BookEvent.Redoing
                    Start.ALREADY_RUNNING -> BookEvent.ImportRunning
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "redo failed", e)
                BookEvent.Failed(e.message ?: e::class.java.simpleName)
            }
            eventChannel.send(event)
        }
    }

    companion object {
        private const val TAG = "BookViewModel"

        fun factory(bookId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as App
                val c = app.container
                BookViewModel(bookId, c.db, c.cards, c.importRuns, c.io)
            }
        }
    }
}

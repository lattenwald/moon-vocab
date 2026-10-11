package org.lattenwald.moonvocab.domain.import

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.lattenwald.moonvocab.data.anki.AnkiAvailability
import org.lattenwald.moonvocab.data.anki.AnkiGateway
import org.lattenwald.moonvocab.data.anki.ankiFields
import org.lattenwald.moonvocab.data.db.AppDatabase
import org.lattenwald.moonvocab.data.db.BookEntity
import org.lattenwald.moonvocab.data.db.CardEntity
import org.lattenwald.moonvocab.data.settings.Settings
import org.lattenwald.moonvocab.domain.card.CardEvent
import org.lattenwald.moonvocab.domain.card.CardStatus
import org.lattenwald.moonvocab.domain.card.bookTags
import org.lattenwald.moonvocab.domain.card.deckName
import org.lattenwald.moonvocab.domain.card.next

data class SendResult(val sent: Int, val skipped: Int, val unavailable: AnkiAvailability?, val error: String?)

class CardSender(
    private val db: AppDatabase,
    private val anki: AnkiGateway,
    private val settings: Settings,
    private val clock: () -> Long,
) {
    // The import worker and the debug screen can send the same pending cards at once.
    private val mutex = Mutex()

    suspend fun send(cardIds: List<Long>): SendResult = mutex.withLock { sendLocked(cardIds) }

    suspend fun deleteAllCreated(): Int = mutex.withLock { deleteAllCreatedLocked() }

    private suspend fun sendLocked(cardIds: List<Long>): SendResult {
        val availability = anki.availability()
        if (availability != AnkiAvailability.READY) return SendResult(0, cardIds.size, availability, null)
        val wanted = cardIds.toSet()
        val pending = db.cards().byStatus(CardStatus.PENDING).filter { it.id in wanted }
        val skipped = wanted.size - pending.size
        if (pending.isEmpty()) return SendResult(0, skipped, null, null)

        var sent = 0
        try {
            val s = settings.current()
            val (mid, version) = anki.ensureNoteType(s.noteTypeId, s.noteTypeVersion)
            if (mid != s.noteTypeId || version != s.noteTypeVersion) {
                settings.update { it.copy(noteTypeId = mid, noteTypeVersion = version) }
            }
            val books = HashMap<Long, BookEntity>()
            val decks = HashMap<String, Long>()
            for (card in pending) {
                val book = books.getOrPut(card.bookId) { checkNotNull(db.books().byId(card.bookId)) { "no book ${card.bookId}" } }
                val deck = deckName(s.deckPattern, card.sourceLanguage, s.deckOverride)
                val deckId = decks.getOrPut(deck) { anki.ensureDeck(deck) }
                val noteId = anki.add(mid, deckId, card.ankiFields(bookLine(book)), bookTags(s.tagPattern, book.title))
                save(
                    card.copy(
                        status = next(card.status, CardEvent.SEND), ankiNoteId = noteId, sentAt = clock(), ankiMod = null, error = null,
                    ),
                )
                sent++
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return SendResult(sent, skipped, null, e.message ?: e::class.simpleName)
        }
        return SendResult(sent, skipped, null, null)
    }

    private suspend fun deleteAllCreatedLocked(): Int {
        if (anki.availability() != AnkiAvailability.READY) return 0
        var deleted = 0
        for (card in db.cards().withNoteIds()) {
            if (!anki.delete(checkNotNull(card.ankiNoteId))) continue
            deleted++
            val status = if (card.status == CardStatus.SENT) next(card.status, CardEvent.UNDO_SEND) else card.status
            save(card.copy(status = status, ankiNoteId = null, sentAt = null, ankiMod = null))
        }
        return deleted
    }

    // AnkiDroid already holds the note; a write lost to cancellation would send it again.
    private suspend fun save(card: CardEntity) = withContext(NonCancellable) { db.cards().update(card) }
}

internal fun bookLine(book: BookEntity): String =
    if (book.author.isNullOrBlank()) book.title else "${book.title}, ${book.author}"

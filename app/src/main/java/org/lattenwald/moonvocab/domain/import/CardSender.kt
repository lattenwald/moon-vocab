package org.lattenwald.moonvocab.domain.import

import kotlin.coroutines.cancellation.CancellationException
import org.lattenwald.moonvocab.data.anki.AnkiAvailability
import org.lattenwald.moonvocab.data.anki.AnkiGateway
import org.lattenwald.moonvocab.data.anki.NoteTypeChanged
import org.lattenwald.moonvocab.data.anki.ankiFields
import org.lattenwald.moonvocab.data.db.AppDatabase
import org.lattenwald.moonvocab.data.db.BookEntity
import org.lattenwald.moonvocab.data.settings.Settings
import org.lattenwald.moonvocab.domain.card.CardStatus
import org.lattenwald.moonvocab.domain.card.CardStore
import org.lattenwald.moonvocab.domain.card.CardWrites
import org.lattenwald.moonvocab.domain.card.SENDABLE
import org.lattenwald.moonvocab.domain.card.bookTags
import org.lattenwald.moonvocab.domain.card.deckName
import org.lattenwald.moonvocab.domain.card.deletedFromAnki
import org.lattenwald.moonvocab.domain.card.noteDeleted
import org.lattenwald.moonvocab.domain.card.sent
import org.lattenwald.moonvocab.domain.card.unsent

data class SendResult(
    val sent: Int,
    val skipped: Int,
    val unavailable: AnkiAvailability?,
    val error: String?,
    val sentIds: List<Long> = emptyList(),
    val missingFields: List<String> = emptyList(),
)

data class UndoResult(val restored: Int, val kept: Int, val unavailable: AnkiAvailability?, val error: String?)

sealed interface DeleteResult {
    data object Deleted : DeleteResult
    data object NotSent : DeleteResult
    data object Refused : DeleteResult
    data class Unavailable(val availability: AnkiAvailability) : DeleteResult
    data class Failed(val message: String) : DeleteResult
}

class CardSender(
    private val db: AppDatabase,
    private val anki: AnkiGateway,
    private val settings: Settings,
    private val store: CardStore,
    private val clock: () -> Long,
) {
    private var noteType: Pair<Long, Int>? = null

    suspend fun send(cardIds: List<Long>): SendResult = store.locked { it.sendLocked(cardIds) }

    suspend fun deleteAllCreated(): Int = store.locked { it.deleteAllCreatedLocked() }

    suspend fun undoSend(cardIds: List<Long>): UndoResult = store.locked { it.undoSendLocked(cardIds) }

    suspend fun deleteFromAnki(cardId: Long): DeleteResult = store.locked { it.deleteFromAnkiLocked(cardId) }

    private suspend fun CardWrites.sendLocked(cardIds: List<Long>): SendResult {
        val availability = anki.availability()
        if (availability != AnkiAvailability.READY) return SendResult(0, cardIds.size, availability, null)
        val wanted = cardIds.toSet()
        val sendable = db.cards().byIds(cardIds).filter { it.status in SENDABLE }
        val skipped = wanted.size - sendable.size
        if (sendable.isEmpty()) return SendResult(0, skipped, null, null)

        val sentIds = ArrayList<Long>()
        try {
            val s = settings.current()
            val (mid, _) = noteType ?: anki.ensureNoteType(s.noteTypeId, s.noteTypeVersion).also { (mid, version) ->
                if (mid != s.noteTypeId || version != s.noteTypeVersion) {
                    settings.update { it.copy(noteTypeId = mid, noteTypeVersion = version) }
                }
                noteType = mid to version
            }
            // Process death between add and save leaves the note in AnkiDroid and the card unsent.
            val existing = anki.findByAppIds(mid, sendable.map { it.uuid })
            val books = HashMap<Long, BookEntity>()
            val decks = HashMap<String, Long>()
            for (card in sendable) {
                val noteId = existing[card.uuid] ?: run {
                    val book = books.getOrPut(card.bookId) { checkNotNull(db.books().byId(card.bookId)) { "no book ${card.bookId}" } }
                    val deck = deckName(s.deckPattern, card.sourceLanguage, s.deckOverride)
                    val deckId = decks.getOrPut(deck) { anki.ensureDeck(deck) }
                    anki.add(mid, deckId, card.ankiFields(bookLine(book)), bookTags(s.tagPattern, book.title))
                }
                save(card.sent(noteId, clock()))
                sentIds += card.id
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: NoteTypeChanged) {
            noteType = null
            return SendResult(sentIds.size, skipped, null, e.message, sentIds, e.missing)
        } catch (e: Exception) {
            noteType = null
            return SendResult(sentIds.size, skipped, null, e.message ?: e::class.simpleName, sentIds)
        }
        return SendResult(sentIds.size, skipped, null, null, sentIds)
    }

    private suspend fun CardWrites.undoSendLocked(cardIds: List<Long>): UndoResult {
        val availability = anki.availability()
        if (availability != AnkiAvailability.READY) return UndoResult(0, cardIds.size, availability, null)
        val wanted = cardIds.toSet()
        val sent = db.cards().byStatus(CardStatus.SENT).filter { it.id in wanted && it.ankiNoteId != null }
        var restored = 0
        for (card in sent) {
            val deleted = try {
                anki.delete(checkNotNull(card.ankiNoteId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return UndoResult(restored, sent.size - restored, null, e.message ?: e::class.simpleName)
            }
            if (!deleted) continue
            save(card.unsent())
            restored++
        }
        return UndoResult(restored, sent.size - restored, null, null)
    }

    private suspend fun CardWrites.deleteFromAnkiLocked(cardId: Long): DeleteResult {
        val availability = anki.availability()
        if (availability != AnkiAvailability.READY) return DeleteResult.Unavailable(availability)
        val card = db.cards().byIds(listOf(cardId)).singleOrNull()?.takeIf { it.status == CardStatus.SENT }
            ?: return DeleteResult.NotSent
        val noteId = card.ankiNoteId ?: return DeleteResult.NotSent
        val deleted = try {
            anki.delete(noteId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return DeleteResult.Failed(e.message ?: e.javaClass.name)
        }
        if (!deleted) return DeleteResult.Refused
        save(card.deletedFromAnki())
        return DeleteResult.Deleted
    }

    private suspend fun CardWrites.deleteAllCreatedLocked(): Int {
        if (anki.availability() != AnkiAvailability.READY) return 0
        var deleted = 0
        for (card in db.cards().withNoteIds()) {
            if (!anki.delete(checkNotNull(card.ankiNoteId))) continue
            deleted++
            save(card.noteDeleted())
        }
        return deleted
    }
}

internal fun bookLine(book: BookEntity): String =
    if (book.author.isNullOrBlank()) book.title else "${book.title}, ${book.author}"

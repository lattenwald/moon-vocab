package org.lattenwald.moonvocab.domain.cardpage

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.lattenwald.moonvocab.data.anki.AnkiAvailability
import org.lattenwald.moonvocab.data.anki.AnkiGateway
import org.lattenwald.moonvocab.data.anki.NoteTypeChanged
import org.lattenwald.moonvocab.data.anki.storedFields
import org.lattenwald.moonvocab.data.db.AppDatabase
import org.lattenwald.moonvocab.data.db.CardEntity
import org.lattenwald.moonvocab.domain.card.CardEvent
import org.lattenwald.moonvocab.domain.card.CardExtras
import org.lattenwald.moonvocab.domain.card.CardField
import org.lattenwald.moonvocab.domain.card.CardFields
import org.lattenwald.moonvocab.domain.card.CardStatus
import org.lattenwald.moonvocab.domain.card.ReconcileAction
import org.lattenwald.moonvocab.domain.card.SavePlan
import org.lattenwald.moonvocab.domain.card.SentCard
import org.lattenwald.moonvocab.domain.card.next
import org.lattenwald.moonvocab.domain.card.planSave
import org.lattenwald.moonvocab.domain.card.reconcile
import org.lattenwald.moonvocab.domain.card.refreshed
import org.lattenwald.moonvocab.domain.card.withFields

sealed interface OpenResult {
    data class Fresh(val card: CardEntity) : OpenResult
    data class Local(val card: CardEntity, val unavailable: AnkiAvailability?, val error: String?) : OpenResult
    data class Deleted(val card: CardEntity) : OpenResult
    data object Gone : OpenResult
}

sealed interface SaveResult {
    data class Saved(val card: CardEntity) : SaveResult
    data class Conflict(val card: CardEntity, val fields: Set<CardField>) : SaveResult
    data class Deleted(val card: CardEntity) : SaveResult
    data class Unavailable(val availability: AnkiAvailability) : SaveResult
    data class Failed(val message: String, val missingFields: List<String> = emptyList()) : SaveResult
}

class CardEditor(
    private val db: AppDatabase,
    private val anki: AnkiGateway,
    // Shared with CardSender and AnkiCheck, which write the same cards.
    private val lock: Mutex,
) {
    suspend fun open(cardId: Long): OpenResult {
        val card = card(cardId) ?: return OpenResult.Gone
        if (card.status != CardStatus.SENT) return OpenResult.Local(card, null, null)
        return lock.withLock { openLocked(cardId) }
    }

    suspend fun save(cardId: Long, baseline: CardFields, edited: CardFields, extras: CardExtras?): SaveResult =
        lock.withLock { saveLocked(cardId, baseline, edited, extras) }

    suspend fun saveLocal(cardId: Long, edited: CardFields, extras: CardExtras?): CardEntity? = lock.withLock {
        val card = card(cardId)?.takeIf { it.status == CardStatus.PENDING } ?: return@withLock null
        card.withFields(edited, extras).also { db.cards().update(it) }
    }

    private suspend fun openLocked(cardId: Long): OpenResult {
        // Re-read: the card may have changed while waiting for the lock.
        val card = card(cardId) ?: return OpenResult.Gone
        val noteId = card.ankiNoteId
        if (card.status != CardStatus.SENT || noteId == null) return OpenResult.Local(card, null, null)
        val availability = anki.availability()
        if (availability != AnkiAvailability.READY) return OpenResult.Local(card, availability, null)
        val snapshots = try {
            anki.read(listOf(noteId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return OpenResult.Local(card, null, e.message ?: e::class.java.simpleName)
        }
        return when (val action = reconcile(listOf(SentCard(card.id, noteId, card.storedFields())), snapshots).singleOrNull()) {
            is ReconcileAction.Missing -> {
                val deleted = card.copy(status = next(card.status, CardEvent.NOTE_MISSING))
                db.cards().update(deleted)
                OpenResult.Deleted(deleted)
            }
            is ReconcileAction.Refresh -> {
                val fresh = card.refreshed(action.fields, action.mod)
                db.cards().update(fresh)
                OpenResult.Fresh(fresh)
            }
            null -> OpenResult.Fresh(card)
        }
    }

    private suspend fun saveLocked(cardId: Long, baseline: CardFields, edited: CardFields, extras: CardExtras?): SaveResult {
        val card = card(cardId) ?: return SaveResult.Failed("card not found")
        if (card.status == CardStatus.DELETED_IN_ANKI) {
            val kept = card.withFields(edited, extras)
            db.cards().update(kept)
            return SaveResult.Deleted(kept)
        }
        val noteId = card.ankiNoteId
        if (card.status != CardStatus.SENT || noteId == null) return SaveResult.Failed("card is not in AnkiDroid")
        val availability = anki.availability()
        if (availability != AnkiAvailability.READY) return SaveResult.Unavailable(availability)
        return try {
            val note = anki.read(listOf(noteId))[noteId]
            if (note == null) {
                val kept = card.withFields(edited, extras).copy(status = next(card.status, CardEvent.NOTE_MISSING))
                db.cards().update(kept)
                return SaveResult.Deleted(kept)
            }
            when (val plan = planSave(baseline, edited, note.fields)) {
                is SavePlan.Conflict -> {
                    val fresh = card.refreshed(note.fields, note.mod)
                    db.cards().update(fresh)
                    SaveResult.Conflict(fresh, plan.fields)
                }
                is SavePlan.Write -> {
                    // update ignores mid; empty tags keep the note's own.
                    if (plan.anki.isNotEmpty()) anki.update(noteId, 0L, plan.anki, emptySet())
                    val saved = card.withFields(plan.result, extras).copy(ankiMod = null)
                    withContext(NonCancellable) { db.cards().update(saved) }
                    SaveResult.Saved(saved)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: NoteTypeChanged) {
            SaveResult.Failed(e.message ?: e::class.java.simpleName, e.missing)
        } catch (e: Exception) {
            SaveResult.Failed(e.message ?: e::class.java.simpleName)
        }
    }

    private suspend fun card(id: Long): CardEntity? = db.cards().byIds(listOf(id)).singleOrNull()
}

package org.lattenwald.moonvocab.domain.card

import kotlin.coroutines.cancellation.CancellationException
import org.lattenwald.moonvocab.data.anki.AnkiAvailability
import org.lattenwald.moonvocab.data.anki.AnkiGateway
import org.lattenwald.moonvocab.data.anki.NoteSnapshot
import org.lattenwald.moonvocab.data.anki.NoteTypeChanged
import org.lattenwald.moonvocab.data.anki.readFrom
import org.lattenwald.moonvocab.data.db.AppDatabase
import org.lattenwald.moonvocab.data.db.CardEntity

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

sealed interface CheckResult {
    data class Changed(val count: Int) : CheckResult
    data class Unavailable(val availability: AnkiAvailability) : CheckResult
}

class NoteSync(
    private val db: AppDatabase,
    private val anki: AnkiGateway,
    private val store: CardStore,
) {
    suspend fun open(cardId: Long): OpenResult {
        val card = card(cardId) ?: return OpenResult.Gone
        if (card.status != CardStatus.SENT) return OpenResult.Local(card, null, null)
        return store.locked { it.openLocked(cardId) }
    }

    suspend fun checkAll(): CheckResult = store.locked { writes ->
        val availability = anki.availability()
        if (availability != AnkiAvailability.READY) return@locked CheckResult.Unavailable(availability)
        val sent = db.cards().byStatus(CardStatus.SENT).filter { it.ankiNoteId != null }
        if (sent.isEmpty()) return@locked CheckResult.Changed(0)
        val notes = anki.read(sent.map { checkNotNull(it.ankiNoteId) })
        CheckResult.Changed(sent.count { writes.sync(it, notes[it.ankiNoteId]) != it })
    }

    suspend fun save(cardId: Long, baseline: CardFields, edited: CardFields, extras: CardExtras?): SaveResult =
        store.locked { it.saveLocked(cardId, baseline, edited, extras) }

    private suspend fun CardWrites.sync(card: CardEntity, note: NoteSnapshot?): CardEntity {
        val synced = when {
            note == null -> card.noteMissing()
            else -> card.readFrom(note.fields).let { if (it == card) card else it.copy(ankiMod = note.mod) }
        }
        if (synced != card) save(synced)
        return synced
    }

    private suspend fun CardWrites.openLocked(cardId: Long): OpenResult {
        // Re-read: the card may have changed while waiting for the lock.
        val card = card(cardId) ?: return OpenResult.Gone
        val noteId = card.ankiNoteId
        if (card.status != CardStatus.SENT || noteId == null) return OpenResult.Local(card, null, null)
        val availability = anki.availability()
        if (availability != AnkiAvailability.READY) return OpenResult.Local(card, availability, null)
        val notes = try {
            anki.read(listOf(noteId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return OpenResult.Local(card, null, e.message ?: e::class.java.simpleName)
        }
        val synced = sync(card, notes[noteId])
        return if (synced.status == CardStatus.DELETED_IN_ANKI) OpenResult.Deleted(synced) else OpenResult.Fresh(synced)
    }

    private suspend fun CardWrites.saveLocked(cardId: Long, baseline: CardFields, edited: CardFields, extras: CardExtras?): SaveResult {
        val card = card(cardId) ?: return SaveResult.Failed("card not found")
        if (card.status == CardStatus.DELETED_IN_ANKI) {
            val kept = card.withFields(edited, extras)
            save(kept)
            return SaveResult.Deleted(kept)
        }
        val noteId = card.ankiNoteId
        if (card.status != CardStatus.SENT || noteId == null) return SaveResult.Failed("card is not in AnkiDroid")
        val availability = anki.availability()
        if (availability != AnkiAvailability.READY) return SaveResult.Unavailable(availability)
        return try {
            val note = anki.read(listOf(noteId))[noteId]
            if (note == null) {
                val kept = card.withFields(edited, extras).noteMissing()
                save(kept)
                return SaveResult.Deleted(kept)
            }
            when (val plan = planSave(baseline, edited, note.fields)) {
                is SavePlan.Conflict -> {
                    val fresh = card.readFrom(note.fields).copy(ankiMod = note.mod)
                    save(fresh)
                    SaveResult.Conflict(fresh, plan.fields)
                }
                is SavePlan.Write -> {
                    // update ignores mid; empty tags keep the note's own.
                    if (plan.anki.isNotEmpty()) anki.update(noteId, 0L, plan.anki, emptySet())
                    val saved = card.readFrom(note.fields).withFields(plan.result, extras).copy(ankiMod = null)
                    save(saved)
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

package org.lattenwald.moonvocab.domain.inbox

import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.lattenwald.moonvocab.data.db.AppDatabase
import org.lattenwald.moonvocab.domain.card.CardEvent
import org.lattenwald.moonvocab.domain.card.CardStatus
import org.lattenwald.moonvocab.domain.card.next

class InboxActions(
    private val db: AppDatabase,
    // CardSender's lock: otherwise a send saves its snapshot of PENDING cards over these changes.
    private val lock: Mutex,
) {
    suspend fun discard(ids: List<Long>): List<Long> = locked {
        db.cards().byIds(ids).filter { it.status in UNSENT }.map { card ->
            db.cards().update(card.copy(status = next(card.status, CardEvent.DISCARD), previousStatus = card.status))
            card.id
        }
    }

    suspend fun restore(ids: List<Long>): Int = locked {
        val discarded = db.cards().byIds(ids).filter { it.status == CardStatus.DISCARDED && it.previousStatus != null }
        for (card in discarded) {
            db.cards().update(card.copy(status = next(card.status, CardEvent.RESTORE, card.previousStatus), previousStatus = null))
        }
        discarded.size
    }

    suspend fun forgetForRetranslation(ids: List<Long>): Int = locked {
        val forgotten = db.cards().byIds(ids).filter { it.status in UNSENT && it.ankiNoteId == null }.map { it.id }
        if (forgotten.isNotEmpty()) db.cards().delete(forgotten) // mark.cardId is ON DELETE SET NULL
        forgotten.size
    }

    private suspend fun <T> locked(block: suspend () -> T): T = lock.withLock { db.withTransaction(block) }

    private companion object {
        val UNSENT = setOf(CardStatus.PENDING, CardStatus.FAILED)
    }
}

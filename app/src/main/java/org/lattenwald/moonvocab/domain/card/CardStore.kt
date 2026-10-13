package org.lattenwald.moonvocab.domain.card

import androidx.annotation.VisibleForTesting
import androidx.room.withTransaction
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.lattenwald.moonvocab.data.db.AppDatabase
import org.lattenwald.moonvocab.data.db.CardEntity
import org.lattenwald.moonvocab.data.db.CardWriteAccess
import org.lattenwald.moonvocab.data.db.CardWriteDao

@OptIn(CardWriteAccess::class)
class CardStore(private val db: AppDatabase) {
    private val lock = Mutex()
    private val dao = db.cardWrites()
    private val writes = CardWrites(dao)

    @get:VisibleForTesting
    val isLocked: Boolean get() = lock.isLocked

    suspend fun <T> locked(block: suspend (CardWrites) -> T): T = lock.withLock { block(writes) }

    suspend fun add(batch: List<Pair<CardEntity, List<Long>>>, links: List<Pair<Long, Long>> = emptyList()): List<Long> =
        transaction {
            val live = db.cards().byIds(links.map { it.second }.distinct()).map { it.id }.toSet()
            for ((markId, cardId) in links) if (cardId in live) db.marks().link(listOf(markId), cardId)
            batch.map { (card, markIds) ->
                val id = dao.insert(card)
                db.marks().link(markIds, id)
                id
            }
        }

    suspend fun discard(ids: List<Long>): List<Long> = transaction {
        db.cards().byIds(ids).filter { it.status in UNSENT }.map { card ->
            dao.update(card.discarded())
            card.id
        }
    }

    suspend fun restore(ids: List<Long>): Int = transaction {
        val discarded = db.cards().byIds(ids).filter { it.status == CardStatus.DISCARDED && it.previousStatus != null }
        for (card in discarded) dao.update(card.restored())
        discarded.size
    }

    suspend fun saveLocal(cardId: Long, edited: CardFields, extras: CardExtras?): CardEntity? = transaction {
        val card = db.cards().byIds(listOf(cardId)).singleOrNull()?.takeIf { it.status == CardStatus.PENDING }
        card?.withFields(edited, extras)?.also { dao.update(it) }
    }

    suspend fun forgetForRetranslation(ids: List<Long>): Int = transaction { forget(db.cards().byIds(ids)) }

    suspend fun forgetFailed(): Int = transaction { forget(db.cards().byStatus(CardStatus.FAILED)) }

    suspend fun forgetUnsent(bookId: Long? = null): Int = transaction { dao.deleteUnsent(bookId) }

    private suspend fun forget(cards: List<CardEntity>): Int {
        val forgotten = cards.filter { it.status in UNSENT && it.ankiNoteId == null }.map { it.id }
        if (forgotten.isNotEmpty()) dao.delete(forgotten) // mark.cardId is ON DELETE SET NULL
        return forgotten.size
    }

    private suspend fun <T> transaction(block: suspend () -> T): T = lock.withLock { db.withTransaction(block) }

    private companion object {
        val UNSENT = setOf(CardStatus.PENDING, CardStatus.FAILED)
    }
}

class CardWrites internal constructor(private val dao: CardWriteDao) {
    // A save recording an AnkiDroid write must survive cancellation, or the write is made again.
    suspend fun save(card: CardEntity) = withContext(NonCancellable) { dao.update(card) }
}

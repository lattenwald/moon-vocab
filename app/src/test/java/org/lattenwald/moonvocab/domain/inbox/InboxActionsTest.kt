package org.lattenwald.moonvocab.domain.inbox

import androidx.room.withTransaction
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lattenwald.moonvocab.data.backup.MrproFixture
import org.lattenwald.moonvocab.data.backup.NoteRow
import org.lattenwald.moonvocab.domain.card.CardStatus.DISCARDED
import org.lattenwald.moonvocab.domain.card.CardStatus.FAILED
import org.lattenwald.moonvocab.domain.card.CardStatus.PENDING
import org.lattenwald.moonvocab.domain.card.CardStatus.SENT
import org.lattenwald.moonvocab.domain.card.SenseJudge
import org.lattenwald.moonvocab.domain.card.Translator
import org.lattenwald.moonvocab.domain.import.ImportTestBase

class InboxActionsTest : ImportTestBase() {
    private val actions by lazy { InboxActions(db, ankiLock) }
    private val firstCandidate = SenseJudge { questions -> questions.associate { it.id to it.candidates.first().ref } }
    private val failsWord1 = Translator { items, _, _ -> items.filter { it.marked != word(1) }.associate { it.id to tr(it) } }

    private fun again(i: Int): NoteRow = rows(1, from = i).single().copy(time = T0 + 1_000 + i)

    private suspend fun TestScope.importPendingAAndFailedB() {
        pipeline(translate = failsWord1, judge = firstCandidate).run(req(MrproFixture.build(tmp, rows(2) + again(0))), {})
    }

    private suspend fun allIds(): List<Long> = db.cards().observeAll().first().map { it.id }

    @Test fun inboxHoldsPendingAndFailedWithTheirNewestMark() = runTest {
        importPendingAAndFailedB()
        val rows = db.cards().observeInbox().first()
        assertEquals(setOf(PENDING, FAILED), rows.map { it.card.status }.toSet())
        assertEquals(T0 + 1_000, rows.single { it.card.status == PENDING }.markedAt)
        assertEquals(TITLE, rows.first().bookTitle)
    }

    @Test fun discardThenRestoreGivesBackEachStatus() = runTest {
        importPendingAAndFailedB()
        val allIds = allIds()
        assertEquals(2, actions.discard(allIds).size)
        assertTrue(db.cards().byIds(allIds).all { it.status == DISCARDED })
        assertEquals(2, actions.restore(allIds))
        assertEquals(setOf(PENDING, FAILED), db.cards().byIds(allIds).map { it.status }.toSet())
        assertTrue(db.cards().byIds(allIds).all { it.previousStatus == null })
    }

    @Test fun discardLeavesSentCardsAlone() = runTest {
        pipeline().run(req(backup(2)), {})
        val allIds = allIds()
        val sentId = sender.send(allIds.take(1)).sentIds.single()
        val pendingId = allIds.single { it != sentId }
        assertEquals(listOf(pendingId), actions.discard(allIds))
        assertEquals(SENT, db.cards().byIds(listOf(sentId)).single().status)
    }

    @Test fun discardWaitsForASendHoldingTheAnkiLock() = runTest {
        pipeline().run(req(backup(1)), {})
        val ids = allIds()
        ankiLock.lock()
        val discarded = async { actions.discard(ids) }
        yield()
        db.withTransaction { // a send's save, made under the lock
            db.cards().update(db.cards().byIds(ids).single().copy(status = SENT, ankiNoteId = 1))
        }
        ankiLock.unlock()
        assertEquals(emptyList<Long>(), discarded.await())
        assertEquals(SENT, db.cards().byIds(ids).single().status)
    }

    @Test fun restoreTwiceRestoresOnce() = runTest {
        pipeline().run(req(backup(1)), {})
        val allIds = allIds()
        actions.discard(allIds)
        assertEquals(1, actions.restore(allIds))
        assertEquals(0, actions.restore(allIds))
    }

    @Test fun translateAgainFreesEveryMarkOfTheCard() = runTest {
        importPendingAAndFailedB()
        val cardA = db.cards().byStatus(PENDING).single()
        assertEquals(1, actions.forgetForRetranslation(listOf(cardA.id)))
        assertEquals(2, db.marks().withoutCard(null).size)
        pipeline(judge = firstCandidate).run(req(backup = null), {})
        assertTrue(db.marks().all().all { it.cardId != null })
    }

    @Test fun translateAgainKeepsSentCards() = runTest {
        importPendingAAndFailedB()
        val cardA = db.cards().byStatus(PENDING).single()
        sender.send(listOf(cardA.id))
        assertEquals(0, actions.forgetForRetranslation(listOf(cardA.id)))
        assertEquals(SENT, db.cards().byIds(listOf(cardA.id)).single().status)
        assertEquals(2, db.marks().all().count { it.cardId == cardA.id })
    }
}

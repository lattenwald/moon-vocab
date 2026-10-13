package org.lattenwald.moonvocab.domain.card

import kotlinx.coroutines.CompletableDeferred
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
import org.lattenwald.moonvocab.data.db.CardEntity
import org.lattenwald.moonvocab.domain.card.CardStatus.DISCARDED
import org.lattenwald.moonvocab.domain.card.CardStatus.FAILED
import org.lattenwald.moonvocab.domain.card.CardStatus.PENDING
import org.lattenwald.moonvocab.domain.card.CardStatus.SENT
import org.lattenwald.moonvocab.domain.import.ImportTestBase

class CardStoreTest : ImportTestBase() {
    private val firstCandidate = SenseJudge { questions -> questions.associate { it.id to it.candidates.first().ref } }
    private val failsWord1 = Translator { items, _, _, _ -> items.filter { it.marked != word(1) }.associate { it.id to tr(it) } }

    private fun again(i: Int): NoteRow = rows(1, from = i).single().copy(time = T0 + 1_000 + i)

    private suspend fun TestScope.importPendingAAndFailedB() {
        pipeline(translate = failsWord1, judge = firstCandidate).run(req(MrproFixture.build(tmp, rows(2) + again(0))), {})
    }

    private suspend fun allIds(): List<Long> = db.cards().observeAll().first().map { it.id }

    private suspend fun only(ids: List<Long>): CardEntity = db.cards().byIds(ids).single()

    private suspend fun <T> TestScope.waitingOnALockedWrite(write: suspend (CardWrites) -> Unit, waiting: suspend () -> T): T {
        val release = CompletableDeferred<Unit>()
        val writer = async { store.locked { release.await(); write(it) } }
        yield()
        val waiter = async { waiting() }
        yield()
        release.complete(Unit)
        writer.await()
        return waiter.await()
    }

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
        assertEquals(2, store.discard(allIds).size)
        assertTrue(db.cards().byIds(allIds).all { it.status == DISCARDED })
        assertEquals(2, store.restore(allIds))
        assertEquals(setOf(PENDING, FAILED), db.cards().byIds(allIds).map { it.status }.toSet())
        assertTrue(db.cards().byIds(allIds).all { it.previousStatus == null })
    }

    @Test fun discardLeavesSentCardsAlone() = runTest {
        pipeline().run(req(backup(2)), {})
        val allIds = allIds()
        val sentId = sender.send(allIds.take(1)).sentIds.single()
        val pendingId = allIds.single { it != sentId }
        assertEquals(listOf(pendingId), store.discard(allIds))
        assertEquals(SENT, only(listOf(sentId)).status)
    }

    @Test fun discardWaitsForASend() = runTest {
        pipeline().run(req(backup(1)), {})
        val ids = allIds()
        val discarded = waitingOnALockedWrite({ it.save(only(ids).sent(noteId = 1, at = NOW)) }) { store.discard(ids) }
        assertEquals(emptyList<Long>(), discarded)
        assertEquals(SENT, only(ids).status)
    }

    @Test fun forgetUnsentWaitsForASendSoItsNoteStaysRecorded() = runTest {
        pipeline().run(req(backup(1)), {})
        val ids = allIds()
        assertEquals(0, waitingOnALockedWrite({ it.save(only(ids).sent(noteId = 1, at = NOW)) }) { store.forgetUnsent() })
        assertEquals(SENT, only(ids).status)
    }

    @Test fun retryKeepsACardDiscardedWhileItWaited() = runTest {
        importPendingAAndFailedB()
        val failed = db.cards().byStatus(FAILED).single()
        waitingOnALockedWrite({ it.save(failed.discarded()) }) { pipeline().retryFailed {} }
        assertEquals(DISCARDED, only(listOf(failed.id)).status)
    }

    @Test fun restoreTwiceRestoresOnce() = runTest {
        pipeline().run(req(backup(1)), {})
        val allIds = allIds()
        store.discard(allIds)
        assertEquals(1, store.restore(allIds))
        assertEquals(0, store.restore(allIds))
    }

    @Test fun translateAgainFreesEveryMarkOfTheCard() = runTest {
        importPendingAAndFailedB()
        val cardA = db.cards().byStatus(PENDING).single()
        assertEquals(1, store.forgetForRetranslation(listOf(cardA.id)))
        assertEquals(2, db.marks().withoutCard(null).size)
        pipeline(judge = firstCandidate).run(req(backup = null), {})
        assertTrue(db.marks().all().all { it.cardId != null })
    }

    @Test fun translateAgainKeepsSentCards() = runTest {
        importPendingAAndFailedB()
        val cardA = db.cards().byStatus(PENDING).single()
        sender.send(listOf(cardA.id))
        assertEquals(0, store.forgetForRetranslation(listOf(cardA.id)))
        assertEquals(SENT, only(listOf(cardA.id)).status)
        assertEquals(2, db.marks().all().count { it.cardId == cardA.id })
    }
}

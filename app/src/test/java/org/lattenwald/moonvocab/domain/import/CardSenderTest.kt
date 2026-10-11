package org.lattenwald.moonvocab.domain.import

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lattenwald.moonvocab.domain.card.CardStatus.PENDING
import org.lattenwald.moonvocab.domain.card.CardStatus.SENT

class CardSenderTest : ImportTestBase() {
    @Test fun deleteAllCreatedRemovesNotesAndResetsCards() = runTest {
        pipeline().run(req(backup(2), send = true), {})
        assertEquals(2, sender.deleteAllCreated())
        assertTrue(fakeAnki.notes.isEmpty())
        val cards = db.cards().byStatus(PENDING)
        assertEquals(2, cards.size)
        assertTrue(cards.all { it.ankiNoteId == null && it.sentAt == null })
    }

    @Test fun cardWhoseNoteWasNotDeletedKeepsItsNote() = runTest {
        pipeline().run(req(backup(2), send = true), {})
        val (kept, deleted) = db.cards().byStatus(SENT)
        fakeAnki.notes.remove(kept.ankiNoteId)
        assertEquals(1, sender.deleteAllCreated())
        assertEquals(listOf(kept), db.cards().byStatus(SENT))
        assertEquals(listOf(deleted.id), db.cards().byStatus(PENDING).map { it.id })
    }

    @Test fun gatewayFailurePartwayStopsAndLeavesRestPending() = runTest {
        pipeline().run(req(backup(3)), {})
        val ids = db.cards().byStatus(PENDING).map { it.id }
        fakeAnki.failOnNthAdd = 2
        val r = sender.send(ids)
        assertEquals(1, r.sent)
        assertEquals("AnkiDroid refused to add the note", r.error)
        assertEquals(1, db.cards().byStatus(SENT).size)
        assertEquals(2, db.cards().byStatus(PENDING).size)
    }

    @Test fun overlappingSendsAddEachNoteOnce() = runTest {
        pipeline().run(req(backup(3)), {})
        val ids = db.cards().byStatus(PENDING).map { it.id }
        val results = List(2) { async { sender.send(ids) } }.awaitAll()
        assertEquals(3, fakeAnki.notes.size)
        assertEquals(3, results.sumOf { it.sent })
    }

    @Test fun sentCardsAreNotSentAgain() = runTest {
        pipeline().run(req(backup(2)), {})
        val ids = db.cards().byStatus(PENDING).map { it.id }
        assertEquals(SendResult(2, 0, null, null), sender.send(ids))
        assertEquals(SendResult(0, 2, null, null), sender.send(ids))
        assertEquals(2, fakeAnki.notes.size)
        assertEquals(FakeAnki.MID, settings.current().noteTypeId)
        assertTrue(db.cards().byStatus(SENT).all { it.sentAt == NOW && it.ankiNoteId in fakeAnki.notes })
    }
}

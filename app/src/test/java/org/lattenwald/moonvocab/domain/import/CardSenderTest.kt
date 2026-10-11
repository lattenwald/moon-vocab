package org.lattenwald.moonvocab.domain.import

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lattenwald.moonvocab.data.anki.AnkiAvailability
import org.lattenwald.moonvocab.data.anki.NoteTypeChanged
import org.lattenwald.moonvocab.domain.card.CardStatus.PENDING
import org.lattenwald.moonvocab.domain.card.CardStatus.SENT

class CardSenderTest : ImportTestBase() {
    @Test fun undoSendDeletesNotesAndReturnsCardsToPending() = runTest {
        pipeline().run(req(backup(2)), {})
        val sent = sender.send(db.cards().byStatus(PENDING).map { it.id })
        assertEquals(2, sent.sentIds.size)
        assertEquals(UndoResult(restored = 2, kept = 0, unavailable = null, error = null), sender.undoSend(sent.sentIds))
        assertTrue(fakeAnki.notes.isEmpty())
        assertTrue(db.cards().byStatus(PENDING).all { it.ankiNoteId == null && it.sentAt == null && it.ankiMod == null })
    }

    @Test fun undoSendKeepsCardsAnkiDroidDidNotDelete() = runTest {
        pipeline().run(req(backup(2)), {})
        val sent = sender.send(db.cards().byStatus(PENDING).map { it.id })
        val keep = db.cards().byStatus(SENT).first()
        fakeAnki.refuseDelete += keep.ankiNoteId!!
        assertEquals(UndoResult(restored = 1, kept = 1, unavailable = null, error = null), sender.undoSend(sent.sentIds))
        assertEquals(listOf(keep), db.cards().byStatus(SENT))
    }

    @Test fun undoSendChangesNothingWhileAnkiDroidIsUnavailable() = runTest {
        pipeline().run(req(backup(2)), {})
        val sent = sender.send(db.cards().byStatus(PENDING).map { it.id })
        fakeAnki.availability = AnkiAvailability.NO_PERMISSION
        assertEquals(UndoResult(0, 2, AnkiAvailability.NO_PERMISSION, null), sender.undoSend(sent.sentIds))
        assertEquals(2, db.cards().byStatus(SENT).size)
    }

    @Test fun missingNoteTypeFieldsAreReported() = runTest {
        pipeline().run(req(backup(1)), {})
        fakeAnki.addError = NoteTypeChanged(listOf("Excerpt"))
        val r = sender.send(db.cards().byStatus(PENDING).map { it.id })
        assertEquals(0, r.sent)
        assertEquals(listOf("Excerpt"), r.missingFields)
    }

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

    @Test fun noteAlreadyInAnkiIsAdoptedNotAddedAgain() = runTest {
        pipeline().run(req(backup(1)), {})
        val card = db.cards().byStatus(PENDING).single()
        fakeAnki.notes[7L] = FakeAnki.Note(FakeAnki.MID, "Moon+::Test", mapOf("AppId" to card.uuid), emptySet(), mod = 1)
        assertEquals(SendResult(1, 0, null, null, listOf(card.id)), sender.send(listOf(card.id)))
        assertEquals(0, fakeAnki.adds)
        assertEquals(7L, db.cards().byStatus(SENT).single().ankiNoteId)
    }

    @Test fun noteTypeIsEnsuredOncePerSender() = runTest {
        pipeline().run(req(backup(2)), {})
        val (a, b) = db.cards().byStatus(PENDING).map { it.id }
        sender.send(listOf(a))
        sender.send(listOf(b))
        assertEquals(1, fakeAnki.ensureCalls)
    }

    @Test fun sendErrorForgetsTheNoteType() = runTest {
        pipeline().run(req(backup(1)), {})
        val ids = db.cards().byStatus(PENDING).map { it.id }
        fakeAnki.failOnNthAdd = 1
        sender.send(ids)
        assertEquals(SendResult(1, 0, null, null, ids), sender.send(ids))
        assertEquals(2, fakeAnki.ensureCalls)
    }

    @Test fun sentCardsAreNotSentAgain() = runTest {
        pipeline().run(req(backup(2)), {})
        val ids = db.cards().byStatus(PENDING).map { it.id }
        assertEquals(SendResult(2, 0, null, null, ids), sender.send(ids))
        assertEquals(SendResult(0, 2, null, null), sender.send(ids))
        assertEquals(2, fakeAnki.notes.size)
        assertEquals(FakeAnki.MID, settings.current().noteTypeId)
        assertTrue(db.cards().byStatus(SENT).all { it.sentAt == NOW && it.ankiNoteId in fakeAnki.notes })
    }
}

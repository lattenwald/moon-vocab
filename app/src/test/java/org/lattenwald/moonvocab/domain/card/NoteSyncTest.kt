package org.lattenwald.moonvocab.domain.card

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lattenwald.moonvocab.data.anki.AnkiAvailability
import org.lattenwald.moonvocab.data.anki.NoteTypeChanged
import org.lattenwald.moonvocab.data.db.CardEntity
import org.lattenwald.moonvocab.domain.card.CardStatus.DELETED_IN_ANKI
import org.lattenwald.moonvocab.domain.card.CardStatus.PENDING
import org.lattenwald.moonvocab.domain.card.CardStatus.SENT
import org.lattenwald.moonvocab.domain.import.ImportTestBase

class NoteSyncTest : ImportTestBase() {
    private suspend fun TestScope.sentCard(): CardEntity {
        pipeline().run(req(backup(1)), {})
        sender.send(listOf(db.cards().byStatus(PENDING).single().id))
        return db.cards().byStatus(SENT).single()
    }

    private fun editNote(noteId: Long, field: String, value: String) {
        val note = fakeAnki.notes.getValue(noteId)
        fakeAnki.notes[noteId] = note.copy(fields = note.fields + (field to value), mod = note.mod + 1)
    }

    private fun noteField(card: CardEntity, field: String): String? = fakeAnki.notes.getValue(card.ankiNoteId!!).fields[field]

    @Test fun openReadsAChangeMadeInAnkiDroid() = runTest {
        val card = sentCard()
        editNote(card.ankiNoteId!!, "Translation", "neu &amp; mehr")

        val opened = sync.open(card.id) as OpenResult.Fresh
        assertEquals("neu & mehr", opened.card.translation)
        assertEquals(opened.card, stored(card.id))
    }

    @Test fun openMarksAMissingNoteDeletedInAnki() = runTest {
        val card = sentCard()
        fakeAnki.notes.clear()

        val opened = sync.open(card.id) as OpenResult.Deleted
        assertEquals(DELETED_IN_ANKI, opened.card.status)
        assertEquals(DELETED_IN_ANKI, stored(card.id).status)
    }

    @Test fun openShowsTheLocalCopyWhileAnkiDroidIsUnavailable() = runTest {
        val card = sentCard()
        editNote(card.ankiNoteId!!, "Translation", "neu")
        fakeAnki.availability = AnkiAvailability.NO_PERMISSION

        assertEquals(OpenResult.Local(card, AnkiAvailability.NO_PERMISSION, null), sync.open(card.id))
        assertEquals(card, stored(card.id))
    }

    @Test fun checkRefreshesEditedNotesAndMarksMissingOnesDeleted() = runTest {
        pipeline().run(req(backup(3), send = true), {})
        val (edited, gone, same) = db.cards().byStatus(SENT)
        editNote(edited.ankiNoteId!!, "Translation", "neu &amp; <b>x</b>")
        fakeAnki.notes.remove(gone.ankiNoteId)

        assertEquals(CheckResult.Changed(2), sync.checkAll())
        assertEquals("neu & x", stored(edited.id).translation)
        assertEquals(fakeAnki.notes.getValue(edited.ankiNoteId).mod, stored(edited.id).ankiMod)
        assertEquals(DELETED_IN_ANKI, stored(gone.id).status)
        assertEquals(gone.ankiNoteId, stored(gone.id).ankiNoteId)
        assertEquals(same, stored(same.id))
    }

    @Test fun saveWritesTheChangedFieldAndAdoptsAnkiDroidsEditsToTheOthers() = runTest {
        val card = sentCard()
        val fields = card.cardFields()
        editNote(card.ankiNoteId!!, "Meaning", "<b>to win</b>")
        editNote(card.ankiNoteId, "Source", "Example by the model")

        val saved = sync.save(card.id, fields, fields.copy(translation = "одолеть"), null) as SaveResult.Saved
        assertEquals("одолеть", noteField(card, "Translation"))
        assertEquals("<b>to win</b>", noteField(card, "Meaning"))
        val room = stored(card.id)
        assertEquals(saved.card, room)
        assertEquals("одолеть", room.translation)
        assertEquals("to win", room.meaning)
        assertEquals("Example by the model", room.sourceNote)
    }

    @Test fun saveStopsWhenAnEditedFieldChangedInAnkiDroid() = runTest {
        val card = sentCard()
        val fields = card.cardFields()
        editNote(card.ankiNoteId!!, "Translation", "X")

        val result = sync.save(card.id, fields, fields.copy(translation = "Y"), null)
        assertEquals(SaveResult.Conflict(stored(card.id), setOf(CardField.TRANSLATION)), result)
        assertEquals("X", (result as SaveResult.Conflict).card.translation)
        assertEquals("X", noteField(card, "Translation"))
    }

    @Test fun saveOnAMissingNoteKeepsTheEditsLocally() = runTest {
        val card = sentCard()
        val fields = card.cardFields()
        fakeAnki.notes.clear()

        val result = sync.save(card.id, fields, fields.copy(translation = "Y"), null)
        val room = stored(card.id)
        assertEquals(SaveResult.Deleted(room), result)
        assertEquals(DELETED_IN_ANKI, room.status)
        assertEquals("Y", room.translation)
    }

    @Test fun saveReportsMissingNoteTypeFields() = runTest {
        val card = sentCard()
        val fields = card.cardFields()
        fakeAnki.updateError = NoteTypeChanged(listOf("Meaning"))

        val result = sync.save(card.id, fields, fields.copy(translation = "Y"), null) as SaveResult.Failed
        assertEquals(listOf("Meaning"), result.missingFields)
        assertEquals(card, stored(card.id))
    }

    @Test fun saveStoresRetranslatedExtras() = runTest {
        val card = sentCard()
        val fields = card.cardFields()

        sync.save(card.id, fields, fields.copy(translation = "перевес"), CardExtras("upper hand", widened = true))
        val room = stored(card.id)
        assertEquals("upper hand", room.lemma)
        assertTrue(room.widened)
        assertEquals("перевес", noteField(card, "Translation"))
    }
}

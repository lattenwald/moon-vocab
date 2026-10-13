package org.lattenwald.moonvocab.domain.cardpage

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lattenwald.moonvocab.data.anki.AnkiAvailability
import org.lattenwald.moonvocab.data.anki.NoteTypeChanged
import org.lattenwald.moonvocab.data.db.CardEntity
import org.lattenwald.moonvocab.domain.card.CardExtras
import org.lattenwald.moonvocab.domain.card.CardField
import org.lattenwald.moonvocab.domain.card.CardStatus.DELETED_IN_ANKI
import org.lattenwald.moonvocab.domain.card.CardStatus.PENDING
import org.lattenwald.moonvocab.domain.card.CardStatus.SENT
import org.lattenwald.moonvocab.domain.card.cardFields
import org.lattenwald.moonvocab.domain.import.ImportTestBase

class CardEditorTest : ImportTestBase() {
    private val editor by lazy { CardEditor(db, fakeAnki, store) }

    private suspend fun TestScope.pendingCard(): CardEntity {
        pipeline().run(req(backup(1)), {})
        return db.cards().byStatus(PENDING).single()
    }

    private suspend fun TestScope.sentCard(): CardEntity {
        sender.send(listOf(pendingCard().id))
        return db.cards().byStatus(SENT).single()
    }

    private suspend fun stored(id: Long): CardEntity = db.cards().byIds(listOf(id)).single()

    private fun editNote(noteId: Long, field: String, value: String) {
        val note = fakeAnki.notes.getValue(noteId)
        fakeAnki.notes[noteId] = note.copy(fields = note.fields + (field to value), mod = note.mod + 1)
    }

    private fun noteField(card: CardEntity, field: String): String? = fakeAnki.notes.getValue(card.ankiNoteId!!).fields[field]

    @Test fun openReadsAChangeMadeInAnkiDroid() = runTest {
        val card = sentCard()
        editNote(card.ankiNoteId!!, "Translation", "neu &amp; mehr")

        val opened = editor.open(card.id) as OpenResult.Fresh
        assertEquals("neu & mehr", opened.card.translation)
        assertEquals(opened.card, stored(card.id))
    }

    @Test fun openMarksAMissingNoteDeletedInAnki() = runTest {
        val card = sentCard()
        fakeAnki.notes.clear()

        val opened = editor.open(card.id) as OpenResult.Deleted
        assertEquals(DELETED_IN_ANKI, opened.card.status)
        assertEquals(DELETED_IN_ANKI, stored(card.id).status)
    }

    @Test fun openShowsTheLocalCopyWhileAnkiDroidIsUnavailable() = runTest {
        val card = sentCard()
        editNote(card.ankiNoteId!!, "Translation", "neu")
        fakeAnki.availability = AnkiAvailability.NO_PERMISSION

        assertEquals(OpenResult.Local(card, AnkiAvailability.NO_PERMISSION, null), editor.open(card.id))
        assertEquals(card, stored(card.id))
    }

    @Test fun saveWritesTheChangedFieldAndKeepsTheOthers() = runTest {
        val card = sentCard()
        val fields = card.cardFields()
        editNote(card.ankiNoteId!!, "Meaning", "<b>to win</b>")

        val saved = editor.save(card.id, fields, fields.copy(translation = "одолеть"), null) as SaveResult.Saved
        assertEquals("одолеть", noteField(card, "Translation"))
        assertEquals("<b>to win</b>", noteField(card, "Meaning"))
        val room = stored(card.id)
        assertEquals(saved.card, room)
        assertEquals("одолеть", room.translation)
        assertEquals("to win", room.meaning)
    }

    @Test fun saveStopsWhenAnEditedFieldChangedInAnkiDroid() = runTest {
        val card = sentCard()
        val fields = card.cardFields()
        editNote(card.ankiNoteId!!, "Translation", "X")

        val result = editor.save(card.id, fields, fields.copy(translation = "Y"), null)
        assertEquals(SaveResult.Conflict(stored(card.id), setOf(CardField.TRANSLATION)), result)
        assertEquals("X", (result as SaveResult.Conflict).card.translation)
        assertEquals("X", noteField(card, "Translation"))
    }

    @Test fun saveOnAMissingNoteKeepsTheEditsLocally() = runTest {
        val card = sentCard()
        val fields = card.cardFields()
        fakeAnki.notes.clear()

        val result = editor.save(card.id, fields, fields.copy(translation = "Y"), null)
        val room = stored(card.id)
        assertEquals(SaveResult.Deleted(room), result)
        assertEquals(DELETED_IN_ANKI, room.status)
        assertEquals("Y", room.translation)
    }

    @Test fun saveReportsMissingNoteTypeFields() = runTest {
        val card = sentCard()
        val fields = card.cardFields()
        fakeAnki.updateError = NoteTypeChanged(listOf("Meaning"))

        val result = editor.save(card.id, fields, fields.copy(translation = "Y"), null) as SaveResult.Failed
        assertEquals(listOf("Meaning"), result.missingFields)
        assertEquals(card, stored(card.id))
    }

    @Test fun saveStoresRetranslatedExtras() = runTest {
        val card = sentCard()
        val fields = card.cardFields()

        editor.save(card.id, fields, fields.copy(translation = "перевес"), CardExtras("upper hand", widened = true))
        val room = stored(card.id)
        assertEquals("upper hand", room.lemma)
        assertTrue(room.widened)
        assertEquals("перевес", noteField(card, "Translation"))
    }

    @Test fun saveLocalUpdatesAPendingCard() = runTest {
        val card = pendingCard()
        val fields = card.cardFields()

        val saved = editor.saveLocal(card.id, fields.copy(translation = "Y"), CardExtras("lemma", widened = true))
        val room = stored(card.id)
        assertEquals(saved, room)
        assertEquals("Y", room.translation)
        assertEquals("lemma", room.lemma)
        assertTrue(room.widened)
    }

    @Test fun saveLocalRefusesACardThatIsNoLongerPending() = runTest {
        val card = sentCard()

        assertNull(editor.saveLocal(card.id, card.cardFields().copy(translation = "Y"), null))
        assertEquals(card, stored(card.id))
    }
}

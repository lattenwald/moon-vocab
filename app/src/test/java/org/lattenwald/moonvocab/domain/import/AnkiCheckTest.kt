package org.lattenwald.moonvocab.domain.import

import kotlin.test.assertFailsWith
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.lattenwald.moonvocab.data.anki.AnkiAvailability
import org.lattenwald.moonvocab.domain.card.CardStatus.DELETED_IN_ANKI
import org.lattenwald.moonvocab.domain.card.CardStatus.SENT

class AnkiCheckTest : ImportTestBase() {
    @Test fun editedNoteRefreshesCardAndMissingNoteMarksItDeleted() = runTest {
        pipeline().run(req(backup(2), send = true), {})
        assertEquals(0, check.run())
        val (edited, gone) = db.cards().byStatus(SENT)
        val noteId = edited.ankiNoteId!!
        val note = fakeAnki.notes.getValue(noteId)
        fakeAnki.notes[noteId] = note.copy(fields = note.fields + ("Translation" to "neu &amp; <b>x</b>"), mod = 5)
        fakeAnki.notes.remove(gone.ankiNoteId)

        assertEquals(2, check.run())
        val cards = db.cards().observeAll().first().associateBy { it.id }
        assertEquals("neu & x", cards.getValue(edited.id).translation)
        assertEquals(5L, cards.getValue(edited.id).ankiMod)
        assertEquals(DELETED_IN_ANKI, cards.getValue(gone.id).status)
        assertEquals(gone.ankiNoteId, cards.getValue(gone.id).ankiNoteId)
    }

    @Test fun failingReadThrowsFromCheckButNotFromRun() = runTest {
        pipeline().run(req(backup(1), send = true), {})
        fakeAnki.failReads = true
        assertFailsWith<NullPointerException> { check.check() }
        assertEquals(0, check.run())
    }

    @Test fun checkReportsUnavailableAnkiDroid() = runTest {
        fakeAnki.availability = AnkiAvailability.NO_PERMISSION
        assertEquals(CheckResult.Unavailable(AnkiAvailability.NO_PERMISSION), check.check())
    }
}

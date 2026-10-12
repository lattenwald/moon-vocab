package org.lattenwald.moonvocab.domain.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkLocatorTest {
    private fun locator(chapters: List<String>, notes: List<NoteRef> = emptyList(), chaptersMatchMoonReader: Boolean = false) =
        MarkLocator(chapters.map(::normalize), notes, chaptersMatchMoonReader)

    @Test fun markInsideALongerWordIsNotAMatch() {
        val l = locator(listOf("Were not those the days?", "He wore long hose and a doublet."))
        assertEquals(Spot(1, 13, 17), l.locate(chapter = 0, position = 9, marked = "hose"))
    }

    @Test fun markMayEndInsideAWord() =
        assertEquals(Spot(0, 3, 10), locator(listOf("It behooves us.")).locate(0, 3, "behoove"))

    @Test fun repeatedWordWithoutEvidenceIsNotLocated() =
        assertNull(locator(listOf("A grim one.", "A grim two.")).locate(1, 2, "grim"))

    @Test fun repeatedWordIsTheOneBetweenTheNeighbouringNotes() {
        val notes = listOf(NoteRef(0, 14, "Alpha"), NoteRef(1, 0, "Beta"))
        val l = locator(listOf("Cover", "A grim start. Alpha here.", "Beta there. A grim end."), notes)
        assertEquals(Spot(2, 14, 18), l.locate(1, 14, "grim"))
    }

    @Test fun repeatedWordIsLocatedFromANoteInTheSameChapter() {
        val text = "Zeta begins. " + "Filler. ".repeat(100) + "A grim one. " + "Filler. ".repeat(100) + "A grim two."
        val second = text.lastIndexOf("grim")
        val l = locator(listOf("Front", text), listOf(NoteRef(5, 0, "Zeta")))
        assertEquals(Spot(1, second, second + 4), l.locate(5, second, "grim"))
    }

    @Test fun repeatedWordInTheSavedChapterWhenChaptersMatchMoonReader() =
        assertEquals(Spot(1, 2, 6), locator(listOf("A grim one.", "A grim two."), chaptersMatchMoonReader = true).locate(1, 2, "grim"))

    @Test fun occurrenceExactlyAtTheSavedPositionWinsOverOneNearby() {
        val text = "The Greater Trumps lay there. Those were the Greater Trumps."
        val l = locator(listOf(text), chaptersMatchMoonReader = true)
        assertEquals(Spot(0, 45, 59), l.locate(0, 45, "Greater Trumps"))
        assertNull(l.locate(0, 30, "Greater Trumps"))
    }

    @Test fun notesAtTheirSavedPlacesProveTheChapterNumbers() {
        val l = locator(listOf("Alpha. A grim one.", "A grim two."), listOf(NoteRef(0, 0, "Alpha")))
        assertEquals(Spot(1, 2, 6), l.locate(1, 2, "grim"))
    }

    @Test fun noteAwayFromItsSavedChapterDisprovesTheChapterNumbers() {
        val chapters = listOf("Alpha. A grim one.", "A grim two.")
        assertNull(locator(chapters, listOf(NoteRef(1, 0, "Alpha"))).locate(1, 2, "grim"))
        assertNull(locator(chapters, listOf(NoteRef(1, 0, "Alpha")), chaptersMatchMoonReader = true).locate(1, 2, "grim"))
    }

    @Test fun absentAndBlankMarksAreNotLocated() {
        val l = locator(listOf("Nothing here."))
        assertNull(l.locate(0, 0, "absent"))
        assertNull(l.locate(0, 0, " ${Char(0x00AD)} "))
    }
}

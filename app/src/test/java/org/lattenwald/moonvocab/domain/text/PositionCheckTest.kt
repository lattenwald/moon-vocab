package org.lattenwald.moonvocab.domain.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionCheckTest {
    private val chapters = listOf("Cover", "He wore long hose and a doublet. The hose was red.")

    @Test fun noteAtItsSavedPlaceIsExact() {
        val check = checkPosition(chapters, NoteRef(1, 13, "hose"))
        assertTrue(check.exact)
        assertEquals("hose", check.atPosition)
        assertEquals(emptyList<Occurrence>(), check.occurrences)
    }

    @Test fun missShowsWhatIsThereAndWhereTheTextOccurs() {
        val check = checkPosition(chapters, NoteRef(1, 8, "hose"))
        assertFalse(check.exact)
        assertEquals("long", check.atPosition)
        assertEquals(chapters[1], check.around)
        assertEquals(listOf(Occurrence(1, 13), Occurrence(1, 37)), check.occurrences)
    }

    @Test fun missingChapterHasNothingAtThePosition() {
        val check = checkPosition(chapters, NoteRef(7, 0, "hose"))
        assertFalse(check.exact)
        assertNull(check.atPosition)
        assertNull(check.around)
        assertEquals(2, check.occurrences.size)
    }
}

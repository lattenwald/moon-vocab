package org.lattenwald.moonvocab.domain.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionCheckTest {
    private val text = "He wore long hose and a doublet. The hose was red."
    private val parts = listOf(listOf("Cover"), listOf(text, "Part two: hose again."))

    @Test fun noteAtItsSavedPlaceIsExact() {
        val check = checkPosition(parts, text, 13, "hose")
        assertTrue(check.exact)
        assertEquals(13, check.shownAt)
        assertEquals(emptyList<Occurrence>(), check.occurrences)
    }

    @Test fun missShowsWhatIsThereAndWhereTheTextOccurs() {
        val check = checkPosition(parts, text, 8, "hose")
        assertFalse(check.exact)
        assertEquals(13, check.shownAt)
        assertEquals("long", check.atPosition)
        assertEquals(text, check.around)
        assertEquals(listOf(Occurrence(1, 0, 13), Occurrence(1, 0, 37), Occurrence(1, 1, 10)), check.occurrences)
    }

    @Test fun missingPartHasNothingAtThePosition() {
        val check = checkPosition(parts, null, 0, "hose")
        assertFalse(check.exact)
        assertNull(check.shownAt)
        assertNull(check.atPosition)
        assertEquals(3, check.occurrences.size)
    }
}

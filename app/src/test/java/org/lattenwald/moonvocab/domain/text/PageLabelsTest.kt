package org.lattenwald.moonvocab.domain.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageLabelsTest {
    private val plain = "One two.\nThree four five."
    private val labelled = "Page 1 \nOne two.\n\npageix \n\nThree four\n\nPage 2 \nfive.\n\nPage 3"

    @Test fun aPositionAfterLabelsMovesBackByThem() {
        assertEquals(plain.indexOf("Three"), unlabelledIndex(labelled, plain, labelled.indexOf("Three")))
        assertEquals(plain.indexOf("five"), unlabelledIndex(labelled, plain, labelled.indexOf("five")))
        assertEquals(plain.length, unlabelledIndex(labelled, plain, labelled.length))
    }

    @Test fun textThatDiffersBeyondLabelsHasNoPlace() {
        assertNull(unlabelledIndex(labelled, "One two.\nThree four six.", labelled.indexOf("five") + 2))
        assertNull(unlabelledIndex("Pages 1 \nOne two.", "One two.", 12))
    }
}

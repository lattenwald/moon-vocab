package org.lattenwald.moonvocab.domain.mark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MarkKeyTest {
    @Test fun keyIgnoresTextAndDirectory() = assertEquals(
        markKeys(listOf(KeyInput("/sdcard/Books/A/Diaspora.epub", 5, "myopia")))[0],
        markKeys(listOf(KeyInput("/storage/emulated/0/X/diaspora.EPUB", 5, "myopia, the")))[0],
    )

    @Test fun sameTimeSameBookDiffersByText() {
        val k = markKeys(listOf(KeyInput("/a/b.epub", 5, "one"), KeyInput("/a/b.epub", 5, "two")))
        assertNotEquals(k[0], k[1])
    }

    @Test fun styleDecoding() {
        assertEquals(MarkStyle.WAVY, styleOf("1", 0, 0)); assertEquals(MarkStyle.UNDERLINE, styleOf("0", 1, 0))
        assertEquals(MarkStyle.STRIKETHROUGH, styleOf(null, 0, 1)); assertEquals(MarkStyle.HIGHLIGHT, styleOf("", 0, 0))
    }
}

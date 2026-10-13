package org.lattenwald.moonvocab.domain.mark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MarkKeyTest {
    @Test fun keyIgnoresTextAndDirectory() = assertEquals(
        markKeys(listOf(KeyInput("/sdcard/Books/A/Diaspora.epub", 5, "myopia")))[0],
        markKeys(listOf(KeyInput("/storage/emulated/0/X/diaspora.EPUB", 5, "myopia, the")))[0],
    )

    // From sha256sum. Changing them makes every stored mark import again.
    @Test fun keyFormatIsPinned() = assertEquals(
        listOf(
            "e1be032af62ee42aff51abf64e1ae0cbe7eca898af58ec2d8b6e58d959ef979e",
            "48e3150a10e3a4c1ff8a9dedbb8caba727d36ab0f02d5f49955723e3d632e1bf",
        ),
        markKeys(listOf(KeyInput("/sdcard/Books/Diaspora.epub", 5, "x"))) +
            markKeys(listOf(KeyInput("/b/Diaspora.epub", 5, "one"), KeyInput("/b/Diaspora.epub", 5, "two"))).take(1),
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

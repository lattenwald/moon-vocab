package org.lattenwald.moonvocab.domain.card

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkedTextTest {
    @Test fun markRangeIsInDecodedText() {
        val m = markedText("a &amp; <span class=\"mark\">b&lt;c</span> d")
        assertEquals("a & b<c d", m.text)
        assertEquals("b<c", m.text.substring(m.mark!!))
    }

    @Test fun noMarkGivesPlainText() = assertEquals(MarkedText("…plain text…", null), markedText("…plain text…"))

    @Test fun foreignTagsAreDropped() =
        assertEquals("bold word", markedText("<b>bold</b> <span class=\"mark\">word</span>").text)
}

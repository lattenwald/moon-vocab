package org.lattenwald.moonvocab.domain.text

import org.junit.Assert.assertEquals
import org.junit.Test

class NormalizeTest {
    @Test fun removesSoftHyphenAndKeepsOffsets() {
        val n = normalize("Schnal\u00ADlen und")
        assertEquals("Schnallen und", n.text)
        assertEquals(7, n.originalIndex(6))
        assertEquals(14, n.originalIndex(n.text.length))
    }

    @Test fun collapsesNbspAndNewlines() = assertEquals("a b c", normalize("a\u00A0\n  b\tc").text)

    @Test fun anImageReadsAsASpace() = assertEquals("a b c", normalize("a \uFFFC b\uFFFCc").text)

    @Test fun unifiesQuotes() = assertEquals("Hacker's \"Mind\"", normalize("Hacker\u2019s \u201CMind\u201D").text)

    @Test fun composesNfd() = assertEquals("\u00E9", normalize("e\u0301").text)

    @Test fun nfcExpansionKeepsOffsets() {
        val n = normalize("\u0958x")
        assertEquals("\u0915\u093Cx", n.text)
        assertEquals(0, n.originalIndex(1))
        assertEquals(1, n.originalIndex(2))
        assertEquals(2, n.originalIndex(n.text.length))
    }

    @Test fun nfcExpansionThenSpaceKeepsOffsets() {
        val n = normalize("\u0958 ")
        assertEquals("\u0915\u093C ", n.text)
        assertEquals(1, n.originalIndex(2))
        assertEquals(2, n.originalIndex(n.text.length))
    }
}

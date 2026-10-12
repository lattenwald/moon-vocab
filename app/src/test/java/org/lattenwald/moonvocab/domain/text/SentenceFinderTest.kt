package org.lattenwald.moonvocab.domain.text

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val en = Locale.ENGLISH

private fun findSentence(chapters: List<String>, chapter: Int, position: Int, marked: String, locale: Locale): SentenceResult {
    val normalized = chapters.map(::normalize)
    val spot = MarkLocator(normalized, emptyList(), chaptersMatchMoonReader = true).locate(chapter, position, marked)
        ?: return SentenceResult.NotFound
    return sentenceAt(chapters, normalized, spot, locale)
}

class SentenceFinderTest {
    @Test fun softHyphenInBook() {
        val r = findSentence(listOf("Mit Falten, Taschen, Schnal${Char(0x00AD)}len und Knöpfen versehen."), 0, 20, "Schnallen", Locale.GERMAN)
        assertTrue(r is SentenceResult.Found)
    }

    @Test fun abbreviationSplitIsWidened() {
        val r = findSentence(listOf("Er kam z. B. spät nach Hause."), 0, 15, "spät", Locale.GERMAN) as SentenceResult.Found
        assertTrue(r.sentence.split(' ').size >= 6)
    }

    @Test fun markLongerThanSentenceIsItsOwnSentence() {
        val m = "It was a joke. It was not funny."
        val r = findSentence(listOf("Intro. $m Outro."), 0, 7, m, en) as SentenceResult.Found
        assertEquals(m, r.sentence)
    }

    @Test fun caseInsensitiveMatchKeepsOffsetsAfterLengthChangingLowercase() {
        val r = findSentence(listOf("${Char(0x0130)}".repeat(4) + ". Then the Hapless man left the room."), 0, 15, "HAPLESS", en)
            as SentenceResult.Found
        assertEquals("Then the Hapless man left the room.", r.sentence)
        assertEquals("Hapless", r.sentence.substring(r.markStart, r.markEnd))
    }

    @Test fun headingLineIsNotPartOfTheSentence() {
        val r = findSentence(listOf("Chapter Three\nHe was as hapless as ever."), 0, 24, "hapless", en) as SentenceResult.Found
        assertEquals("He was as hapless as ever.", r.sentence)
    }

    @Test fun wideningStaysInsideTheParagraph() {
        val r = findSentence(listOf("Chapter Three\nHe ran. The night was long and cold there."), 0, 17, "ran", en) as SentenceResult.Found
        assertEquals("He ran. The night was long and cold there.", r.sentence)
        assertEquals("ran", r.sentence.substring(r.markStart, r.markEnd))
    }

    @Test fun markAcrossParagraphsIsItsOwnSentence() {
        val r = findSentence(listOf("Intro here.\nIt was over\nand done. Outro."), 0, 19, "over and done", en) as SentenceResult.Found
        assertEquals("over\nand done", r.sentence)
    }
}

package org.lattenwald.moonvocab.domain.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val KLEID = "Er trug ein Kleid mit Falten, Taschen, Schnallen und Knöpfen."
private val SCHNALLEN = KLEID.indexOf("Schnallen")

class ExcerptTest {
    @Test fun focusLongerThanLimitIsTheExcerpt() {
        val f = "one two three four five six seven eight nine"
        assertEquals(Excerpt(f, 0, f.length), cutExcerpt("x $f y", 2, 2 + f.length, 6))
    }

    @Test fun windowIsCentredOnFocusWithEllipses() {
        val s = "a b c d e f g h i j"
        assertEquals(Excerpt("…c d e f g…", 5, 6), cutExcerpt(s, s.indexOf('e'), s.indexOf('e') + 1, 5))
    }

    @Test fun windowAtSentenceStartTakesWordsFromTheRight() {
        assertEquals(Excerpt("a b c…", 0, 1), cutExcerpt("a b c d e f", 0, 1, 3))
    }

    @Test fun gluedEllipsisIsNotAWord() = assertEquals(3, wordCount("…mit  Falten, Taschen…"))

    @Test fun modelExcerptEllipsesComeFromItsPositionInTheSentence() {
        assertEquals(Excerpt("…Taschen, Schnallen und…", 10, 19), acceptModelExcerpt("Taschen, [Schnallen] und", KLEID, "Schnallen", SCHNALLEN, 15))
        assertEquals(Excerpt("Er trug ein Kleid…", 8, 11), acceptModelExcerpt("…Er trug [ein] Kleid", KLEID, "ein", KLEID.indexOf("ein"), 15))
    }

    @Test fun modelExcerptDifferingOnlyInCaseKeepsSentenceCasing() {
        assertEquals(Excerpt("…Taschen, Schnallen und…", 10, 19), acceptModelExcerpt("…taschen, [schnallen] und…", KLEID, "Schnallen", SCHNALLEN, 15))
    }

    @Test fun modelExcerptBracketingAnotherWordIsRejected() {
        assertNull(acceptModelExcerpt("…[Taschen], Schnallen und…", KLEID, "Schnallen", SCHNALLEN, 15))
    }

    @Test fun modelExcerptOverTheWordLimitIsRejected() {
        assertNull(acceptModelExcerpt("…mit Falten, Taschen, [Schnallen] und…", KLEID, "Schnallen", SCHNALLEN, 4))
    }

    @Test fun modelExcerptStartingInsideAWordIsRejected() {
        assertNull(acceptModelExcerpt("…llen und [Knöpfen]", KLEID, "Knöpfen", KLEID.indexOf("Knöpfen"), 15))
    }

    @Test fun htmlIsEscaped() = assertEquals("a &lt;b&gt; <span class=\"mark\">c</span>", markHtml("a <b> c", 6, 7))

    @Test fun ampersandIsEscapedInAndAroundTheMark() = assertEquals("a &amp; <span class=\"mark\">b&amp;c</span>", markHtml("a & b&c", 4, 7))
}

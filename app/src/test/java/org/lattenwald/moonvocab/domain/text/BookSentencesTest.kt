package org.lattenwald.moonvocab.domain.text

import org.junit.Assert.assertEquals
import org.junit.Test
import org.lattenwald.moonvocab.data.books.BookText
import org.lattenwald.moonvocab.data.books.MoonChapters

class BookSentencesTest {
    private fun moonBook(vararg splits: String, labelled: List<String> = splits.toList()) =
        BookText(null, null, null, chapters = listOf(splits.joinToString("\n")), moon = MoonChapters(listOf(splits.toList()), listOf(labelled)))

    private fun find(text: BookText, mark: MarkPlace, marks: List<MarkPlace> = emptyList()) = BookSentences(text, marks).find(mark, null)

    private fun mark(chapter: Int, split: Int, position: Int, text: String, markedAt: Long = 0L) = MarkPlace(chapter, split, position, markedAt, text)

    private val Lookup.found get() = (sentence as SentenceResult.Found).sentence

    @Test fun theMoonTextAnswersAtOrNearThePosition() {
        val book = moonBook("Cover page. He met a grim man on the road today.")
        val at = find(book, mark(0, 0, 21, "grim"))
        assertEquals(Via.AT_POSITION, at.via)
        assertEquals("He met a grim man on the road today.", at.found)
        assertEquals(Via.MOVED, find(book, mark(0, 0, 80, "grim")).via)
    }

    @Test fun marksFromBeforePageLabelsCountInTheUnlabelledText() {
        val book = moonBook("A grim man stood by the door.", labelled = listOf("Page 1 \nA grim man stood by the door."))
        assertEquals(Via.AT_POSITION, find(book, mark(0, 0, 2, "grim")).via)
        assertEquals(Via.AT_POSITION, find(book, mark(0, 0, 10, "grim", markedAt = 1_800_000_000_000L)).via)
    }

    @Test fun aRepeatedWordMissedInTheMoonTextIsTheOneInTheMarksSplit() {
        val book = moonBook("A grim one stood by the door.", "A gr­im two stood by the door.")
        val lookup = find(book, mark(0, 1, 2, "grim"))
        assertEquals(Via.ANCHORS, lookup.via)
        assertEquals("A gr­im two stood by the door.", lookup.found)
    }

    @Test fun theSplitPicksThePart() {
        val book = moonBook("A grim one stood by the door.", "A grim two stood by the door.")
        val lookup = find(book, mark(0, 1, 2, "grim"))
        assertEquals(Via.AT_POSITION, lookup.via)
        assertEquals("A grim two stood by the door.", lookup.found)
    }

    @Test fun aMarkInTheNextSplitPlacesARepeatedWord() {
        val book = moonBook("Alpha. A grim one stood by the door.", "Delta. A grim two stood by the door.")
        val lookup = find(book, mark(0, 0, 500, "grim"), marks = listOf(mark(0, 1, 0, "Delta")))
        assertEquals("A grim one stood by the door.", lookup.found)
    }

    @Test fun withoutMoonTextTheChaptersAreSearchedButTheirNumbersNotTrusted() {
        val book = BookText(null, null, null, listOf("A grim one stood by the door.", "A grim two stood by the old gate."))
        assertEquals("A grim two stood by the old gate.", find(book, mark(1, 0, 2, "gate")).found)
        assertEquals(Lookup(SentenceResult.NotFound, null), find(book, mark(1, 0, 2, "grim")))
    }

    @Test fun aMarkOutsideTheMoonPartsIsFoundOnlyByAUniqueText() {
        val book = moonBook("A grim one stood by the door.", "A grim two, and an owl.")
        assertEquals("A grim two, and an owl.", find(book, mark(0, 5, 0, "owl")).found)
        assertEquals(Lookup(SentenceResult.NotFound, null), find(book, mark(0, 5, 2, "grim")))
    }
}

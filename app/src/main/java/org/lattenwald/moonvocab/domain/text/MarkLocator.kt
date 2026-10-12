package org.lattenwald.moonvocab.domain.text

import kotlin.math.abs

data class NoteRef(val chapter: Int, val position: Int, val text: String)

data class Spot(val chapter: Int, val start: Int, val end: Int)

// Moon+ chapter numbers match the EPUB spine only in some books, so a repeated word is placed only by evidence.
class MarkLocator(
    val chapters: List<NormalizedText>,
    private val notes: List<NoteRef>,
    private val chaptersMatchMoonReader: Boolean,
) {
    private val starts = IntArray(chapters.size).also { s ->
        var offset = 0
        chapters.forEachIndexed { i, c -> s[i] = offset; offset += c.text.length + 1 }
    }

    // Lowercased char by char, so offsets stay put; the newline joins chapters and never matches a normalized needle.
    private val lower: String = chapters.joinToString("\n") { it.text }.let { s -> String(CharArray(s.length) { s[it].lowercaseChar() }) }

    private class Anchor(val chapter: Int, val position: Int, val offset: Int)

    private val anchors: List<Anchor> by lazy {
        val unique = notes.mapNotNull { n ->
            val needle = needle(n.text)?.takeIf { it.length >= MIN_ANCHOR_CHARS } ?: return@mapNotNull null
            occurrences(needle).singleOrNull()?.let { Anchor(n.chapter, n.position, it) }
        }.sortedWith(compareBy({ it.chapter }, { it.position }))
        inReadingOrder(unique)
    }

    private val chapterNumbersProven: Boolean by lazy {
        (chaptersMatchMoonReader || anchors.isNotEmpty()) && anchors.all { a ->
            val chapter = chapterOf(a.offset)
            chapter == a.chapter && abs(chapters[chapter].originalIndex(a.offset - starts[chapter]) - a.position) <= tolerance(a.position)
        }
    }

    fun locate(chapter: Int, position: Int, marked: String): Spot? {
        val needle = needle(marked) ?: return null
        val found = occurrences(needle)
        val offset = when (found.size) {
            0 -> null
            1 -> found[0]
            else -> betweenNotes(found, chapter, position) ?: nearSameChapterNote(found, chapter, position)
                ?: inSavedChapter(found, chapter, position)
        } ?: return null
        val at = chapterOf(offset)
        return Spot(at, offset - starts[at], offset - starts[at] + needle.length)
    }

    private fun betweenNotes(found: List<Int>, chapter: Int, position: Int): Int? {
        val before = anchors.lastOrNull { it.chapter < chapter || it.chapter == chapter && it.position < position }
        val after = anchors.firstOrNull { it.chapter > chapter || it.chapter == chapter && it.position > position }
        if (before == null && after == null) return null
        val low = before?.offset ?: -1
        val high = after?.offset ?: Int.MAX_VALUE
        return found.filter { it in (low + 1) until high }.singleOrNull()
    }

    // Within one Moon+ chapter, distances match the text's up to how Moon+ counts whitespace.
    private fun nearSameChapterNote(found: List<Int>, chapter: Int, position: Int): Int? {
        val note = anchors.filter { it.chapter == chapter }.minByOrNull { abs(it.position - position) } ?: return null
        val distance = position - note.position
        val expected = note.offset + distance
        return found.filter { abs(it - expected) <= tolerance(distance) }.singleOrNull()
    }

    private fun inSavedChapter(found: List<Int>, chapter: Int, position: Int): Int? {
        if (!chapterNumbersProven || chapter !in chapters.indices) return null
        val inChapter = found.filter { chapterOf(it) == chapter }
        fun distance(offset: Int) = abs(chapters[chapter].originalIndex(offset - starts[chapter]) - position)
        return inChapter.filter { distance(it) == 0 }.singleOrNull()
            ?: inChapter.filter { distance(it) <= tolerance(position) }.singleOrNull()
    }

    private fun needle(text: String): String? =
        normalize(text).text.trim().takeIf { it.isNotEmpty() }?.let { s -> String(CharArray(s.length) { s[it].lowercaseChar() }) }

    // A mark starts a word but may stop inside one: Moon+ saves "behoove" for a selection in "behooves".
    private fun occurrences(needle: String): List<Int> {
        val wordStart = needle[0].isLetterOrDigit()
        val result = ArrayList<Int>()
        var i = lower.indexOf(needle)
        while (i >= 0) {
            if (!wordStart || i == 0 || !lower[i - 1].isLetterOrDigit()) result += i
            i = lower.indexOf(needle, i + 1)
        }
        return result
    }

    private fun chapterOf(offset: Int): Int {
        val i = starts.binarySearch(offset)
        return if (i >= 0) i else -i - 2
    }

    private companion object {
        const val MIN_ANCHOR_CHARS = 3

        // Moon+ positions drift from the text's by up to a few percent across a chapter.
        fun tolerance(distance: Int): Int = 40 + abs(distance) / 10

        fun inReadingOrder(anchors: List<Anchor>): List<Anchor> {
            if (anchors.isEmpty()) return anchors
            val tails = ArrayList<Int>()
            val previous = IntArray(anchors.size) { -1 }
            for (i in anchors.indices) {
                var lo = 0
                var hi = tails.size
                while (lo < hi) {
                    val mid = (lo + hi) / 2
                    if (anchors[tails[mid]].offset < anchors[i].offset) lo = mid + 1 else hi = mid
                }
                if (lo > 0) previous[i] = tails[lo - 1]
                if (lo == tails.size) tails += i else tails[lo] = i
            }
            val run = ArrayList<Anchor>()
            var i = tails.last()
            while (i >= 0) {
                run += anchors[i]
                i = previous[i]
            }
            return run.asReversed()
        }
    }
}

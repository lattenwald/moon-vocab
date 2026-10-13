package org.lattenwald.moonvocab.domain.text

import java.util.Locale
import org.lattenwald.moonvocab.data.books.BookText

data class MarkPlace(val chapter: Int, val split: Int, val position: Int, val markedAt: Long, val text: String)

enum class Via { AT_POSITION, MOVED, ANCHORS }

data class Lookup(val sentence: SentenceResult, val via: Via?)

class BookSentences(private val text: BookText, private val marks: List<MarkPlace>) {
    fun part(mark: MarkPlace): String? = text.moon?.part(mark.chapter, mark.split, mark.markedAt)

    fun find(mark: MarkPlace, language: String?): Lookup {
        val locale = language?.let(Locale::forLanguageTag) ?: Locale.ROOT
        val part = part(mark)
        val start = part?.let { moonNoteStart(it, mark.position, mark.text) }
        if (part != null && start != null) {
            val via = if (start == mark.position) Via.AT_POSITION else Via.MOVED
            return Lookup(sentenceAt(part, start, start + mark.text.length, locale), via)
        }
        val spot = anchors.locator.locate(anchors.index(mark), mark.position, mark.text)
            ?: return Lookup(SentenceResult.NotFound, null)
        return Lookup(sentenceAt(anchors.parts, anchors.locator.chapters, spot, locale), Via.ANCHORS)
    }

    private inner class Anchors(val parts: List<String>, val index: (MarkPlace) -> Int?, proven: Boolean) {
        val locator = MarkLocator(
            parts.map(::normalize),
            marks.mapNotNull { m -> index(m)?.let { NoteRef(it, m.position, m.text) } },
            chaptersMatchMoonReader = proven,
        )
    }

    private val anchors: Anchors by lazy {
        val moon = text.moon?.parts
        if (moon == null) {
            Anchors(text.chapters, { m -> m.chapter.takeIf { m.split == 0 && it in text.chapters.indices } }, proven = false)
        } else {
            val firstIndex = moon.runningFold(0) { at, splits -> at + splits.size }
            val index = { m: MarkPlace -> moon.getOrNull(m.chapter)?.takeIf { m.split in it.indices }?.let { firstIndex[m.chapter] + m.split } }
            Anchors(moon.flatten(), index, proven = true)
        }
    }
}

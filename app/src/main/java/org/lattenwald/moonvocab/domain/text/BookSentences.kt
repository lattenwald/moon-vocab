package org.lattenwald.moonvocab.domain.text

import java.util.Locale
import org.lattenwald.moonvocab.data.books.BookText
import org.lattenwald.moonvocab.data.books.MoonChapters
import org.lattenwald.moonvocab.data.books.parts

data class MarkPlace(val chapter: Int, val split: Int, val position: Int, val markedAt: Long, val text: String)

enum class Via { AT_POSITION, MOVED, ANCHORS }

data class Lookup(val sentence: SentenceResult, val via: Via?)

class BookSentences(text: BookText, private val marks: List<MarkPlace>) {
    private val moon: MoonChapters? = text.moon
    private val parts = text.parts
    private val firstIndex = parts.runningFold(0) { at, splits -> at + splits.size }
    private val flat by lazy { parts.flatten() }
    private val locator by lazy {
        val anchors = marks.mapNotNull { m -> index(m)?.let { NoteRef(it, unlabelledPosition(m), m.text) } }
        MarkLocator(flat.map(::normalize), anchors, chaptersMatchMoonReader = moon != null)
    }

    fun part(mark: MarkPlace): String? = moon?.part(mark.chapter, mark.split, mark.markedAt)

    fun find(mark: MarkPlace, language: String?): Lookup {
        val locale = language?.let(Locale::forLanguageTag) ?: Locale.ROOT
        val part = part(mark)
        val start = part?.let { moonNoteStart(it, mark.position, mark.text) }
        if (part != null && start != null) {
            val via = if (start == mark.position) Via.AT_POSITION else Via.MOVED
            // Page labels sit on lines of their own, and a sentence stops at a line break.
            val plain = plainPart(mark)
            val at = plain?.let { unlabelledIndex(part, it, start) }?.takeIf { plain.startsWith(mark.text, it) }
            val sentence = if (plain != null && at != null) {
                sentenceAt(plain, at, at + mark.text.length, locale)
            } else {
                sentenceAt(part, start, start + mark.text.length, locale)
            }
            return Lookup(sentence, via)
        }
        val spot = locator.locate(index(mark), unlabelledPosition(mark), mark.text) ?: return Lookup(SentenceResult.NotFound, null)
        return Lookup(sentenceAt(flat, locator.chapters, spot, locale), Via.ANCHORS)
    }

    private fun plainPart(mark: MarkPlace): String? = parts.getOrNull(mark.chapter)?.getOrNull(mark.split)

    private fun unlabelledPosition(mark: MarkPlace): Int {
        val part = part(mark) ?: return mark.position
        val plain = plainPart(mark) ?: return mark.position
        return unlabelledIndex(part, plain, mark.position) ?: mark.position
    }

    private fun index(mark: MarkPlace): Int? =
        parts.getOrNull(mark.chapter)?.takeIf { mark.split in it.indices }?.let { firstIndex[mark.chapter] + mark.split }
}

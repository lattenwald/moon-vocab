package org.lattenwald.moonvocab.domain.text

import java.text.BreakIterator
import java.util.Locale

sealed interface SentenceResult {
    data class Found(val sentence: String, val markStart: Int, val markEnd: Int) : SentenceResult
    data object NotFound : SentenceResult
}

private const val MIN_WORDS = 6
private val SOFT_HYPHEN_CHAR = Char(0x00AD)

// normalized[i] must be normalize(chapters[i]).
fun sentenceAt(chapters: List<String>, normalized: List<NormalizedText>, spot: Spot, locale: Locale): SentenceResult.Found {
    val norm = normalized[spot.chapter]
    return sentenceAt(chapters[spot.chapter], norm.originalIndex(spot.start), norm.originalIndex(spot.end), locale)
}

fun sentenceAt(text: String, start: Int, end: Int, locale: Locale): SentenceResult.Found {
    val markStart = start
    var markEnd = end
    while (markEnd > markStart && text[markEnd - 1] == SOFT_HYPHEN_CHAR) markEnd--

    // ICU (Android) breaks sentences at LF, the JDK BreakIterator doesn't.
    val paraStart = text.lastIndexOf('\n', markStart - 1) + 1
    val paraEnd = text.indexOf('\n', markStart).let { if (it < 0) text.length else it }
    var begin = markStart
    var end = markEnd
    if (markEnd <= paraEnd) {
        val para = text.substring(paraStart, paraEnd)
        val (b, e) = sentenceInParagraph(para, markStart - paraStart, markEnd - paraStart, locale)
        begin = paraStart + b
        end = paraStart + e
    }
    while (begin < end && text[begin].isWhitespace()) begin++
    while (end > begin && text[end - 1].isWhitespace()) end--
    return SentenceResult.Found(text.substring(begin, end), markStart - begin, markEnd - begin)
}

private fun sentenceInParagraph(para: String, markStart: Int, markEnd: Int, locale: Locale): Pair<Int, Int> {
    val bi = BreakIterator.getSentenceInstance(locale)
    bi.setText(para)
    var end = bi.following(markStart)
    var begin = bi.previous()
    if (markEnd > end) {
        begin = markStart
        end = markEnd
    }
    if (wordCount(para, begin, end) < MIN_WORDS) {
        begin = previousSentenceStart(bi, begin)
        end = nextSentenceEnd(bi, end, para.length)
    }
    return begin to end
}

private fun previousSentenceStart(bi: BreakIterator, begin: Int): Int {
    if (begin == 0) return 0
    val onBoundary = bi.isBoundary(begin)
    val start = bi.preceding(begin)
    return if (onBoundary || start == 0) start else bi.preceding(start)
}

private fun nextSentenceEnd(bi: BreakIterator, end: Int, length: Int): Int {
    if (end >= length) return length
    val onBoundary = bi.isBoundary(end)
    val next = bi.following(end)
    return if (onBoundary || next >= length) next else bi.following(next)
}

private fun wordCount(text: String, begin: Int, end: Int): Int {
    var count = 0
    for (i in begin until end) {
        if (!text[i].isWhitespace() && (i == begin || text[i - 1].isWhitespace())) count++
    }
    return count
}

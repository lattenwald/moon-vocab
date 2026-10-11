package org.lattenwald.moonvocab.domain.card

import org.lattenwald.moonvocab.domain.text.normalizeForMatch

data class Excerpt(val text: String, val focusStart: Int, val focusEnd: Int)

private const val ELLIPSIS = '…'

fun wordCount(s: String): Int = wordRanges(s).size

fun acceptModelExcerpt(modelExcerpt: String?, sentence: String, focus: String, focusStart: Int, maxWords: Int): Excerpt? {
    if (modelExcerpt == null) return null
    val model = normalizeForMatch(modelExcerpt)
    val wanted = normalizeForMatch(focus).trim()
    val (open, close) = singleBracketPair(model) ?: return null
    if (!model.substring(open + 1, close).equals(wanted, ignoreCase = true)) return null

    val stripped = model.filterNot { it == '[' || it == ']' || it == ELLIPSIS }
    val core = stripped.trim()
    if (wordCount(core) > maxWords) return null
    val focusInCore = model.substring(0, open).count { it != ELLIPSIS } - stripped.indexOfFirst { !it.isWhitespace() }
    val at = focusStart - focusInCore
    val end = at + core.length
    if (at < 0 || end > sentence.length || !sentence.regionMatches(at, core, 0, core.length, ignoreCase = true)) return null
    if (!isWordBoundary(sentence, at) || !isWordBoundary(sentence, end)) return null

    val prefix = if (at > 0) ELLIPSIS.toString() else ""
    val suffix = if (end < sentence.length) ELLIPSIS.toString() else ""
    val excerptFocus = prefix.length + focusInCore
    return Excerpt(prefix + sentence.substring(at, end) + suffix, excerptFocus, excerptFocus + wanted.length)
}

fun cutExcerpt(sentence: String, focusStart: Int, focusEnd: Int, maxWords: Int): Excerpt {
    val focusText = sentence.substring(focusStart, focusEnd)
    if (wordCount(focusText) > maxWords) return Excerpt(focusText, 0, focusText.length)

    val words = wordRanges(sentence)
    val first = words.indexOfFirst { it.last >= focusStart }
    val last = words.indexOfLast { it.first < focusEnd }
    val spare = (maxWords - (last - first + 1)).coerceAtLeast(0)
    val right = minOf(spare - minOf(spare / 2, first), words.lastIndex - last)
    val left = minOf(spare - right, first)
    val from = words[first - left].first
    val to = words[last + right].last + 1

    val prefix = if (first - left > 0) ELLIPSIS.toString() else ""
    val suffix = if (last + right < words.lastIndex) ELLIPSIS.toString() else ""
    val shift = prefix.length - from
    return Excerpt(prefix + sentence.substring(from, to) + suffix, focusStart + shift, focusEnd + shift)
}

fun markHtml(text: String, start: Int, end: Int): String =
    escapeHtml(text.substring(0, start)) + "<span class=\"mark\">" + escapeHtml(text.substring(start, end)) + "</span>" +
        escapeHtml(text.substring(end))

internal fun singleBracketPair(s: String): Pair<Int, Int>? {
    val open = s.indexOf('[')
    val close = s.indexOf(']')
    if (open < 0 || close < open || s.indexOf('[', open + 1) >= 0 || s.indexOf(']', close + 1) >= 0) return null
    if (s.substring(open + 1, close).isBlank()) return null
    return open to close
}

private fun isWordBoundary(s: String, i: Int): Boolean =
    i == 0 || i == s.length || !s[i - 1].isLetterOrDigit() || !s[i].isLetterOrDigit()

private fun wordRanges(s: String): List<IntRange> {
    val out = mutableListOf<IntRange>()
    var i = 0
    while (i < s.length) {
        if (s[i].isWhitespace()) {
            i++
            continue
        }
        val start = i
        while (i < s.length && !s[i].isWhitespace()) i++
        out += start until i
    }
    return out
}

internal fun escapeHtml(s: String): String = buildString(s.length) {
    for (c in s) {
        when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            else -> append(c)
        }
    }
}

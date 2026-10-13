package org.lattenwald.moonvocab.domain.text

import java.text.Normalizer

class NormalizedText(val text: String, private val toOriginal: IntArray) {
    fun originalIndex(i: Int): Int = toOriginal[i]
}

fun normalize(s: String): NormalizedText {
    val out = StringBuilder(s.length)
    var map = IntArray(s.length + 1)
    fun record(original: Int) {
        // NFC can expand a run (composition exclusions such as U+0958 decompose).
        if (out.length >= map.size) map = map.copyOf(map.size * 2)
        map[out.length] = original
    }
    var i = 0
    while (i < s.length) {
        val cp = s.codePointAt(i)
        when {
            cp == SOFT_HYPHEN -> i += Character.charCount(cp)
            isSpace(cp) -> {
                if (out.isEmpty() || out[out.length - 1] != ' ') {
                    record(i)
                    out.append(' ')
                }
                i += Character.charCount(cp)
            }
            else -> {
                val start = i
                i += Character.charCount(cp)
                while (i < s.length && isCombiningMark(s.codePointAt(i))) {
                    i += Character.charCount(s.codePointAt(i))
                }
                val composed = Normalizer.normalize(s.substring(start, i), Normalizer.Form.NFC)
                for (c in composed) {
                    record(start)
                    out.append(unifyQuote(c))
                }
            }
        }
    }
    record(s.length)
    return NormalizedText(out.toString(), map.copyOf(out.length + 1))
}

fun normalizeForMatch(s: String): String = normalize(s).text

private const val SOFT_HYPHEN = 0x00AD
private const val OBJECT_REPLACEMENT = 0xFFFC

// Moon+ text holds U+FFFC where an image is.
private fun isSpace(cp: Int): Boolean = Character.isWhitespace(cp) || Character.isSpaceChar(cp) || cp == OBJECT_REPLACEMENT
private fun isCombiningMark(cp: Int): Boolean = when (Character.getType(cp)) {
    Character.NON_SPACING_MARK.toInt(),
    Character.COMBINING_SPACING_MARK.toInt(),
    Character.ENCLOSING_MARK.toInt() -> true
    else -> false
}

private fun unifyQuote(c: Char): Char = when (c) {
    '\u2018', '\u2019', '\u02BC' -> '\''
    '\u201C', '\u201D', '\u201E' -> '"'
    else -> c
}

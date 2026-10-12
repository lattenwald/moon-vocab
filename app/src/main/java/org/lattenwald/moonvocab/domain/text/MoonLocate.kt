package org.lattenwald.moonvocab.domain.text

import kotlin.math.abs

// MRTextView.drawLineHighlight: Moon+ shows a note whose text isn't at its position at the occurrence within this distance.
const val MOON_REANCHOR = 200

fun moonNoteStart(part: String, position: Int, text: String): Int? {
    if (text.isEmpty()) return null
    if (position >= 0 && part.startsWith(text, position)) return position
    var best: Int? = null
    var i = part.indexOf(text, (position - MOON_REANCHOR).coerceAtLeast(0))
    while (i != -1 && i < position + MOON_REANCHOR) {
        if (abs(i - position) < MOON_REANCHOR && (best == null || abs(i - position) < abs(best - position))) best = i
        i = part.indexOf(text, i + 1)
    }
    return best
}

package org.lattenwald.moonvocab.domain.text

data class Occurrence(val chapter: Int, val split: Int, val position: Int)

data class PositionCheck(
    val exact: Boolean,
    val shownAt: Int?,
    val atPosition: String?,
    val around: String?,
    val occurrences: List<Occurrence>,
)

fun checkPosition(parts: List<List<String>>, part: String?, position: Int, text: String): PositionCheck {
    val inPart = position.takeIf { part != null && it in 0..part.length }
    val exact = inPart != null && text.isNotEmpty() && part!!.startsWith(text, inPart)
    val shownAt = part?.let { moonNoteStart(it, position, text) }
    val atPosition = inPart?.let { part!!.substring(it, minOf(part.length, it + text.length)) }
    val around = inPart?.let { part!!.substring(maxOf(0, it - CONTEXT), minOf(part.length, it + text.length + CONTEXT)) }
    val occurrences = if (exact || text.isEmpty()) emptyList() else occurrences(parts, text)
    return PositionCheck(exact, shownAt, atPosition, around, occurrences)
}

private fun occurrences(parts: List<List<String>>, text: String): List<Occurrence> = buildList {
    for ((chapter, splits) in parts.withIndex()) for ((split, part) in splits.withIndex()) {
        var from = part.indexOf(text)
        while (from >= 0) {
            if (size == MAX_OCCURRENCES) return@buildList
            add(Occurrence(chapter, split, from))
            from = part.indexOf(text, from + 1)
        }
    }
}

private const val CONTEXT = 80
private const val MAX_OCCURRENCES = 10

package org.lattenwald.moonvocab.domain.text

data class Occurrence(val chapter: Int, val position: Int)

data class PositionCheck(
    val note: NoteRef,
    val exact: Boolean,
    val atPosition: String?,
    val around: String?,
    val occurrences: List<Occurrence>,
)

fun checkPosition(chapters: List<String>, note: NoteRef): PositionCheck {
    val chapter = chapters.getOrNull(note.chapter)
    val position = note.position.takeIf { chapter != null && it in 0..chapter.length }
    val exact = position != null && note.text.isNotEmpty() && chapter!!.startsWith(note.text, position)
    val atPosition = position?.let { chapter!!.substring(it, minOf(chapter.length, it + note.text.length)) }
    val around = position?.let { chapter!!.substring(maxOf(0, it - CONTEXT), minOf(chapter.length, it + note.text.length + CONTEXT)) }
    val occurrences = if (exact || note.text.isEmpty()) emptyList() else occurrences(chapters, note.text)
    return PositionCheck(note, exact, atPosition, around, occurrences)
}

private fun occurrences(chapters: List<String>, text: String): List<Occurrence> = buildList {
    for ((index, chapter) in chapters.withIndex()) {
        var from = chapter.indexOf(text)
        while (from >= 0) {
            if (size == MAX_OCCURRENCES) return@buildList
            add(Occurrence(index, from))
            from = chapter.indexOf(text, from + 1)
        }
    }
}

private const val CONTEXT = 80
private const val MAX_OCCURRENCES = 10

package org.lattenwald.moonvocab.data.anki

private const val SPECIAL = "\\\"*_:()"

fun wordSearch(term: String): String = buildString {
    append("\"w:")
    term.forEachIndexed { i, c ->
        if (c in SPECIAL || (i == 0 && c == '-')) append('\\')
        append(c)
    }
    append('"')
}

fun elsewhereQuery(terms: List<String>): String =
    "-note:\"${NoteTypeSpec.NAME}\" (" + terms.distinctBy { it.lowercase() }.joinToString(" OR ") { wordSearch(it) } + ")"

fun appIdQuery(mid: Long, uuids: List<String>): String =
    "mid:$mid (" + uuids.joinToString(" OR ") { "AppId:$it" } + ")"

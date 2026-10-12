package org.lattenwald.moonvocab.domain.card

import org.lattenwald.moonvocab.data.anki.NoteTypeSpec

private val SECTION = Regex("""\{\{#\s*([^}]+?)\s*\}\}(.*?)\{\{/\s*\1\s*\}\}""", RegexOption.DOT_MATCHES_ALL)
private val FIELD = Regex("""\{\{\s*([^#/}][^}]*?)\s*\}\}""")

// Sections go first, so a field's HTML is never read as template syntax.
fun fillTemplate(template: String, fields: Map<String, String>, frontSide: String = ""): String {
    var t = template
    while (true) {
        val next = SECTION.replace(t) { m -> if (fields[m.groupValues[1]].isNullOrBlank()) "" else m.groupValues[2] }
        if (next == t) break
        t = next
    }
    return FIELD.replace(t) { m ->
        val name = m.groupValues[1]
        if (name == "FrontSide") frontSide else fields[name].orEmpty()
    }
}

fun previewHtml(fields: Map<String, String>, showAnswer: Boolean, night: Boolean, background: String, text: String): String {
    val front = fillTemplate(NoteTypeSpec.FRONT, fields)
    val body = if (showAnswer) fillTemplate(NoteTypeSpec.BACK, fields, front) else front
    return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">" +
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
        "<style>${NoteTypeSpec.CSS}\nbody{margin:0;background:$background;color:$text}</style></head>" +
        "<body class=\"card${if (night) " nightMode" else ""}\">$body</body></html>"
}

package org.lattenwald.moonvocab.domain.card

data class MarkedText(val text: String, val mark: IntRange?)

private val ENTITIES = listOf(
    "&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&#39;" to "'", "&nbsp;" to " ",
)
private val MARK_OPEN = Regex("""<span\s+class\s*=\s*["']mark["']\s*>""", RegexOption.IGNORE_CASE)

fun markedText(html: String): MarkedText {
    val out = StringBuilder(html.length)
    var markStart = -1
    var markEnd = -1
    var depth = 0
    var i = 0
    while (i < html.length) {
        when (html[i]) {
            '<' -> {
                val close = html.indexOf('>', i)
                if (close < 0) {
                    out.append(html, i, html.length)
                    break
                }
                val tag = html.substring(i, close + 1)
                when {
                    markStart < 0 && MARK_OPEN.matches(tag) -> {
                        markStart = out.length
                        depth = 1
                    }
                    depth > 0 && tag.startsWith("<span", ignoreCase = true) -> depth++
                    depth > 0 && tag.startsWith("</span", ignoreCase = true) -> if (--depth == 0) markEnd = out.length
                }
                i = close + 1
            }
            '&' -> {
                val entity = ENTITIES.firstOrNull { html.startsWith(it.first, i) }
                out.append(entity?.second ?: "&")
                i += entity?.first?.length ?: 1
            }
            else -> out.append(html[i++])
        }
    }
    if (markStart >= 0 && markEnd < 0) markEnd = out.length
    return MarkedText(out.toString(), if (markStart >= 0) markStart until markEnd else null)
}

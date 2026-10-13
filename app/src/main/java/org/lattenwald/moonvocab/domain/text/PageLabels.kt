package org.lattenwald.moonvocab.domain.text

// MoonText.pageNumber: a line of its own, "Page N " or the page id; the part's final trim cuts the space of a last label.
private val PAGE_LABEL = Regex("(?m)^(?:Page [1-9]\\d*|page\\S*)(?: $|\\z)")

fun unlabelledIndex(labelled: String, plain: String, at: Int): Int? {
    if (labelled == plain) return at
    if (at !in 0..labelled.length) return null
    val labels = PAGE_LABEL.findAll(labelled).map { it.range }.iterator()
    var label = if (labels.hasNext()) labels.next() else null
    var j = 0
    for (i in 0 until at) {
        while (label != null && i > label.last) label = if (labels.hasNext()) labels.next() else null
        val c = labelled[i]
        if (label != null && i in label || c.isWhitespace()) continue
        while (j < plain.length && plain[j].isWhitespace()) j++
        if (j == plain.length || plain[j] != c) return null
        j++
    }
    if (at < labelled.length && !labelled[at].isWhitespace()) {
        while (j < plain.length && plain[j].isWhitespace()) j++
    }
    return j
}

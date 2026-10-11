package org.lattenwald.moonvocab.data.books

import org.lattenwald.moonvocab.domain.text.normalizeForMatch

class BookLocator(relativePaths: List<String>) {
    private val candidates = relativePaths.map { it to components(it) }

    fun locate(bookPath: String): String? {
        val wanted = components(bookPath)
        var best: String? = null
        var bestLength = 0
        for ((path, parts) in candidates) {
            val length = matchingSuffixLength(wanted, parts)
            if (length == 0) continue
            if (length > bestLength || (length == bestLength && path.length < best!!.length)) {
                best = path
                bestLength = length
            }
        }
        return best
    }
}

private fun components(path: String): List<String> =
    path.split('/').filter { it.isNotEmpty() }.map { normalizeForMatch(it).lowercase() }

private fun matchingSuffixLength(a: List<String>, b: List<String>): Int {
    var n = 0
    while (n < a.size && n < b.size && a[a.size - 1 - n] == b[b.size - 1 - n]) n++
    return n
}

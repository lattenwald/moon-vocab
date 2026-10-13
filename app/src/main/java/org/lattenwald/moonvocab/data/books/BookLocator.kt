package org.lattenwald.moonvocab.data.books

import org.lattenwald.moonvocab.domain.text.normalizeForMatch

class BookLocator(relativePaths: List<String>) {
    private val candidates = relativePaths.map { it to components(it) }

    fun locate(bookPath: String): String? {
        val wanted = components(bookPath)
        return candidates.map { (path, parts) -> path to matchingSuffixLength(wanted, parts) }
            .filter { (_, length) -> length > 0 }
            .minWithOrNull(BEST)
            ?.first
    }
}

private val BEST = compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first.length }.thenBy { it.first }

private fun components(path: String): List<String> =
    path.split('/').filter { it.isNotEmpty() }.map { normalizeForMatch(it).lowercase() }

private fun matchingSuffixLength(a: List<String>, b: List<String>): Int {
    var n = 0
    while (n < a.size && n < b.size && a[a.size - 1 - n] == b[b.size - 1 - n]) n++
    return n
}

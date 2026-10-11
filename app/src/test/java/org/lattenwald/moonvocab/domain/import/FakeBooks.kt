package org.lattenwald.moonvocab.domain.import

import org.lattenwald.moonvocab.data.books.BookSource
import org.lattenwald.moonvocab.data.books.BookText

class FakeBooks(private val texts: Map<String, BookText>) : BookSource {
    val reads = HashMap<String, Int>()

    override fun text(bookPath: String): BookText? {
        reads.merge(bookPath, 1, Int::plus)
        return texts[bookPath]
    }
}

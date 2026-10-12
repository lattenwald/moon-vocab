package org.lattenwald.moonvocab.domain.library

import org.junit.Assert.assertEquals
import org.junit.Test
import org.lattenwald.moonvocab.data.db.CardEntity
import org.lattenwald.moonvocab.data.db.InboxRow
import org.lattenwald.moonvocab.data.db.LibraryRow
import org.lattenwald.moonvocab.domain.card.CardStatus
import org.lattenwald.moonvocab.domain.card.CardStatus.DELETED_IN_ANKI
import org.lattenwald.moonvocab.domain.card.CardStatus.DISCARDED
import org.lattenwald.moonvocab.domain.card.CardStatus.FAILED
import org.lattenwald.moonvocab.domain.card.CardStatus.PENDING
import org.lattenwald.moonvocab.domain.card.CardStatus.SENT

private fun book(id: Long, language: String?, newestMark: Long? = 0, inAnki: Int = 0) =
    LibraryRow(bookId = id, title = "Book $id", author = null, language = language, inAnki = inAnki, toReview = 0, newestMark = newestMark)

private fun row(id: Long, status: CardStatus, markedAt: Long? = 0, sentAt: Long? = null) = InboxRow(
    card = CardEntity(
        id = id, uuid = "u$id", bookId = 1, sourceLanguage = "de", targetLanguage = "en",
        word = "w$id", lemma = "w$id", grammar = "", excerptHtml = "", translation = "", meaning = "",
        sentenceHtml = "", sourceNote = "", widened = false, invented = false, elsewhereDeck = null,
        status = status, previousStatus = null, ankiNoteId = null, ankiMod = null, sentAt = sentAt, error = null,
    ),
    bookTitle = "Book 1",
    bookAuthor = null,
    markedAt = markedAt,
)

class LibraryTest {
    @Test fun groupsByLanguageWithOtherLast() {
        val library = library(listOf(book(1, "en"), book(2, null), book(3, "de"), book(4, "en")))
        assertEquals(
            listOf<Pair<String?, Int>>("en" to 2, "de" to 1, null to 1),
            library.groups.map { it.language to it.books.size },
        )
    }

    @Test fun booksInAGroupAreNewestMarkFirst() {
        val library = library(listOf(book(1, "de", 100), book(2, "de", 300), book(3, "de", 300), book(4, "de", null)))
        assertEquals(listOf(3L, 2L, 1L, 4L), library.groups.single().books.map { it.bookId })
    }

    @Test fun libraryTotalsBooksAndCardsInAnki() {
        val library = library(listOf(book(1, "de", inAnki = 3), book(2, "en"), book(3, null, inAnki = 5)))
        assertEquals(3, library.books)
        assertEquals(8, library.inAnki)
    }

    @Test fun bookPageSplitsCardsByStatus() {
        val page = bookPage(
            listOf(
                row(1, PENDING, 100), row(2, SENT, sentAt = 5), row(3, FAILED, 300), row(4, DISCARDED),
                row(5, PENDING, 300), row(6, SENT, sentAt = 9), row(7, DELETED_IN_ANKI),
            ),
        )
        assertEquals(listOf(5L, 3L, 1L), page.toReview.map { it.id })
        assertEquals(listOf(6L, 2L), page.inAnki.map { it.id })
        assertEquals(2, page.inAnkiTotal)
        assertEquals(setOf(4L, 7L), page.discarded.map { it.id }.toSet())
    }

    @Test fun inAnkiShowsTheLatestFiftyBySendTime() {
        val page = bookPage((1L..60L).map { sentAt -> row(id = 61 - sentAt, status = SENT, sentAt = sentAt) })
        assertEquals(50, page.inAnki.size)
        assertEquals(1L, page.inAnki.first().id)
        assertEquals(50L, page.inAnki.last().id)
        assertEquals(60, page.inAnkiTotal)
    }

    @Test fun cardsDeletedInAnkiAreListedWithDiscarded() {
        val page = bookPage(listOf(row(1, DISCARDED, 100), row(2, DELETED_IN_ANKI, 300), row(3, DISCARDED, 200), row(4, SENT)))
        assertEquals(listOf(2L, 3L, 1L), page.discarded.map { it.id })
        assertEquals(setOf(2L), page.deletedInAnki)
    }
}

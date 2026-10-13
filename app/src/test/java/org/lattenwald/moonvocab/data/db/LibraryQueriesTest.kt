package org.lattenwald.moonvocab.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.lattenwald.moonvocab.domain.card.CardStatus
import org.lattenwald.moonvocab.domain.card.CardStore
import org.lattenwald.moonvocab.domain.mark.MarkStyle
import org.robolectric.RobolectricTestRunner

private fun book(fileKey: String, language: String? = null) =
    BookEntity(fileKey = fileKey, title = fileKey, author = "A", language = language, relativePath = null)

private fun mark(key: String, bookId: Long, markedAt: Long) = MarkEntity(
    key = key, bookId = bookId, chapter = 1, position = 0, text = key, style = MarkStyle.WAVY, color = -28160,
    markedAt = markedAt, cardId = null,
)

private fun card(bookId: Long, uuid: String, status: CardStatus) = CardEntity(
    uuid = uuid, bookId = bookId, sourceLanguage = "de", targetLanguage = "ru", word = "w", lemma = "w", grammar = "",
    excerptHtml = "", translation = "", meaning = "", sentenceHtml = "", sourceNote = "", widened = false, invented = false,
    elsewhereDeck = null, status = status, previousStatus = null, ankiNoteId = null, ankiMod = null, sentAt = null,
    error = null,
)

@RunWith(RobolectricTestRunner::class)
class LibraryQueriesTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries().build()
    private val store = CardStore(db)

    @After fun close() = db.close()

    @Test fun libraryCountsEachCardOnce() = runTest {
        val b = db.books().upsertByFileKey(book("a.epub", "de"))
        val ids = db.marks().insertIgnoringKnown(
            listOf(mark("s1", b, 10), mark("s2", b, 40), mark("p", b, 20), mark("f", b, 30), mark("d", b, 5)),
        )
        store.add(
            listOf(
                card(b, "s", CardStatus.SENT) to listOf(ids[0], ids[1]),
                card(b, "p", CardStatus.PENDING) to listOf(ids[2]),
                card(b, "f", CardStatus.FAILED) to listOf(ids[3]),
                card(b, "d", CardStatus.DISCARDED) to listOf(ids[4]),
            ),
        )
        assertEquals(
            listOf(LibraryRow(bookId = b, title = "a.epub", author = "A", language = "de", inAnki = 1, toReview = 2, newestMark = 40)),
            db.cards().observeLibrary().first(),
        )
    }

    @Test fun libraryOmitsBooksWithoutCards() = runTest {
        val withCard = db.books().upsertByFileKey(book("a.epub"))
        val marksOnly = db.books().upsertByFileKey(book("b.epub"))
        db.books().upsertByFileKey(book("c.epub"))
        val ids = db.marks().insertIgnoringKnown(listOf(mark("a1", withCard, 1)))
        db.marks().insertIgnoringKnown(listOf(mark("b1", marksOnly, 2)))
        store.add(listOf(card(withCard, "a", CardStatus.PENDING) to ids))
        assertEquals(listOf(withCard), db.cards().observeLibrary().first().map { it.bookId })
    }

    @Test fun observeBookReturnsEveryStatusOfThatBookOnly() = runTest {
        val a = db.books().upsertByFileKey(book("a.epub"))
        val b = db.books().upsertByFileKey(book("b.epub"))
        val ids = db.marks().insertIgnoringKnown(
            listOf(mark("a1", a, 1), mark("a2", a, 7), mark("a3", a, 3), mark("b1", b, 9)),
        )
        store.add(
            listOf(
                card(a, "p", CardStatus.PENDING) to listOf(ids[0], ids[1]),
                card(a, "f", CardStatus.FAILED) to listOf(ids[2]),
                card(a, "s", CardStatus.SENT) to emptyList(),
                card(a, "d", CardStatus.DISCARDED) to emptyList(),
                card(a, "x", CardStatus.DELETED_IN_ANKI) to emptyList(),
                card(b, "other", CardStatus.PENDING) to listOf(ids[3]),
            ),
        )
        val rows = db.cards().observeBook(a).first()
        assertEquals(
            setOf(
                Triple("p", CardStatus.PENDING, 7L),
                Triple("f", CardStatus.FAILED, 3L),
                Triple("s", CardStatus.SENT, null),
                Triple("d", CardStatus.DISCARDED, null),
                Triple("x", CardStatus.DELETED_IN_ANKI, null),
            ),
            rows.map { Triple(it.card.uuid, it.card.status, it.markedAt) }.toSet(),
        )
        assertEquals(5, rows.size)
    }
}

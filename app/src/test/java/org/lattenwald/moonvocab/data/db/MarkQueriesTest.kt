package org.lattenwald.moonvocab.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.lattenwald.moonvocab.domain.card.CardStatus
import org.lattenwald.moonvocab.domain.mark.MarkStyle
import org.robolectric.RobolectricTestRunner

private fun book(fileKey: String, title: String = fileKey, author: String? = null, language: String? = null) =
    BookEntity(fileKey = fileKey, title = title, author = author, language = language, relativePath = null)

private fun mark(key: String, bookId: Long, markedAt: Long = 0) = MarkEntity(
    key = key, bookId = bookId, chapter = 1, position = 0, text = key, style = MarkStyle.WAVY, color = -28160,
    markedAt = markedAt, cardId = null,
)

private fun card(bookId: Long, uuid: String = "u-$bookId", status: CardStatus = CardStatus.PENDING, ankiNoteId: Long? = null) = CardEntity(
    uuid = uuid, bookId = bookId, sourceLanguage = "de", targetLanguage = "ru", word = "w", lemma = "w", grammar = "",
    excerptHtml = "", translation = "", meaning = "", sentenceHtml = "", sourceNote = "", widened = false, invented = false,
    elsewhereDeck = null, status = status, previousStatus = null, ankiNoteId = ankiNoteId, ankiMod = null, sentAt = null,
    error = null,
)

@RunWith(RobolectricTestRunner::class)
class MarkQueriesTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries().build()

    @After fun close() = db.close()

    @Test fun knownKeysAreSkippedAndUnlinkedMarksAreResumable() = runTest {
        val b = db.books().upsertByFileKey(book("diaspora.epub"))
        val first = db.marks().insertIgnoringKnown(listOf(mark("k1", b), mark("k2", b)))
        val again = db.marks().insertIgnoringKnown(listOf(mark("k1", b), mark("k3", b)))
        assertEquals(-1L, again[0]); assertNotEquals(-1L, again[1])
        db.writeBatch(listOf(card(b) to listOf(first[0])))
        assertEquals(setOf("k2", "k3"), db.marks().withoutCard(null).map { it.key }.toSet())
    }

    @Test fun withoutCardFiltersByBookNewestFirst() = runTest {
        val a = db.books().upsertByFileKey(book("a.epub"))
        val b = db.books().upsertByFileKey(book("b.epub"))
        db.marks().insertIgnoringKnown(listOf(mark("a1", a, 1), mark("a2", a, 3), mark("b1", b, 2)))
        assertEquals(listOf("a2", "a1"), db.marks().withoutCard(a).map { it.key })
        assertEquals(listOf("a2", "b1", "a1"), db.marks().withoutCard(null).map { it.key })
    }

    @Test fun unlinkMakesMarksResumable() = runTest {
        val b = db.books().upsertByFileKey(book("diaspora.epub"))
        val ids = db.marks().insertIgnoringKnown(listOf(mark("k1", b), mark("k2", b)))
        db.writeBatch(listOf(card(b) to ids))
        val cardId = db.cards().byStatus(CardStatus.PENDING).single().id
        db.marks().unlink(listOf(cardId))
        assertEquals(setOf("k1", "k2"), db.marks().withoutCard(b).map { it.key }.toSet())
    }

    @Test fun deletingCardMakesItsMarksResumable() = runTest {
        val b = db.books().upsertByFileKey(book("diaspora.epub"))
        val ids = db.marks().insertIgnoringKnown(listOf(mark("k1", b), mark("k2", b)))
        val cardId = db.writeBatch(listOf(card(b) to ids)).single()
        assertEquals(emptyList<MarkEntity>(), db.marks().withoutCard(b))
        db.cards().delete(listOf(cardId))
        assertEquals(setOf("k1", "k2"), db.marks().withoutCard(b).map { it.key }.toSet())
    }

    @Test fun forgetUnsentKeepsSentAndDiscardedCards() = runTest {
        val b = db.books().upsertByFileKey(book("diaspora.epub"))
        val ids = db.marks().insertIgnoringKnown(listOf(mark("pending", b), mark("failed", b), mark("sent", b), mark("discarded", b)))
        db.writeBatch(
            listOf(
                card(b, "p", CardStatus.PENDING) to listOf(ids[0]),
                card(b, "f", CardStatus.FAILED) to listOf(ids[1]),
                card(b, "s", CardStatus.SENT, ankiNoteId = 7) to listOf(ids[2]),
                card(b, "d", CardStatus.DISCARDED) to listOf(ids[3]),
            ),
        )
        db.cards().deleteUnsent()
        assertEquals(setOf("s", "d"), db.cards().observeAll().first().map { it.uuid }.toSet())
        assertEquals(setOf("pending", "failed"), db.marks().withoutCard(b).map { it.key }.toSet())
    }

    @Test fun upsertKeepsIdAndKnownLanguage() = runTest {
        val id = db.books().upsertByFileKey(book("diaspora.epub", title = "Diaspora", author = "Greg Egan", language = "en"))
        val again = db.books().upsertByFileKey(book("diaspora.epub", title = "Diaspora (2)", author = null, language = "de"))
        assertEquals(id, again)
        assertEquals(book("diaspora.epub", title = "Diaspora (2)", author = "Greg Egan", language = "en").copy(id = id), db.books().byId(id))
    }

    @Test fun upsertFillsMissingLanguage() = runTest {
        val id = db.books().upsertByFileKey(book("diaspora.epub"))
        db.books().upsertByFileKey(book("diaspora.epub", language = "en"))
        assertEquals("en", db.books().byId(id)?.language)
    }
}

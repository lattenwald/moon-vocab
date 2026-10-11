package org.lattenwald.moonvocab.domain.inbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lattenwald.moonvocab.data.db.CardEntity
import org.lattenwald.moonvocab.data.db.InboxRow
import org.lattenwald.moonvocab.domain.card.CardStatus
import org.lattenwald.moonvocab.domain.card.CardStatus.FAILED
import org.lattenwald.moonvocab.domain.card.CardStatus.PENDING

private const val X = 10L
private const val Y = 20L

private fun row(
    id: Long,
    book: Long,
    lang: String?,
    markedAt: Long?,
    status: CardStatus = PENDING,
    widened: Boolean = false,
    invented: Boolean = false,
    elsewhere: String? = null,
) = InboxRow(
    card = CardEntity(
        id = id, uuid = "u$id", bookId = book, sourceLanguage = lang, targetLanguage = "en",
        word = "w$id", lemma = "w$id", grammar = "", excerptHtml = "", translation = "", meaning = "",
        sentenceHtml = "", sourceNote = "", widened = widened, invented = invented, elsewhereDeck = elsewhere,
        status = status, previousStatus = null, ankiNoteId = null, ankiMod = null, sentAt = null, error = null,
    ),
    bookTitle = "Book $book",
    bookAuthor = null,
    markedAt = markedAt,
)

class InboxListTest {
    @Test fun groupsAndCardsAreNewestFirst() {
        val rows = listOf(
            row(3, X, "de", 100), row(1, X, "de", 200), row(6, Y, "de", null),
            row(2, X, "de", 200), row(5, X, "de", 300), row(4, Y, "de", 500),
        )
        val list = inboxList(rows, LanguageFilter.All)
        assertEquals(listOf(Y, X), list.groups.map { it.bookId })
        assertEquals(listOf(listOf(4L, 6L), listOf(5L, 2L, 1L, 3L)), list.groups.map { g -> g.cards.map { it.id } })
        assertEquals(listOf(4L, 6L, 5L, 2L, 1L, 3L), list.visibleIds)
    }

    @Test fun filterKeepsOneLanguageInDisplayOrder() {
        val rows = listOf(
            row(1, X, "de", 100), row(2, X, "en", 400), row(3, Y, "de", 300),
            row(4, Y, "de", 200), row(5, Y, "en", 500), row(6, 30, "en", 600),
        )
        val list = inboxList(rows, LanguageFilter.Only("de"))
        assertEquals(LanguageFilter.Only("de"), list.filter)
        assertEquals(listOf(Y, X), list.groups.map { it.bookId })
        assertEquals(listOf(3L, 4L, 1L), list.visibleIds)
        assertEquals(6, list.total)
    }

    @Test fun vanishedFilterLanguageFallsBackToAll() {
        val rows = listOf(row(1, X, "de", 100), row(2, X, "en", 200))
        val list = inboxList(rows, LanguageFilter.Only("fr"))
        assertEquals(LanguageFilter.All, list.filter)
        assertEquals(listOf(2L, 1L), list.visibleIds)
    }

    @Test fun chipsNeedTwoLanguagesAndPutOtherLast() {
        assertTrue(inboxList(listOf(row(1, X, "en", 1), row(2, X, "en", 2)), LanguageFilter.All).chips.isEmpty())

        val rows = listOf(
            row(1, X, null, 1), row(2, X, "fr", 2), row(3, X, "en", 3), row(4, X, "de", 4),
            row(5, X, "en", 5), row(6, X, null, 6), row(7, X, "en", 7),
        )
        assertEquals(
            listOf(LanguageChip("en", 3), LanguageChip("de", 1), LanguageChip("fr", 1), LanguageChip(null, 2)),
            inboxList(rows, LanguageFilter.All).chips,
        )
    }

    @Test fun labelsFollowCardState() {
        val rows = listOf(
            row(1, X, "de", 2, widened = true, invented = true, elsewhere = ""),
            row(2, X, "de", 1, status = FAILED),
        )
        val cards = inboxList(rows, LanguageFilter.All).groups.single().cards
        assertEquals(listOf(RowLabel.Phrase, RowLabel.Invented, RowLabel.Elsewhere("")), cards[0].labels)
        assertEquals(listOf(RowLabel.Failed), cards[1].labels)
        assertEquals(listOf(false, true), cards.map { it.failed })
    }
}

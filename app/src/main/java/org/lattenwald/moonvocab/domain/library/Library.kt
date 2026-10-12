package org.lattenwald.moonvocab.domain.library

import org.lattenwald.moonvocab.data.db.InboxRow
import org.lattenwald.moonvocab.data.db.LibraryRow
import org.lattenwald.moonvocab.domain.card.CardStatus
import org.lattenwald.moonvocab.domain.inbox.InboxCard
import org.lattenwald.moonvocab.domain.inbox.NEWEST_FIRST
import org.lattenwald.moonvocab.domain.inbox.inboxCard

data class LibraryGroup(val language: String?, val books: List<LibraryRow>)

data class Library(val groups: List<LibraryGroup>, val books: Int, val inAnki: Int)

data class BookPage(
    val toReview: List<InboxCard>,
    val inAnki: List<InboxCard>,
    val inAnkiTotal: Int,
    val discarded: List<InboxCard>,
    val deletedInAnki: Set<Long>,
)

fun library(rows: List<LibraryRow>): Library {
    val groups = rows.groupBy { it.language }
        .map { (language, books) ->
            LibraryGroup(language, books.sortedWith(compareByDescending<LibraryRow> { it.newestMark }.thenByDescending { it.bookId }))
        }
        .sortedWith(compareBy<LibraryGroup> { it.language == null }.thenByDescending { it.books.size }.thenBy { it.language })
    return Library(groups = groups, books = rows.size, inAnki = rows.sumOf { it.inAnki })
}

fun bookPage(rows: List<InboxRow>, latest: Int = 50): BookPage {
    val byStatus = rows.groupBy { it.card.status }
    fun cards(vararg statuses: CardStatus) = statuses.flatMap { byStatus[it].orEmpty() }
    val sent = cards(CardStatus.SENT)
    val deleted = cards(CardStatus.DELETED_IN_ANKI)
    return BookPage(
        toReview = cards(CardStatus.PENDING, CardStatus.FAILED).map(::inboxCard).sortedWith(NEWEST_FIRST),
        inAnki = sent.sortedWith(compareByDescending<InboxRow> { it.card.sentAt }.thenByDescending { it.card.id })
            .take(latest)
            .map(::inboxCard),
        inAnkiTotal = sent.size,
        discarded = (cards(CardStatus.DISCARDED) + deleted).map(::inboxCard).sortedWith(NEWEST_FIRST),
        deletedInAnki = deleted.mapTo(HashSet()) { it.card.id },
    )
}

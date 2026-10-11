package org.lattenwald.moonvocab.domain.inbox

import org.lattenwald.moonvocab.data.db.InboxRow
import org.lattenwald.moonvocab.domain.card.CardStatus
import org.lattenwald.moonvocab.domain.card.MarkedText
import org.lattenwald.moonvocab.domain.card.markedText

sealed interface RowLabel {
    data object Phrase : RowLabel
    data object Invented : RowLabel
    data object Failed : RowLabel
    data class Elsewhere(val deck: String) : RowLabel
}

data class InboxCard(
    val id: Long,
    val word: String,
    val grammar: String,
    val translation: String,
    val excerpt: MarkedText?,
    val language: String?,
    val markedAt: Long,
    val failed: Boolean,
    val labels: List<RowLabel>,
)

data class InboxGroup(val bookId: Long, val title: String, val author: String?, val cards: List<InboxCard>)

data class LanguageChip(val language: String?, val count: Int)

sealed interface LanguageFilter {
    data object All : LanguageFilter
    data class Only(val language: String?) : LanguageFilter
}

data class InboxList(
    val groups: List<InboxGroup>,
    val chips: List<LanguageChip>,
    val filter: LanguageFilter,
    val total: Int,
    val visibleIds: List<Long>,
)

private val NEWEST_FIRST = compareByDescending<InboxCard> { it.markedAt }.thenByDescending { it.id }

fun inboxList(rows: List<InboxRow>, filter: LanguageFilter): InboxList {
    val present = rows.mapTo(HashSet()) { it.card.sourceLanguage }
    val applied = if (filter is LanguageFilter.Only && filter.language !in present) LanguageFilter.All else filter
    val kept = if (applied is LanguageFilter.Only) rows.filter { it.card.sourceLanguage == applied.language } else rows
    val groups = kept.groupBy { it.card.bookId }
        .map { (bookId, bookRows) ->
            val first = bookRows.first()
            InboxGroup(bookId, first.bookTitle, first.bookAuthor, bookRows.map(::inboxCard).sortedWith(NEWEST_FIRST))
        }
        .sortedWith(compareByDescending<InboxGroup> { it.cards.first().markedAt }.thenByDescending { it.bookId })
    return InboxList(
        groups = groups,
        chips = languageChips(rows),
        filter = applied,
        total = rows.size,
        visibleIds = groups.flatMap { g -> g.cards.map { it.id } },
    )
}

private fun languageChips(rows: List<InboxRow>): List<LanguageChip> {
    val counts = rows.groupingBy { it.card.sourceLanguage }.eachCount()
    if (counts.size < 2) return emptyList()
    return counts.map { (language, count) -> LanguageChip(language, count) }
        .sortedWith(compareBy<LanguageChip> { it.language == null }.thenByDescending { it.count }.thenBy { it.language })
}

private fun inboxCard(row: InboxRow): InboxCard {
    val card = row.card
    val failed = card.status == CardStatus.FAILED
    return InboxCard(
        id = card.id,
        word = card.word,
        grammar = card.grammar,
        translation = card.translation,
        excerpt = card.excerptHtml.takeUnless { it.isBlank() }?.let(::markedText),
        language = card.sourceLanguage,
        markedAt = row.markedAt ?: 0,
        failed = failed,
        labels = buildList {
            if (card.widened) add(RowLabel.Phrase)
            if (card.invented) add(RowLabel.Invented)
            if (failed) add(RowLabel.Failed)
            card.elsewhereDeck?.let { add(RowLabel.Elsewhere(it)) }
        },
    )
}

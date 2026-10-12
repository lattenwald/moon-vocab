package org.lattenwald.moonvocab.ui.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.lattenwald.moonvocab.R
import org.lattenwald.moonvocab.domain.inbox.InboxCard
import org.lattenwald.moonvocab.ui.common.languageName
import org.lattenwald.moonvocab.ui.inbox.RowFlag
import org.lattenwald.moonvocab.ui.inbox.forInboxList
import org.lattenwald.moonvocab.ui.inbox.inboxColors
import org.lattenwald.moonvocab.ui.theme.BookFontFamily

@Composable
fun BookPage(
    bookId: Long,
    onBack: () -> Unit,
    onOpenCard: (Long) -> Unit,
    vm: BookViewModel = viewModel(factory = BookViewModel.factory(bookId)),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val book = state.book
    val page = state.page
    val colors = inboxColors()
    var discardedOpen by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = colors.screen,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(book?.title.orEmpty(), fontFamily = BookFontFamily, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                subtitle = {
                    if (book != null) {
                        val language = languageName(book.language)
                        Text(book.author?.let { stringResource(R.string.book_subtitle, it, language) } ?: language, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.card_navigate_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.screen),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (book == null) return@Scaffold
        val themeShapes = MaterialTheme.shapes
        MaterialTheme(shapes = remember(themeShapes) { themeShapes.forInboxList() }) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
                item(key = "meta") {
                    Text(
                        countsLine(page.inAnkiTotal, page.toReview.size),
                        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                    )
                }
                if (page.toReview.isNotEmpty()) {
                    item(key = "h-review", contentType = "header") { LibrarySubheader(stringResource(R.string.book_to_review)) }
                    cardRows("review", page.toReview, emptySet(), colors.row, onOpenCard)
                }
                if (page.inAnki.isNotEmpty()) {
                    item(key = "h-anki", contentType = "header") { LibrarySubheader(stringResource(R.string.book_in_anki)) }
                    cardRows("anki", page.inAnki, emptySet(), colors.row, onOpenCard)
                    if (page.inAnkiTotal > page.inAnki.size) {
                        item(key = "latest") {
                            Text(
                                stringResource(R.string.book_latest, page.inAnki.size, page.inAnkiTotal),
                                Modifier.padding(start = 28.dp, top = 6.dp, end = 28.dp, bottom = 4.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.5.sp,
                                lineHeight = 17.sp,
                            )
                        }
                    }
                }
                if (page.discarded.isNotEmpty()) {
                    item(key = "h-discarded", contentType = "header") {
                        DiscardedHeader(page.discarded.size, discardedOpen, onToggle = { discardedOpen = !discardedOpen })
                    }
                    if (discardedOpen) cardRows("discarded", page.discarded, page.deletedInAnki, colors.row, onOpenCard)
                }
                item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

private fun LazyListScope.cardRows(section: String, cards: List<InboxCard>, deleted: Set<Long>, containerColor: Color, onOpenCard: (Long) -> Unit) {
    itemsIndexed(cards, key = { _, card -> "$section${card.id}" }, contentType = { _, _ -> "card" }) { index, card ->
        LibraryRow(index, cards.size, containerColor, onClick = { onOpenCard(card.id) }) { CardText(card, card.id in deleted) }
    }
}

@Composable
private fun CardText(card: InboxCard, deletedInAnki: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            card.word,
            modifier = Modifier.alignByBaseline().weight(1f, fill = false),
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (card.grammar.isNotBlank()) {
            Text(
                card.grammar,
                modifier = Modifier.alignByBaseline(),
                color = colors.onSurfaceVariant,
                fontSize = 14.sp,
                fontFamily = BookFontFamily,
                fontStyle = FontStyle.Italic,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (card.translation.isBlank()) {
        Text(stringResource(R.string.card_no_translation), color = colors.error, fontSize = 13.5.sp, lineHeight = 19.sp)
    } else {
        Text(card.translation, color = colors.onSurfaceVariant, fontSize = 13.5.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    if (deletedInAnki) RowFlag(R.drawable.ic_delete, stringResource(R.string.book_deleted_label))
}

@Composable
private fun DiscardedHeader(count: Int, open: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(if (open) 180f else 0f)
    Row(
        Modifier.fillMaxWidth()
            .padding(start = 12.dp, top = 6.dp, end = 12.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onToggle)
            .heightIn(min = 48.dp)
            .padding(start = 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            pluralStringResource(R.plurals.book_discarded, count, count),
            Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null, modifier = Modifier.rotate(rotation), tint = MaterialTheme.colorScheme.primary)
    }
}

package org.lattenwald.moonvocab.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.lattenwald.moonvocab.R
import org.lattenwald.moonvocab.data.db.LibraryRow
import org.lattenwald.moonvocab.ui.common.languageName
import org.lattenwald.moonvocab.ui.common.EmptyState
import org.lattenwald.moonvocab.ui.inbox.forInboxList
import org.lattenwald.moonvocab.ui.inbox.inboxColors
import org.lattenwald.moonvocab.ui.common.segmentedRowShapes
import org.lattenwald.moonvocab.ui.theme.BookFontFamily

private val RowPadding = PaddingValues(start = 18.dp, top = 14.dp, end = 12.dp, bottom = 14.dp)

@Composable
fun LibraryScreen(
    onOpenBook: (Long) -> Unit,
    modifier: Modifier = Modifier,
    vm: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val library = state.library
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val colors = inboxColors()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = colors.screen,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.library_title)) },
                subtitle = {
                    Text(
                        if (state.loaded) {
                            stringResource(
                                R.string.library_subtitle,
                                pluralStringResource(R.plurals.library_books, library.books, library.books),
                                pluralStringResource(R.plurals.library_cards_in_anki, library.inAnki, library.inAnki),
                            )
                        } else {
                            stringResource(R.string.library_loading)
                        },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.screen),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val themeShapes = MaterialTheme.shapes
        MaterialTheme(shapes = remember(themeShapes) { themeShapes.forInboxList() }) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
                for (group in library.groups) {
                    item(key = "h${group.language}", contentType = "header") { LibrarySubheader(languageName(group.language)) }
                    itemsIndexed(group.books, key = { _, book -> book.bookId }, contentType = { _, _ -> "book" }) { index, book ->
                        LibraryRow(index, group.books.size, colors.row, onClick = { onOpenBook(book.bookId) }) { BookText(book) }
                    }
                }
                if (state.loaded && library.books == 0) {
                    item(key = "empty") { EmptyState(R.drawable.ic_menu_book, R.string.library_empty_title, R.string.library_empty_text) }
                }
                item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun BookText(book: LibraryRow) {
    val colors = MaterialTheme.colorScheme
    Column {
        Text(
            book.title,
            fontFamily = BookFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        book.author?.let {
            Text(it, color = colors.onSurfaceVariant, fontSize = 13.sp, lineHeight = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(countsLine(book.inAnki, book.toReview), Modifier.padding(top = 4.dp), color = colors.onSurfaceVariant, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@Composable
internal fun countsLine(inAnki: Int, toReview: Int) = buildAnnotatedString {
    append(pluralStringResource(R.plurals.library_in_anki, inAnki, inAnki))
    if (toReview > 0) {
        append(", ")
        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)) {
            append(pluralStringResource(R.plurals.library_to_review, toReview, toReview))
        }
    }
}

@Composable
internal fun LibrarySubheader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.padding(start = 24.dp, top = 14.dp, end = 24.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.primary,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
internal fun LibraryRow(index: Int, count: Int, containerColor: Color, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    SegmentedListItem(
        onClick = onClick,
        shapes = segmentedRowShapes(index, count),
        modifier = Modifier.padding(
            start = 12.dp,
            top = if (index > 0) ListItemDefaults.SegmentedGap else 0.dp,
            end = 12.dp,
            bottom = if (index == count - 1) 4.dp else 0.dp,
        ),
        colors = ListItemDefaults.segmentedColors(containerColor = containerColor),
        contentPadding = RowPadding,
        trailingContent = {
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
    ) {
        Column(content = content)
    }
}

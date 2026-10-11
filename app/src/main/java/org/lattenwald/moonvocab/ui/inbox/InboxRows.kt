package org.lattenwald.moonvocab.ui.inbox

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import org.lattenwald.moonvocab.R
import org.lattenwald.moonvocab.data.anki.AnkiAvailability
import org.lattenwald.moonvocab.data.llm.LlmException
import org.lattenwald.moonvocab.domain.import.ImportProgress
import org.lattenwald.moonvocab.domain.import.ImportSummary
import org.lattenwald.moonvocab.domain.inbox.InboxCard
import org.lattenwald.moonvocab.domain.inbox.InboxGroup
import org.lattenwald.moonvocab.domain.inbox.InboxList
import org.lattenwald.moonvocab.domain.inbox.LanguageFilter
import org.lattenwald.moonvocab.domain.inbox.RowLabel
import org.lattenwald.moonvocab.ui.common.MarkedExcerpt
import org.lattenwald.moonvocab.ui.theme.BookFontFamily
import org.lattenwald.moonvocab.work.ImportState

private val OuterCorner = 24.dp
private val InnerCorner = 6.dp
private val PanelShape = RoundedCornerShape(24.dp)
private val RowPadding = PaddingValues(start = 14.dp, top = 14.dp, end = 18.dp, bottom = 15.dp)

internal data class InboxColors(val screen: Color, val row: Color)

@Composable
internal fun inboxColors(): InboxColors {
    val c = MaterialTheme.colorScheme
    return if (c.surface.luminance() > 0.5f) InboxColors(c.surfaceContainer, c.surfaceContainerLowest) else InboxColors(c.surface, c.surfaceContainer)
}

// SegmentedListItem's outer, selected and pressed corners come from the theme's large shape.
internal fun Shapes.forInboxList(): Shapes = copy(large = RoundedCornerShape(OuterCorner))

@Composable
internal fun InboxCardRow(card: InboxCard, index: Int, count: Int, containerColor: Color, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index, count, ListItemDefaults.shapes(shape = RoundedCornerShape(InnerCorner))),
        modifier = modifier,
        colors = ListItemDefaults.segmentedColors(containerColor = containerColor),
        contentPadding = RowPadding,
    ) {
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    card.word,
                    modifier = Modifier.alignByBaseline().weight(1f, fill = false),
                    color = colors.onSurface,
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
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
            if (card.failed) {
                Text(stringResource(R.string.inbox_label_failed), Modifier.padding(top = 1.dp), color = colors.error, fontSize = 14.sp, lineHeight = 20.sp)
            } else if (card.translation.isNotBlank()) {
                Text(card.translation, Modifier.padding(top = 1.dp), color = colors.onSurface, fontSize = 15.sp, lineHeight = 20.sp)
            }
            if (!card.failed && card.excerpt != null) {
                MarkedExcerpt(
                    card.excerpt,
                    modifier = Modifier.padding(top = 6.dp),
                    style = TextStyle(fontFamily = BookFontFamily, fontSize = 14.sp, lineHeight = 21.sp),
                    maxLines = 2,
                )
            }
            for (label in card.labels) {
                when (label) {
                    RowLabel.Phrase -> RowFlag(R.drawable.ic_add, stringResource(R.string.inbox_label_phrase))
                    RowLabel.Invented -> RowFlag(R.drawable.ic_edit, stringResource(R.string.inbox_label_invented))
                    is RowLabel.Elsewhere -> RowFlag(
                        R.drawable.ic_school,
                        if (label.deck.isEmpty()) {
                            stringResource(R.string.inbox_label_elsewhere_unknown)
                        } else {
                            stringResource(R.string.inbox_label_elsewhere, label.deck)
                        },
                    )
                    RowLabel.Failed -> Unit
                }
            }
        }
    }
}

@Composable
private fun RowFlag(icon: Int, text: String) {
    val color = MaterialTheme.colorScheme.tertiary
    Row(Modifier.padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(15.dp), tint = color)
        Text(text, color = color, fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun GroupHeader(group: InboxGroup, modifier: Modifier = Modifier) {
    val variant = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier.fillMaxWidth().heightIn(min = 44.dp).padding(start = 8.dp, top = 6.dp, end = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                group.title,
                modifier = Modifier.alignByBaseline(),
                fontFamily = BookFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            group.author?.let {
                Text(it, modifier = Modifier.alignByBaseline(), color = variant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(group.cards.size.toString(), color = variant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun LanguageChips(list: InboxList, onFilter: (LanguageFilter) -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LanguageChip(stringResource(R.string.inbox_all), list.total, list.filter == LanguageFilter.All) { onFilter(LanguageFilter.All) }
        for (chip in list.chips) {
            val filter = LanguageFilter.Only(chip.language)
            val name = chip.language?.let { languageName(it, locale) } ?: stringResource(R.string.inbox_language_other)
            LanguageChip(name, chip.count, list.filter == filter) { onFilter(filter) }
        }
    }
}

private fun languageName(code: String, locale: Locale): String =
    Locale.forLanguageTag(code).getDisplayLanguage(locale).ifEmpty { code }.replaceFirstChar { it.titlecase(locale) }

@Composable
private fun LanguageChip(name: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Row {
                Text(name)
                Spacer(Modifier.width(6.dp))
                Text(count.toString(), Modifier.alpha(0.75f))
            }
        },
        leadingIcon = if (selected) {
            { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
    )
}

@Composable
private fun Panel(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHighest, PanelShape).padding(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
internal fun ImportProgressPanel(progress: ImportProgress?, modifier: Modifier = Modifier) {
    val p = progress?.takeIf { it.total > 0 }
    Panel(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                p?.bookTitle?.let { stringResource(R.string.inbox_progress_title, it) } ?: stringResource(R.string.inbox_progress_reading),
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
            )
            if (p != null) {
                Text(
                    stringResource(R.string.inbox_progress_count, p.done, p.total),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 1,
                )
            }
        }
        if (p == null) {
            LinearWavyProgressIndicator(Modifier.fillMaxWidth())
        } else {
            val fraction by animateFloatAsState(p.done.toFloat() / p.total, ProgressIndicatorDefaults.ProgressAnimationSpec)
            LinearWavyProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun ImportResultCard(import: ImportState, onDismiss: () -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Panel(modifier) {
        Row {
            Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val failure = import.failure
                val summary = import.summary
                if (failure != null) {
                    ResultTitle(stringResource(R.string.inbox_result_import_failed, failure), MaterialTheme.colorScheme.error)
                } else if (summary != null) {
                    ResultTitle(stringResource(R.string.inbox_result_title), MaterialTheme.colorScheme.onSurface)
                    ResultLines(summary, onOpenSettings)
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.padding(start = 8.dp)) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.inbox_result_dismiss))
            }
        }
    }
}

@Composable
private fun ResultTitle(text: String, color: Color) {
    Text(text, color = color, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun ResultLines(s: ImportSummary, onOpenSettings: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val toReview = (s.newCards - s.sent).coerceAtLeast(0)
    val lines = buildList {
        if (toReview > 0) add(pluralStringResource(R.plurals.inbox_result_to_review, toReview, toReview) to false)
        if (s.sent > 0) add(pluralStringResource(R.plurals.inbox_result_sent, s.sent, s.sent) to false)
        if (s.alreadyCard > 0) add(pluralStringResource(R.plurals.import_already_card, s.alreadyCard, s.alreadyCard) to false)
        if (s.skippedDiscarded > 0) add(pluralStringResource(R.plurals.import_skipped_discarded, s.skippedDiscarded, s.skippedDiscarded) to false)
        if (s.failed > 0) add(pluralStringResource(R.plurals.inbox_result_failed, s.failed, s.failed) to true)
        if (s.senseFailed > 0) add(pluralStringResource(R.plurals.import_sense_failed, s.senseFailed, s.senseFailed) to true)
        if (s.incompleteBackup != null) add(stringResource(R.string.inbox_result_incomplete) to true)
    }
    for ((text, error) in lines) ResultLine(text, if (error) colors.error else colors.onSurfaceVariant)
    val stopped = when (s.stopped) {
        null -> null
        LlmException.Stop.Kind.AUTH -> R.string.inbox_result_stopped_auth
        LlmException.Stop.Kind.CREDITS -> R.string.inbox_result_stopped_credits
        LlmException.Stop.Kind.CONFIG -> R.string.inbox_result_stopped_config
    }
    if (stopped != null) {
        ResultLine(stringResource(stopped), colors.error)
        TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.inbox_result_open_settings)) }
    }
    s.sendError?.let { ResultLine(stringResource(R.string.inbox_result_send_error, it), colors.error) }
    if (lines.isEmpty() && stopped == null && s.sendError == null) {
        ResultLine(stringResource(R.string.inbox_result_nothing), colors.onSurfaceVariant)
    }
}

@Composable
private fun ResultLine(text: String, color: Color) {
    Text(text, color = color, fontSize = 14.sp, lineHeight = 20.sp)
}

@Composable
internal fun AnkiBanner(availability: AnkiAvailability, onGet: () -> Unit, onAllow: () -> Unit, modifier: Modifier = Modifier) {
    val (text, action, onClick) = when (availability) {
        AnkiAvailability.NOT_INSTALLED -> Triple(R.string.inbox_banner_not_installed, R.string.inbox_banner_get, onGet)
        AnkiAvailability.NO_PERMISSION -> Triple(R.string.inbox_banner_no_permission, R.string.inbox_banner_allow, onAllow)
        AnkiAvailability.READY -> return
    }
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().background(colors.secondaryContainer, PanelShape).padding(start = 18.dp, top = 16.dp, end = 8.dp, bottom = 6.dp),
    ) {
        Row(Modifier.padding(end = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(R.drawable.ic_error), contentDescription = null, tint = colors.onSecondaryContainer)
            Text(stringResource(text), color = colors.onSecondaryContainer, fontSize = 14.sp, lineHeight = 20.sp)
        }
        TextButton(onClick = onClick, modifier = Modifier.align(Alignment.End)) { Text(stringResource(action)) }
    }
}

@Composable
internal fun EmptyInbox(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.padding(bottom = 10.dp).size(96.dp).rotate(-8f).background(colors.primaryContainer, RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_inbox), contentDescription = null, modifier = Modifier.size(44.dp).rotate(8f), tint = colors.onPrimaryContainer)
        }
        Text(stringResource(R.string.inbox_empty_title), fontSize = 22.sp, lineHeight = 28.sp, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.inbox_empty_text),
            modifier = Modifier.widthIn(max = 240.dp),
            color = colors.onSurfaceVariant,
            fontSize = 14.5.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center,
        )
    }
}

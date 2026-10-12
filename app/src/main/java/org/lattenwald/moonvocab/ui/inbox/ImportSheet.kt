package org.lattenwald.moonvocab.ui.inbox

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.lattenwald.moonvocab.R
import org.lattenwald.moonvocab.data.backup.FoundBackup
import org.lattenwald.moonvocab.domain.mark.ColorName
import org.lattenwald.moonvocab.domain.mark.ComboRow
import org.lattenwald.moonvocab.domain.mark.MarkStyle
import org.lattenwald.moonvocab.domain.mark.StyleCombo
import org.lattenwald.moonvocab.domain.mark.colorName
import org.lattenwald.moonvocab.ui.common.addWave
import org.lattenwald.moonvocab.ui.common.waveStroke
import org.lattenwald.moonvocab.ui.theme.BookFontFamily
import org.lattenwald.moonvocab.work.ImportState

private val SidePadding = 24.dp
private const val OPAQUE = 0xFF000000.toInt()
private const val STRIKE_HEIGHT = 0.3f // of the font size, above the baseline

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImportSheet(vm: ImportSheetViewModel, onDismiss: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    LaunchedEffect(vm) { vm.openIfIdle() }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(vm::chooseFolder) }
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::change) }
    val hide: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) onDismiss() }
    }
    val onChange = { pickBackup.launch(arrayOf("*/*")) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 30.dp)) {
            Text(
                stringResource(R.string.sheet_title),
                Modifier.padding(start = SidePadding, top = 6.dp, end = SidePadding, bottom = 14.dp),
                fontSize = 22.sp,
                lineHeight = 28.sp,
            )
            when (val s = state) {
                SheetState.Searching -> Searching()
                SheetState.NoFolder -> FolderPrompt(R.string.sheet_no_folder, onChoose = { pickFolder.launch(null) })
                SheetState.FolderUnreadable -> FolderPrompt(R.string.sheet_folder_unreadable, onChoose = { pickFolder.launch(null) })
                is SheetState.NoBackup -> {
                    Row(Modifier.padding(start = SidePadding, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (s.pickIncomplete) {
                            Text(stringResource(R.string.sheet_change_incomplete), Modifier.weight(1f), color = MaterialTheme.colorScheme.error, fontSize = 16.sp, lineHeight = 22.sp)
                        } else {
                            Text(stringResource(R.string.sheet_no_backup), Modifier.weight(1f), fontSize = 16.sp, lineHeight = 22.sp)
                        }
                        TextButton(onClick = onChange) { Text(stringResource(R.string.sheet_change)) }
                    }
                    BackupNotes(s.skipped, pickIncomplete = false)
                }
                is SheetState.Ready -> Ready(
                    s,
                    onChange = onChange,
                    onToggle = vm::toggle,
                    onSendToAnki = vm::setSendToAnki,
                    onStart = { vm.start(onStarted = hide) },
                )
                is SheetState.Error -> {
                    Note(stringResource(R.string.inbox_action_failed, s.message), MaterialTheme.colorScheme.error)
                    Row(Modifier.padding(start = SidePadding, top = 12.dp, end = SidePadding), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = vm::open) { Text(stringResource(R.string.sheet_retry)) }
                        FilledTonalButton(onClick = { pickFolder.launch(null) }) {
                            Icon(painterResource(R.drawable.ic_folder_open), contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.sheet_choose_folder), Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Searching() {
    Column(Modifier.padding(horizontal = SidePadding, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LinearWavyProgressIndicator(Modifier.fillMaxWidth())
        Text(stringResource(R.string.sheet_reading), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun FolderPrompt(text: Int, onChoose: () -> Unit) {
    Column(Modifier.padding(horizontal = SidePadding), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(text), fontSize = 16.sp, lineHeight = 22.sp)
        FilledTonalButton(onClick = onChoose) {
            Icon(painterResource(R.drawable.ic_folder_open), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.sheet_choose_folder), Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun Ready(
    s: SheetState.Ready,
    onChange: () -> Unit,
    onToggle: (StyleCombo) -> Unit,
    onSendToAnki: (Boolean) -> Unit,
    onStart: () -> Unit,
) {
    BackupRow(s.backup, onChange)
    BackupNotes(s.skipped, s.pickIncomplete)
    SubHeader(stringResource(R.string.sheet_marks))
    for (row in s.counts.rows) {
        MarkRow(row, checked = row.combo in s.selected, onToggle = { onToggle(row.combo) })
    }
    SubHeader(stringResource(R.string.sheet_after))
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = SidePadding, top = 6.dp, end = SidePadding)) {
        SegmentedButton(
            selected = !s.sendToAnki,
            onClick = { onSendToAnki(false) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            modifier = Modifier.weight(1f),
        ) { Text(stringResource(R.string.sheet_review_first)) }
        SegmentedButton(
            selected = s.sendToAnki,
            onClick = { onSendToAnki(true) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            modifier = Modifier.weight(1f),
        ) { Text(stringResource(R.string.sheet_send)) }
    }
    if (s.importRunning) {
        Spacer(Modifier.height(12.dp))
        Note(stringResource(R.string.sheet_import_running), MaterialTheme.colorScheme.error)
    }
    val n = s.counts.toImport
    Button(
        onClick = onStart,
        enabled = n > 0,
        modifier = Modifier.fillMaxWidth().padding(start = SidePadding, top = 22.dp, end = SidePadding).height(56.dp),
    ) {
        Text(
            if (n > 0) pluralStringResource(R.plurals.sheet_import_n, n, n) else stringResource(R.string.sheet_nothing_new),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun BackupRow(backup: FoundBackup, onChange: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val date = DateUtils.formatDateTime(LocalContext.current, backup.lastModified, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = SidePadding, top = 10.dp, end = 16.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(40.dp).background(colors.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_file_open), contentDescription = null, modifier = Modifier.size(20.dp), tint = colors.onSecondaryContainer)
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.sheet_backup_from, date), fontSize = 16.sp, lineHeight = 22.sp)
            Text(
                backup.name,
                color = colors.onSurfaceVariant,
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onChange) { Text(stringResource(R.string.sheet_change)) }
    }
}

@Composable
private fun BackupNotes(skipped: List<String>, pickIncomplete: Boolean) {
    for (name in skipped) Note(stringResource(R.string.sheet_skipped, name), MaterialTheme.colorScheme.onSurfaceVariant)
    if (pickIncomplete) Note(stringResource(R.string.sheet_change_incomplete), MaterialTheme.colorScheme.error)
}

@Composable
private fun Note(text: String, color: Color) {
    Text(text, Modifier.padding(start = SidePadding, top = 2.dp, end = SidePadding, bottom = 4.dp), color = color, fontSize = 14.sp, lineHeight = 20.sp)
}

@Composable
private fun SubHeader(text: String) {
    Text(
        text,
        Modifier.padding(start = SidePadding, top = 14.dp, end = SidePadding, bottom = 4.dp),
        color = MaterialTheme.colorScheme.primary,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun MarkRow(row: ComboRow, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .toggleable(checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .heightIn(min = 64.dp)
            .padding(start = SidePadding, top = 10.dp, end = 16.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        MarkSample(row.combo)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.sheet_combo, stringResource(styleName(row.combo.style)), stringResource(colorString(colorName(row.combo.color)))), fontSize = 16.sp, lineHeight = 22.sp)
            Text(
                if (row.new > 0) {
                    pluralStringResource(R.plurals.sheet_counts, row.total, row.total, row.new)
                } else {
                    pluralStringResource(R.plurals.sheet_counts_none_new, row.total, row.total)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
            )
        }
    }
}

@Composable
private fun MarkSample(combo: StyleCombo) {
    val color = Color(combo.color or OPAQUE)
    val highlight = combo.style == MarkStyle.HIGHLIGHT
    val fontSize = 16.sp
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.sheet_mark_sample),
            modifier = Modifier
                .then(if (highlight) Modifier.background(color, RoundedCornerShape(2.dp)) else Modifier)
                .padding(horizontal = 2.dp)
                .drawWithCache {
                    val baseline = layout?.firstBaseline
                    val width = size.width
                    val wave = if (baseline != null && combo.style == MarkStyle.WAVY) Path().apply { addWave(0f, width, baseline, this@drawWithCache) } else null
                    val waveStroke = waveStroke(this)
                    val lineY = when {
                        baseline == null -> null
                        combo.style == MarkStyle.UNDERLINE -> baseline + 3.dp.toPx()
                        combo.style == MarkStyle.STRIKETHROUGH -> baseline - fontSize.toPx() * STRIKE_HEIGHT
                        else -> null
                    }
                    val line = 2.dp.toPx()
                    onDrawBehind {
                        if (wave != null) drawPath(wave, color, style = waveStroke)
                        if (lineY != null) drawLine(color, Offset(0f, lineY), Offset(width, lineY), strokeWidth = line)
                    }
                },
            color = when {
                !highlight -> MaterialTheme.colorScheme.onSurface
                color.luminance() > 0.5f -> Color.Black.copy(alpha = 0.87f)
                else -> Color.White
            },
            fontFamily = BookFontFamily,
            fontSize = fontSize,
            lineHeight = 20.sp,
            maxLines = 1,
            onTextLayout = { layout = it },
        )
    }
}

private fun styleName(style: MarkStyle): Int = when (style) {
    MarkStyle.WAVY -> R.string.style_wavy
    MarkStyle.UNDERLINE -> R.string.style_underline
    MarkStyle.HIGHLIGHT -> R.string.style_highlight
    MarkStyle.STRIKETHROUGH -> R.string.style_strikethrough
}

private fun colorString(name: ColorName): Int = when (name) {
    ColorName.RED -> R.string.color_red
    ColorName.ORANGE -> R.string.color_orange
    ColorName.YELLOW -> R.string.color_yellow
    ColorName.GREEN -> R.string.color_green
    ColorName.CYAN -> R.string.color_cyan
    ColorName.BLUE -> R.string.color_blue
    ColorName.PURPLE -> R.string.color_purple
    ColorName.PINK -> R.string.color_pink
    ColorName.GRAY -> R.string.color_gray
    ColorName.BLACK -> R.string.color_black
    ColorName.WHITE -> R.string.color_white
}

@Composable
internal fun ImportFab(import: ImportState, expanded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val running = import.running
    val progress = import.progress?.takeIf { running && it.total > 0 }
    ExtendedFloatingActionButton( // FABs have no enabled state
        text = {
            Text(
                if (progress != null) stringResource(R.string.inbox_progress_count, progress.done, progress.total) else stringResource(R.string.inbox_import),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        },
        icon = {
            if (!running) {
                Icon(painterResource(R.drawable.ic_download), contentDescription = null)
            } else {
                val stroke = with(LocalDensity.current) { Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round) }
                if (progress == null) {
                    CircularWavyProgressIndicator(Modifier.size(24.dp), stroke = stroke, trackStroke = stroke)
                } else {
                    val fraction by animateFloatAsState(progress.done.toFloat() / progress.total, ProgressIndicatorDefaults.ProgressAnimationSpec)
                    CircularWavyProgressIndicator(progress = { fraction }, modifier = Modifier.size(24.dp), stroke = stroke, trackStroke = stroke)
                }
            }
        },
        onClick = { if (!running) onClick() },
        expanded = expanded,
        modifier = modifier.semantics { if (running) disabled() },
    )
}

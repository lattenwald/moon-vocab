package org.lattenwald.moonvocab.ui.debug

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import org.lattenwald.moonvocab.R
import org.lattenwald.moonvocab.ui.inbox.forInboxList
import org.lattenwald.moonvocab.ui.inbox.inboxColors
import org.lattenwald.moonvocab.ui.inbox.segmentedRowShapes
import org.lattenwald.moonvocab.ui.library.LibrarySubheader

private val RowPadding = PaddingValues(start = 18.dp, top = 14.dp, end = 16.dp, bottom = 14.dp)

@Composable
fun DebugScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: DebugViewModel = viewModel(factory = DebugViewModel.Factory),
) {
    val colors = inboxColors()
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        modifier = modifier,
        containerColor = colors.screen,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_developer)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.card_navigate_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.screen),
            )
        },
    ) { padding ->
        val themeShapes = MaterialTheme.shapes
        MaterialTheme(shapes = remember(themeShapes) { themeShapes.forInboxList() }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(bottom = 24.dp)) {
                DebugGroups(state, vm, colors.row)
            }
        }
    }
}

@Composable
private fun DebugGroups(state: DebugState, vm: DebugViewModel, rowColor: Color) {
    val exportCards = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::exportCards) }
    val exportPositions =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let(vm::exportPositionReport) }
    var confirm by remember { mutableStateOf<DebugAction?>(null) }

    Group(stringResource(R.string.debug_testing)) {
        TestDeckRow(state.testDeck, rowColor, onChange = vm::setTestDeck)
        ActionRow(1, 3, R.drawable.ic_delete, R.string.debug_delete_all, R.string.debug_delete_help, DebugAction.DELETE_NOTES, state, rowColor, danger = true) {
            confirm = DebugAction.DELETE_NOTES
        }
        ActionRow(2, 3, R.drawable.ic_close, R.string.debug_forget_unsent, R.string.debug_forget_help, DebugAction.FORGET_UNSENT, state, rowColor, danger = true) {
            confirm = DebugAction.FORGET_UNSENT
        }
    }
    Group(stringResource(R.string.debug_reports)) {
        ActionRow(0, 2, R.drawable.ic_download, R.string.debug_export, R.string.debug_export_help, DebugAction.EXPORT_CARDS, state, rowColor) {
            exportCards.launch(cardsExportName(LocalDate.now()))
        }
        ActionRow(1, 2, R.drawable.ic_menu_book, R.string.debug_position_report, R.string.debug_position_report_help, DebugAction.POSITION_REPORT, state, rowColor) {
            exportPositions.launch(positionReportName(LocalDate.now()))
        }
    }

    when (confirm) {
        DebugAction.DELETE_NOTES -> Confirm(R.string.debug_delete_title, R.string.debug_delete_text, R.string.debug_delete, onDismiss = { confirm = null }) {
            vm.deleteAllCreated()
        }
        DebugAction.FORGET_UNSENT -> Confirm(R.string.debug_forget_title, R.string.debug_forget_text, R.string.debug_forget, onDismiss = { confirm = null }) {
            vm.forgetUnsent()
        }
        else -> Unit
    }
}

@Composable
private fun Group(title: String, rows: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(bottom = 6.dp)) {
        LibrarySubheader(title)
        rows()
    }
}

private fun Modifier.groupRow(index: Int) = padding(start = 12.dp, top = if (index > 0) ListItemDefaults.SegmentedGap else 0.dp, end = 12.dp)

@Composable
private fun TestDeckRow(deck: String?, rowColor: Color, onChange: (Boolean) -> Unit) {
    val on = deck != null
    SegmentedListItem(
        checked = on,
        onCheckedChange = onChange,
        // A switch row, not a selection: on looks like off.
        shapes = segmentedRowShapes(0, 3).let { it.copy(selectedShape = it.shape) },
        modifier = Modifier.groupRow(0),
        colors = ListItemDefaults.segmentedColors(containerColor = rowColor, selectedContainerColor = rowColor),
        contentPadding = RowPadding,
        leadingContent = { IconBadge(R.drawable.ic_send, danger = false) },
        supportingContent = { HelpText(stringResource(R.string.debug_test_deck_help, deck ?: DebugViewModel.TEST_DECK)) },
        trailingContent = { Switch(checked = on, onCheckedChange = null) },
    ) { TitleText(stringResource(R.string.debug_test_deck)) }
}

@Composable
private fun ActionRow(
    index: Int,
    count: Int,
    icon: Int,
    title: Int,
    help: Int,
    action: DebugAction,
    state: DebugState,
    rowColor: Color,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        enabled = state.idle,
        shapes = segmentedRowShapes(index, count),
        modifier = Modifier.groupRow(index),
        colors = ListItemDefaults.segmentedColors(containerColor = rowColor),
        contentPadding = RowPadding,
        leadingContent = { IconBadge(icon, danger) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HelpText(stringResource(help))
                if (action == DebugAction.POSITION_REPORT) state.reportStep?.let { ReportProgress(it) }
                state.outcomes[action]?.let { OutcomeText(it) }
            }
        },
    ) { TitleText(stringResource(title), if (danger) MaterialTheme.colorScheme.error else Color.Unspecified) }
}

@Composable
private fun ReportProgress(step: ReportStep) {
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (step) {
            ReportStep.Finding -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                HelpText(stringResource(R.string.debug_report_finding))
            }
            is ReportStep.Writing -> {
                val p = step.progress
                LinearProgressIndicator(progress = { if (p.total == 0) 0f else p.done.toFloat() / p.total }, modifier = Modifier.fillMaxWidth())
                if (p.bookTitle == null) {
                    HelpText(stringResource(R.string.debug_report_backup))
                } else {
                    HelpText(pluralStringResource(R.plurals.debug_report_books, p.total, p.done + 1, p.total))
                    HelpText(p.bookTitle)
                }
            }
        }
        HelpText(stringResource(R.string.debug_report_keep_open))
    }
}

@Composable
private fun OutcomeText(outcome: Outcome) {
    val text = when (outcome) {
        is Outcome.Deleted -> pluralStringResource(R.plurals.debug_deleted, outcome.notes, outcome.notes)
        is Outcome.Forgotten -> pluralStringResource(R.plurals.debug_forgotten, outcome.cards, outcome.cards)
        is Outcome.Exported -> pluralStringResource(R.plurals.debug_exported, outcome.cards, outcome.cards)
        is Outcome.Reported -> outcome.summary.let { pluralStringResource(R.plurals.debug_position_reported, it.notes, it.exact, it.notes) }
        Outcome.NoBackupFolder -> stringResource(R.string.debug_report_no_folder)
        Outcome.NoBackup -> stringResource(R.string.debug_report_no_backup)
        is Outcome.Failed -> stringResource(R.string.debug_error, outcome.message)
    }
    val failed = outcome is Outcome.Failed || outcome is Outcome.NoBackupFolder || outcome is Outcome.NoBackup
    Text(
        text,
        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
    )
}

@Composable
private fun Confirm(title: Int, text: Int, confirm: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(text)) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) { Text(stringResource(confirm), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.debug_cancel)) } },
    )
}

@Composable
private fun IconBadge(icon: Int, danger: Boolean) {
    val colors = MaterialTheme.colorScheme
    val (background, tint) = if (danger) colors.errorContainer to colors.onErrorContainer else colors.secondaryContainer to colors.onSecondaryContainer
    Box(Modifier.size(40.dp).background(background, CircleShape), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = null, Modifier.size(20.dp), tint = tint)
    }
}

@Composable
private fun TitleText(text: String, color: Color = Color.Unspecified) = Text(text, color = color, fontSize = 16.sp, lineHeight = 22.sp)

@Composable
private fun HelpText(text: String) = Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.5.sp, lineHeight = 19.sp)

package org.lattenwald.moonvocab.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import org.lattenwald.moonvocab.R
import org.lattenwald.moonvocab.data.anki.AnkiAvailability
import org.lattenwald.moonvocab.data.settings.SettingsSnapshot
import org.lattenwald.moonvocab.data.settings.ThemeMode
import org.lattenwald.moonvocab.ui.CardsSettingsKey
import org.lattenwald.moonvocab.ui.DeveloperKey
import org.lattenwald.moonvocab.ui.LanguageKey
import org.lattenwald.moonvocab.ui.LanguageKind
import org.lattenwald.moonvocab.ui.ServicesKey
import org.lattenwald.moonvocab.ui.common.ankiFix
import org.lattenwald.moonvocab.ui.common.languageName
import org.lattenwald.moonvocab.ui.inbox.forInboxList
import org.lattenwald.moonvocab.ui.inbox.inboxColors
import org.lattenwald.moonvocab.ui.common.segmentedRowShapes
import org.lattenwald.moonvocab.ui.library.LibrarySubheader

private val RowPadding = PaddingValues(start = 18.dp, top = 14.dp, end = 16.dp, bottom = 14.dp)

private val THEME_LABELS = listOf(
    ThemeMode.SYSTEM to R.string.settings_theme_system,
    ThemeMode.LIGHT to R.string.settings_theme_light,
    ThemeMode.DARK to R.string.settings_theme_dark,
)

@Composable
fun SettingsScreen(
    onOpen: (NavKey) -> Unit,
    contentPadding: PaddingValues,
    vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    LifecycleResumeEffect(vm) {
        vm.refresh()
        onPauseOrDispose {}
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val colors = inboxColors()
    Scaffold(
        modifier = Modifier.padding(contentPadding).consumeWindowInsets(contentPadding).nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = colors.screen,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.screen),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val s = snapshot ?: return@Scaffold
        val themeShapes = MaterialTheme.shapes
        MaterialTheme(shapes = remember(themeShapes) { themeShapes.forInboxList() }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(bottom = 24.dp)) {
                SettingsGroups(s, vm, colors.row, onOpen)
            }
        }
    }
}

@Composable
private fun SettingsGroups(s: SettingsSnapshot, vm: SettingsViewModel, rowColor: Color, onOpen: (NavKey) -> Unit) {
    val readable by vm.readable.collectAsStateWithLifecycle()
    val anki by vm.ankiAvailability.collectAsStateWithLifecycle()
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(vm::setBackupTree) }
    val pickBooks = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(vm::setBooksTree) }

    Group(stringResource(R.string.settings_translation)) {
        LinkRow(0, 2, R.drawable.ic_translate, stringResource(R.string.settings_service), serviceTitle(s.activeService), rowColor) {
            onOpen(ServicesKey)
        }
        LinkRow(1, 2, R.drawable.ic_language, stringResource(R.string.settings_into), languageName(s.targetLanguage), rowColor) {
            onOpen(LanguageKey(LanguageKind.TARGET))
        }
    }
    Group(stringResource(R.string.settings_look)) {
        ThemeRow(s.theme, rowColor, onSelect = vm::setTheme)
        LinkRow(1, 2, R.drawable.ic_language, stringResource(R.string.settings_app_language), appLanguageTitle(appLanguage()), rowColor) {
            onOpen(LanguageKey(LanguageKind.APP))
        }
    }
    Group(stringResource(R.string.settings_moon)) {
        FolderRow(0, R.drawable.ic_folder, R.string.settings_backups, s.backupTree, readable, rowColor) { pickBackup.launch(null) }
        FolderRow(1, R.drawable.ic_menu_book, R.string.settings_books, s.booksTree, readable, rowColor) { pickBooks.launch(null) }
        ReviewRow(s.reviewFirst, rowColor, onChange = vm::setReviewFirst)
    }
    Group(stringResource(R.string.settings_cards)) {
        LinkRow(0, 2, R.drawable.ic_style, stringResource(R.string.settings_cards), stringResource(R.string.settings_cards_help), rowColor) {
            onOpen(CardsSettingsKey)
        }
        val ankiValue = when (anki) {
            null -> null
            AnkiAvailability.READY -> stringResource(R.string.settings_anki_ready)
            AnkiAvailability.NO_PERMISSION -> stringResource(R.string.settings_anki_allow)
            AnkiAvailability.NOT_INSTALLED -> stringResource(R.string.settings_anki_missing)
            AnkiAvailability.NOT_SET_UP -> stringResource(R.string.settings_anki_not_set_up)
        }
        LinkRow(1, 2, R.drawable.ic_school, stringResource(R.string.settings_anki), ankiValue, rowColor, onClick = ankiFix(anki)?.onClick)
    }
    Group(null) {
        LinkRow(0, 1, R.drawable.ic_code, stringResource(R.string.settings_developer), null, rowColor) { onOpen(DeveloperKey) }
    }
}

@Composable
private fun Group(title: String?, rows: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(top = if (title == null) 14.dp else 0.dp, bottom = 6.dp)) {
        if (title != null) LibrarySubheader(title)
        rows()
    }
}

private fun Modifier.settingsRow(index: Int) = padding(start = 12.dp, top = if (index > 0) ListItemDefaults.SegmentedGap else 0.dp, end = 12.dp)

@Composable
private fun LinkRow(
    index: Int,
    count: Int,
    icon: Int,
    title: String,
    value: String?,
    rowColor: Color,
    valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)?,
) {
    val supporting: (@Composable () -> Unit)? = value?.let { { ValueText(it, valueColor) } }
    if (onClick == null) {
        SegmentedListItem(
            shapes = segmentedRowShapes(index, count),
            modifier = Modifier.settingsRow(index),
            colors = ListItemDefaults.segmentedColors(containerColor = rowColor),
            contentPadding = RowPadding,
            leadingContent = { IconBadge(icon) },
            supportingContent = supporting,
        ) { TitleText(title) }
    } else {
        SegmentedListItem(
            onClick = onClick,
            shapes = segmentedRowShapes(index, count),
            modifier = Modifier.settingsRow(index),
            colors = ListItemDefaults.segmentedColors(containerColor = rowColor),
            contentPadding = RowPadding,
            leadingContent = { IconBadge(icon) },
            supportingContent = supporting,
            trailingContent = {
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
        ) { TitleText(title) }
    }
}

@Composable
private fun ThemeRow(theme: ThemeMode, rowColor: Color, onSelect: (ThemeMode) -> Unit) {
    SegmentedListItem(
        shapes = segmentedRowShapes(0, 2),
        modifier = Modifier.settingsRow(0),
        colors = ListItemDefaults.segmentedColors(containerColor = rowColor),
        contentPadding = RowPadding,
        leadingContent = { IconBadge(R.drawable.ic_palette) },
    ) {
        Column {
            TitleText(stringResource(R.string.settings_theme))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                THEME_LABELS.forEachIndexed { i, (mode, label) ->
                    SegmentedButton(
                        selected = theme == mode,
                        onClick = { if (theme != mode) onSelect(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = THEME_LABELS.size),
                        modifier = Modifier.weight(1f),
                        icon = {}, // a checkmark clips «Системная»
                    ) { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

@Composable
private fun FolderRow(index: Int, icon: Int, title: Int, tree: Uri?, readable: Set<Uri>?, rowColor: Color, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val unreadable = tree != null && readable != null && tree !in readable
    val value = when {
        tree == null -> stringResource(R.string.settings_folder_none)
        unreadable -> stringResource(R.string.settings_folder_unreadable)
        else -> folderName(tree)
    }
    LinkRow(index, 3, icon, stringResource(title), value, rowColor, if (unreadable) colors.error else colors.onSurfaceVariant, onClick)
}

// A tree's last segment is its document id, "volume:path".
internal fun folderName(tree: Uri): String {
    val id = tree.lastPathSegment ?: return tree.toString()
    return id.substringAfter(':').ifEmpty { id }
}

@Composable
private fun ReviewRow(on: Boolean, rowColor: Color, onChange: (Boolean) -> Unit) {
    SegmentedListItem(
        checked = on,
        onCheckedChange = onChange,
        // A switch row, not a selection: on looks like off.
        shapes = segmentedRowShapes(2, 3).let { it.copy(selectedShape = it.shape) },
        modifier = Modifier.settingsRow(2),
        colors = ListItemDefaults.segmentedColors(containerColor = rowColor, selectedContainerColor = rowColor),
        contentPadding = RowPadding,
        leadingContent = { IconBadge(R.drawable.ic_inbox) },
        supportingContent = { ValueText(stringResource(R.string.settings_review_help), MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = { Switch(checked = on, onCheckedChange = null) },
    ) { TitleText(stringResource(R.string.settings_review)) }
}

@Composable
private fun IconBadge(icon: Int) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.size(40.dp).background(colors.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = null, Modifier.size(20.dp), tint = colors.onSecondaryContainer)
    }
}

@Composable
private fun TitleText(text: String) = Text(text, fontSize = 16.sp, lineHeight = 22.sp)

@Composable
private fun ValueText(text: String, color: Color) = Text(text, color = color, fontSize = 13.5.sp, lineHeight = 19.sp)

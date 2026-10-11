package org.lattenwald.moonvocab.ui.debug

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ichi2.anki.api.AddContentApi
import java.time.LocalDate
import org.lattenwald.moonvocab.R
import org.lattenwald.moonvocab.data.anki.AnkiAvailability
import org.lattenwald.moonvocab.data.db.CardEntity
import org.lattenwald.moonvocab.data.llm.LlmException
import org.lattenwald.moonvocab.data.settings.LlmService
import org.lattenwald.moonvocab.data.settings.SettingsSnapshot
import org.lattenwald.moonvocab.domain.import.CheckResult
import org.lattenwald.moonvocab.domain.mark.StyleCombo

@Composable
fun DebugScreen(vm: DebugViewModel = viewModel(factory = DebugViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(vm) {
        vm.refreshAvailability()
        onPauseOrDispose {}
    }

    val pickBackupTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(vm::setBackupTree) }
    val pickBooksTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(vm::setBooksTree) }
    val grantAnki = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshAvailability() }
    val notifyThenImport = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.startImport() }
    val notifyThenRetry = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.retryFailed() }
    val exportCards = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::exportCards) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmForget by remember { mutableStateOf(false) }
    val idle = !state.busy && !state.importState.running

    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Folders(
                    state.settings,
                    onBackup = { pickBackupTree.launch(null) },
                    onBooks = { pickBooksTree.launch(null) },
                )
            }
            state.settings?.let { s ->
                item {
                    Services(
                        s,
                        onSelect = vm::selectService,
                        onEdit = vm::editService,
                        onAdd = vm::addService,
                    )
                }
            }
            item {
                AnkiRow(
                    state.availability,
                    enabled = idle,
                    onGrant = { grantAnki.launch(AddContentApi.READ_WRITE_PERMISSION) },
                    onCheck = vm::checkAnki,
                )
            }
            if (state.notice is Notice.Checked || state.notice is Notice.Error) item { NoticeText(state.notice) }
            item {
                BackupRow(
                    state.backup,
                    enabled = state.settings?.backupTree != null && !state.backup.searching && !state.importState.running,
                    onFind = vm::findNewestBackup,
                )
            }
            if (state.combos.isNotEmpty()) item { Styles(state, onToggle = vm::toggleStyle) }
            item { BookChoice(state, onSelect = vm::selectBook, onSend = vm::setSendToAnki) }
            item {
                ImportSection(
                    state.importState,
                    enabled = !state.importState.running && !state.backup.searching,
                    onImport = {
                        withNotifications(context, notifyThenImport, vm::startImport)
                    },
                )
            }
            item {
                OutlinedButton(
                    onClick = {
                        withNotifications(context, notifyThenRetry, vm::retryFailed)
                    },
                    enabled = !state.importState.running,
                ) { Text(stringResource(R.string.debug_retry_failed)) }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::sendAllPending, enabled = idle) { Text(stringResource(R.string.debug_send_pending)) }
                    OutlinedButton(onClick = { confirmDelete = true }, enabled = idle) { Text(stringResource(R.string.debug_delete_all)) }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.debug_test_deck), modifier = Modifier.weight(1f))
                    Switch(
                        checked = state.settings?.deckOverride != null,
                        onCheckedChange = vm::setTestDeck,
                        enabled = idle && state.settings != null,
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportCards.launch(cardsExportName(LocalDate.now())) }, enabled = idle) {
                        Text(stringResource(R.string.debug_export))
                    }
                    OutlinedButton(onClick = { confirmForget = true }, enabled = idle) { Text(stringResource(R.string.debug_forget_unsent)) }
                }
            }
            if (state.notice !is Notice.Checked && state.notice != null) item { NoticeText(state.notice) }
            item {
                Text(
                    pluralStringResource(R.plurals.debug_cards, state.cards.size, state.cards.size),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(state.cards, key = { it.id }) { CardRow(it) }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.debug_delete_title)) },
            text = { Text(stringResource(R.string.debug_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteAllCreated()
                }) { Text(stringResource(R.string.debug_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.debug_cancel)) } },
        )
    }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text(stringResource(R.string.debug_forget_title)) },
            text = { Text(stringResource(R.string.debug_forget_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmForget = false
                    vm.forgetUnsent()
                }) { Text(stringResource(R.string.debug_forget)) }
            },
            dismissButton = { TextButton(onClick = { confirmForget = false }) { Text(stringResource(R.string.debug_cancel)) } },
        )
    }
}

private fun withNotifications(context: Context, ask: ActivityResultLauncher<String>, start: () -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        ask.launch(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        start()
    }
}

@Composable
private fun SectionTitle(text: String) = Text(text, style = MaterialTheme.typography.titleMedium)

@Composable
private fun Folders(settings: SettingsSnapshot?, onBackup: () -> Unit, onBooks: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle(stringResource(R.string.debug_folders))
        FolderRow(R.string.debug_choose_backup_folder, settings?.backupTree?.lastPathSegment, onBackup)
        FolderRow(R.string.debug_choose_books_folder, settings?.booksTree?.lastPathSegment, onBooks)
    }
}

@Composable
private fun FolderRow(label: Int, path: String?, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onClick) { Text(stringResource(label)) }
        Text(path ?: stringResource(R.string.debug_folder_none))
    }
}

@Composable
private fun Services(
    settings: SettingsSnapshot,
    onSelect: (String) -> Unit,
    onEdit: (String, (LlmService) -> LlmService) -> Unit,
    onAdd: (String) -> Unit,
) {
    val active = settings.activeService
    val customName = stringResource(R.string.debug_service_custom)
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle(stringResource(R.string.debug_service))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                OutlinedButton(onClick = { expanded = true }) { Text(active.name) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    for (service in settings.services) {
                        DropdownMenuItem(text = { Text(service.name) }, onClick = {
                            expanded = false
                            onSelect(service.id)
                        })
                    }
                }
            }
            TextButton(onClick = { onAdd(customName) }) { Text(stringResource(R.string.debug_service_add)) }
        }
        ServiceField(active, R.string.debug_service_name, active.name) { v -> onEdit(active.id) { it.copy(name = v) } }
        ServiceField(active, R.string.debug_service_base_url, active.baseUrl, KeyboardType.Uri) { v -> onEdit(active.id) { it.copy(baseUrl = v) } }
        ServiceField(active, R.string.debug_service_api_key, active.apiKey, KeyboardType.Password) { v -> onEdit(active.id) { it.copy(apiKey = v) } }
        ServiceField(active, R.string.debug_service_model, active.model) { v -> onEdit(active.id) { it.copy(model = v) } }
    }
}

@Composable
private fun ServiceField(
    service: LlmService,
    label: Int,
    initial: String,
    keyboard: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit,
) {
    var value by remember(service.id) { mutableStateOf(initial) }
    val password = keyboard == KeyboardType.Password
    OutlinedTextField(
        value = value,
        onValueChange = {
            value = it
            onChange(it)
        },
        label = { Text(stringResource(label)) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AnkiRow(availability: AnkiAvailability?, enabled: Boolean, onGrant: () -> Unit, onCheck: () -> Unit) {
    val status = when (availability) {
        null -> R.string.debug_anki_checking
        AnkiAvailability.READY -> R.string.debug_anki_ready
        AnkiAvailability.NOT_INSTALLED -> R.string.debug_anki_not_installed
        AnkiAvailability.NO_PERMISSION -> R.string.debug_anki_no_permission
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle(stringResource(R.string.debug_anki, stringResource(status)))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onGrant, enabled = availability == AnkiAvailability.NO_PERMISSION) {
                Text(stringResource(R.string.debug_anki_grant))
            }
            Button(onClick = onCheck, enabled = enabled) { Text(stringResource(R.string.debug_anki_check)) }
        }
    }
}

@Composable
private fun NoticeText(notice: Notice?) {
    val text = when (notice) {
        null -> return
        is Notice.Checked -> when (val r = notice.result) {
            is CheckResult.Changed -> pluralStringResource(R.plurals.debug_anki_checked, r.count, r.count)
            is CheckResult.Unavailable -> stringResource(R.string.debug_send_unavailable, r.availability.name)
        }
        is Notice.Deleted -> pluralStringResource(R.plurals.debug_deleted, notice.notes, notice.notes)
        is Notice.Exported -> pluralStringResource(R.plurals.debug_exported, notice.cards, notice.cards)
        is Notice.Forgotten -> pluralStringResource(R.plurals.debug_forgotten, notice.cards, notice.cards)
        is Notice.Error -> stringResource(R.string.debug_error, notice.message)
        is Notice.Sent -> {
            val r = notice.result
            listOfNotNull(
                pluralStringResource(R.plurals.debug_sent, r.sent, r.sent),
                pluralStringResource(R.plurals.debug_skipped, r.skipped, r.skipped),
                r.unavailable?.let { stringResource(R.string.debug_send_unavailable, it.name) },
                r.error?.let { stringResource(R.string.debug_import_send_error, it) },
            ).joinToString("\n")
        }
    }
    Text(text, color = if (notice is Notice.Error) MaterialTheme.colorScheme.error else Color.Unspecified)
}

@Composable
private fun BackupRow(backup: BackupState, enabled: Boolean, onFind: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(onClick = onFind, enabled = enabled) { Text(stringResource(R.string.debug_backup_find)) }
        Text(
            when {
                backup.searching -> stringResource(R.string.debug_backup_searching)
                !backup.searched -> stringResource(R.string.debug_backup_not_searched)
                backup.name != null -> stringResource(R.string.debug_backup_newest, backup.name)
                else -> stringResource(R.string.debug_backup_none)
            },
        )
        for (name in backup.skipped) Text(stringResource(R.string.debug_backup_skipped, name), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun Styles(state: DebugState, onToggle: (StyleCombo) -> Unit) {
    val selected = state.settings?.styles.orEmpty()
    Column {
        SectionTitle(stringResource(R.string.debug_styles))
        for ((combo, count) in state.combos) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(checked = combo in selected, onCheckedChange = { onToggle(combo) })
                Box(Modifier.size(16.dp).background(Color(combo.color)))
                Text(
                    stringResource(
                        R.string.debug_style_combo,
                        combo.style.name,
                        combo.color.toUInt().toString(16).padStart(8, '0').uppercase(),
                        pluralStringResource(R.plurals.debug_marks, count, count),
                    ),
                )
            }
        }
    }
}

@Composable
private fun BookChoice(state: DebugState, onSelect: (String?) -> Unit, onSend: (Boolean) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val all = stringResource(R.string.debug_book_all)
    val chosen = state.book?.let { key -> state.books.firstOrNull { it.fileKey == key }?.title ?: key } ?: all
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box {
            OutlinedButton(onClick = { expanded = true }) { Text(chosen) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text(all) }, onClick = {
                    expanded = false
                    onSelect(null)
                })
                for (book in state.books) {
                    val marks = pluralStringResource(R.plurals.debug_marks, book.count, book.count)
                    DropdownMenuItem(text = { Text(stringResource(R.string.debug_book_option, book.title, marks)) }, onClick = {
                        expanded = false
                        onSelect(book.fileKey)
                    })
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.debug_send_after), modifier = Modifier.weight(1f))
            Switch(checked = state.sendToAnki, onCheckedChange = onSend)
        }
    }
}

@Composable
private fun ImportSection(run: ImportState, enabled: Boolean, onImport: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(onClick = onImport, enabled = enabled) { Text(stringResource(R.string.debug_import)) }
        if (run.running) {
            val p = run.progress
            if (p == null || p.total == 0) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.debug_import_waiting))
            } else {
                LinearProgressIndicator(progress = { p.done.toFloat() / p.total }, modifier = Modifier.fillMaxWidth())
                Text(pluralStringResource(R.plurals.import_progress, p.total, p.done, p.total))
                p.bookTitle?.let { Text(stringResource(R.string.debug_import_book, it)) }
            }
        }
        run.summary?.let { s ->
            SectionTitle(stringResource(R.string.debug_import_last))
            Text(
                stringResource(
                    R.string.debug_import_counts,
                    pluralStringResource(R.plurals.debug_import_new, s.newCards, s.newCards),
                    pluralStringResource(R.plurals.import_already_card, s.alreadyCard, s.alreadyCard),
                    pluralStringResource(R.plurals.import_skipped_discarded, s.skippedDiscarded, s.skippedDiscarded),
                    pluralStringResource(R.plurals.debug_import_failed, s.failed, s.failed),
                    pluralStringResource(R.plurals.debug_import_sent, s.sent, s.sent),
                ),
            )
            s.incompleteBackup?.let { Text(stringResource(R.string.debug_import_incomplete, it), color = MaterialTheme.colorScheme.error) }
            if (s.senseFailed > 0) {
                Text(pluralStringResource(R.plurals.import_sense_failed, s.senseFailed, s.senseFailed), color = MaterialTheme.colorScheme.error)
            }
            when (s.stopped) {
                null -> Unit
                LlmException.Stop.Kind.AUTH -> Text(stringResource(R.string.debug_import_stopped_auth), color = MaterialTheme.colorScheme.error)
                LlmException.Stop.Kind.CREDITS -> Text(stringResource(R.string.debug_import_stopped_credits), color = MaterialTheme.colorScheme.error)
                LlmException.Stop.Kind.CONFIG -> Text(stringResource(R.string.debug_import_stopped_config), color = MaterialTheme.colorScheme.error)
            }
            s.sendError?.let { Text(stringResource(R.string.debug_import_send_error, it), color = MaterialTheme.colorScheme.error) }
        }
        run.failure?.let { Text(stringResource(R.string.debug_import_failed_with, it), color = MaterialTheme.colorScheme.error) }
        if (run.cancelled) Text(stringResource(R.string.debug_import_cancelled))
    }
}

@Composable
private fun CardRow(card: CardEntity) {
    Column(Modifier.fillMaxWidth()) {
        Text(card.word, style = MaterialTheme.typography.titleSmall)
        if (card.translation.isNotEmpty()) Text(card.translation)
        Text(card.status.name, style = MaterialTheme.typography.labelMedium)
        card.elsewhereDeck?.let { Text(stringResource(R.string.debug_elsewhere, it)) }
        card.error?.let { Text(stringResource(R.string.debug_error, it), color = MaterialTheme.colorScheme.error) }
        HorizontalDivider(Modifier.padding(top = 4.dp))
    }
}

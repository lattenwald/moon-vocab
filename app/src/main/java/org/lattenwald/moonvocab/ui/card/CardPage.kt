package org.lattenwald.moonvocab.ui.card

import android.content.res.Resources
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collectLatest
import org.lattenwald.moonvocab.R
import org.lattenwald.moonvocab.data.anki.AnkiAvailability
import org.lattenwald.moonvocab.data.db.CardEntity
import org.lattenwald.moonvocab.data.llm.LlmException
import org.lattenwald.moonvocab.domain.card.CardStatus
import org.lattenwald.moonvocab.domain.card.FormResult
import org.lattenwald.moonvocab.domain.import.bookLine
import org.lattenwald.moonvocab.ui.inbox.inboxColors

private val PREVIEWABLE = setOf(CardStatus.PENDING, CardStatus.SENT)

@Composable
fun CardPage(
    cardId: Long,
    onBack: () -> Unit,
    onPreview: ((Long) -> Unit)?,
    vm: CardPageViewModel = viewModel(factory = CardPageViewModel.factory(cardId)),
) {
    val state = vm.state
    val card = state.card
    val resources = LocalResources.current
    val leave by rememberUpdatedState(onBack)
    val colors = inboxColors()
    val dirty = state.dirty

    val snackbar = remember { SnackbarHostState() }
    // Cancelling showSnackbar dismisses it, so a new event replaces the shown snackbar.
    LaunchedEffect(vm, resources) {
        vm.events.collectLatest { event ->
            val message = eventMessage(resources, event) ?: return@collectLatest
            val undoable = event == CardPageEvent.Sent || event == CardPageEvent.Discarded
            val result = snackbar.showSnackbar(
                message,
                actionLabel = if (undoable) resources.getString(R.string.inbox_undo) else null,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) vm.undo(event)
        }
    }
    LaunchedEffect(state.gone) { if (state.gone) leave() }
    BackHandler(enabled = dirty) { vm.back(leave) }

    val shown = if (state.editing) null else (state.parsed as? FormResult.Ok)?.fields ?: state.baseline
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = colors.screen,
        topBar = {
            TopAppBar(
                title = { Text((shown ?: state.baseline).word, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { vm.back(leave) }) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.card_navigate_back))
                    }
                },
                actions = { if (card != null) TopActions(card, state, onPreview, vm, onLeave = { leave() }) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.screen),
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = { if (card != null) BottomActions(card, state, vm, onLeave = { leave() }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (card == null) return@Scaffold
        val line = state.book?.let(::bookLine)
        Column(
            // Padding outside the scroll, so a focused field is brought into view above the keyboard.
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Subheader(stringResource(R.string.card_front))
            if (shown == null) FrontForm(state.form, state.invalid, colors.row, vm::edit) else FrontFace(shown, colors.row)
            Subheader(stringResource(R.string.card_back))
            if (shown == null) BackForm(state.form, state.invalid, line, colors.row, vm::edit) else BackFace(shown, line, colors.row)
            CardLabels(card, widened = state.extras?.widened ?: card.widened)
            Notes(card, state.source)
        }
    }

    when (state.dialog) {
        CardDialog.None -> Unit
        CardDialog.ConfirmDelete -> AlertDialog(
            onDismissRequest = vm::dismissDialog,
            title = { Text(stringResource(R.string.card_delete_title)) },
            text = { Text(stringResource(R.string.card_delete_text)) },
            confirmButton = { TextButton(onClick = vm::deleteFromAnki) { Text(stringResource(R.string.card_delete_confirm)) } },
            dismissButton = { TextButton(onClick = vm::dismissDialog) { Text(stringResource(R.string.card_cancel)) } },
        )
        CardDialog.ConfirmLeave -> AlertDialog(
            onDismissRequest = vm::dismissDialog,
            title = { Text(stringResource(R.string.card_leave_title)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.dismissDialog()
                    leave()
                }) { Text(stringResource(R.string.card_leave_discard)) }
            },
            dismissButton = { TextButton(onClick = vm::dismissDialog) { Text(stringResource(R.string.card_leave_keep)) } },
        )
    }
}

@Composable
private fun TopActions(card: CardEntity, state: CardPageState, onPreview: ((Long) -> Unit)?, vm: CardPageViewModel, onLeave: () -> Unit) {
    val status = card.status
    if (onPreview != null && status in PREVIEWABLE) {
        IconButton(onClick = { onPreview(card.id) }, enabled = !state.dirty) {
            Icon(painterResource(R.drawable.ic_visibility), contentDescription = stringResource(R.string.card_preview))
        }
    }
    if (status in CardPageViewModel.EDITABLE) {
        IconButton(onClick = if (state.editing) vm::done else vm::startEditing, enabled = !state.busy) {
            Icon(
                painterResource(if (state.editing) R.drawable.ic_check else R.drawable.ic_edit),
                contentDescription = stringResource(if (state.editing) R.string.card_done else R.string.card_edit),
            )
        }
    }
    if (status !in CardPageViewModel.EDITABLE) return
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, enabled = !state.busy) {
            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.card_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.card_translate_again)) },
                onClick = {
                    open = false
                    vm.translateAgain(onLeave)
                },
                leadingIcon = { Icon(painterResource(R.drawable.ic_translate), contentDescription = null) },
            )
            if (status == CardStatus.SENT) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.card_delete)) },
                    onClick = {
                        open = false
                        vm.askDelete()
                    },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
                )
            }
        }
    }
}

@Composable
private fun BottomActions(card: CardEntity, state: CardPageState, vm: CardPageViewModel, onLeave: () -> Unit) {
    val idle = !state.busy
    val valid = state.parsed is FormResult.Ok
    Row(
        Modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (card.status) {
            CardStatus.PENDING -> {
                Tonal(R.drawable.ic_delete, R.string.card_discard, enabled = idle, onClick = vm::discard)
                val sendable = state.form.translation.isNotBlank() && valid
                Filled(R.drawable.ic_send, R.string.card_send, enabled = idle && sendable, onClick = vm::send)
            }
            CardStatus.FAILED -> {
                Tonal(R.drawable.ic_delete, R.string.card_discard, enabled = idle, onClick = vm::discard)
                Filled(R.drawable.ic_translate, R.string.card_translate_again, enabled = idle && !state.importRunning, onClick = { vm.translateAgain(onLeave) })
            }
            CardStatus.SENT -> Filled(
                R.drawable.ic_check,
                R.string.card_save,
                enabled = idle && state.dirty && valid && state.source !is CardSource.Local,
                onClick = vm::save,
            )
            CardStatus.DISCARDED -> Filled(R.drawable.ic_restore_from_trash, R.string.card_restore, enabled = idle, onClick = vm::restore)
            CardStatus.DELETED_IN_ANKI -> Filled(R.drawable.ic_send, R.string.card_send_as_new, enabled = idle, onClick = vm::sendAsNew)
        }
    }
}

@Composable
private fun RowScope.Filled(icon: Int, text: Int, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.weight(1f).height(56.dp), enabled = enabled) { ButtonContent(icon, text) }
}

@Composable
private fun RowScope.Tonal(icon: Int, text: Int, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.weight(1f).height(56.dp), enabled = enabled) { ButtonContent(icon, text) }
}

@Composable
private fun ButtonContent(icon: Int, text: Int) {
    Icon(painterResource(icon), contentDescription = null)
    Text(stringResource(text), Modifier.padding(start = 8.dp), fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun Subheader(text: String) {
    Text(
        text,
        Modifier.padding(start = 8.dp, top = 10.dp, end = 8.dp, bottom = 2.dp),
        color = MaterialTheme.colorScheme.primary,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun Notes(card: CardEntity, source: CardSource) {
    val colors = MaterialTheme.colorScheme
    val local = source is CardSource.Local && source.unavailable != null
    val (text, color) = when (card.status) {
        CardStatus.SENT -> stringResource(if (local) R.string.card_local_note else R.string.card_sent_note) to colors.onSurfaceVariant
        CardStatus.DELETED_IN_ANKI -> stringResource(R.string.card_deleted_note) to colors.onSurfaceVariant
        CardStatus.DISCARDED -> stringResource(R.string.card_discarded_note) to colors.onSurfaceVariant
        CardStatus.FAILED ->
            (card.error?.let { stringResource(R.string.inbox_action_failed, it) } ?: stringResource(R.string.inbox_label_failed)) to colors.error
        CardStatus.PENDING -> return
    }
    Text(text, Modifier.padding(start = 8.dp, top = 2.dp, end = 8.dp), color = color, fontSize = 12.5.sp, lineHeight = 17.sp)
}

private fun eventMessage(r: Resources, event: CardPageEvent): String? = when (event) {
    CardPageEvent.Sent -> r.getString(R.string.card_snack_sent)
    CardPageEvent.Discarded -> r.getString(R.string.card_snack_discarded)
    CardPageEvent.Saved -> r.getString(R.string.card_snack_saved)
    is CardPageEvent.Conflict -> r.getString(R.string.card_snack_conflict, event.fields.sorted().joinToString(", ") { r.getString(it.label) })
    CardPageEvent.NoteGone -> r.getString(R.string.card_snack_note_gone)
    CardPageEvent.NotPending -> r.getString(R.string.card_snack_not_pending)
    CardPageEvent.Deleted -> r.getString(R.string.card_snack_deleted)
    CardPageEvent.DeleteRefused -> r.getString(R.string.card_snack_delete_refused)
    CardPageEvent.Retranslated -> r.getString(R.string.card_snack_retranslated)
    is CardPageEvent.Stopped -> r.getString(
        when (event.kind) {
            LlmException.Stop.Kind.AUTH -> R.string.inbox_result_stopped_auth
            LlmException.Stop.Kind.CREDITS -> R.string.inbox_result_stopped_credits
            LlmException.Stop.Kind.CONFIG -> R.string.inbox_result_stopped_config
        },
    )
    is CardPageEvent.Unavailable -> when (event.availability) {
        AnkiAvailability.NOT_INSTALLED -> r.getString(R.string.inbox_banner_not_installed)
        AnkiAvailability.NO_PERMISSION -> r.getString(R.string.inbox_banner_no_permission)
        AnkiAvailability.NOT_SET_UP -> r.getString(R.string.inbox_banner_not_set_up)
        AnkiAvailability.READY -> null
    }
    is CardPageEvent.SendFailed -> r.getString(R.string.inbox_send_failed, event.message)
    is CardPageEvent.MissingFields -> r.getString(R.string.inbox_note_type_missing, event.fields.joinToString(", "))
    is CardPageEvent.UndoKept -> r.getQuantityString(R.plurals.inbox_undo_kept, event.count, event.count)
    is CardPageEvent.Failed -> r.getString(R.string.inbox_action_failed, event.message)
}

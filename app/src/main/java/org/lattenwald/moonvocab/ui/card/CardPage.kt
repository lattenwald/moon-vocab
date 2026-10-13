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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
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
import org.lattenwald.moonvocab.domain.card.CardField
import org.lattenwald.moonvocab.domain.card.CardStatus
import org.lattenwald.moonvocab.domain.cardpage.CardDialog
import org.lattenwald.moonvocab.domain.cardpage.CardEditState
import org.lattenwald.moonvocab.domain.cardpage.CardIntent
import org.lattenwald.moonvocab.domain.cardpage.CardMessage
import org.lattenwald.moonvocab.domain.cardpage.CardSource
import org.lattenwald.moonvocab.domain.cardpage.EDITABLE
import org.lattenwald.moonvocab.domain.cardpage.undoable
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
    val dismiss = { vm.dispatch(CardIntent.DismissDialog) }

    val snackbar = remember { SnackbarHostState() }
    // Cancelling showSnackbar dismisses it, so a new message replaces the shown snackbar.
    LaunchedEffect(vm, resources) {
        vm.messages.collectLatest { message ->
            val text = messageText(resources, message) ?: return@collectLatest
            val result = snackbar.showSnackbar(
                text,
                actionLabel = if (message.undoable) resources.getString(R.string.inbox_undo) else null,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) vm.dispatch(CardIntent.Undo(message))
        }
    }
    LaunchedEffect(state.closed) { if (state.closed) leave() }
    BackHandler(enabled = state.dirty) { vm.dispatch(CardIntent.Back) }

    val shown = if (state.editing) null else (state.parsed as? FormResult.Ok)?.fields ?: state.baseline
    val edit = { field: CardField, value: String -> vm.dispatch(CardIntent.Edit(field, value)) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = colors.screen,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text((shown ?: state.baseline).word, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = { vm.dispatch(CardIntent.Back) }) {
                            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.card_navigate_back))
                        }
                    },
                    actions = { if (card != null) TopActions(card, state, onPreview, vm::dispatch) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.screen),
                    scrollBehavior = scrollBehavior,
                )
                Busy(state.busy)
            }
        },
        bottomBar = { if (card != null) BottomActions(card, state, vm::dispatch) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (card == null) return@Scaffold
        val line = vm.book?.let(::bookLine)
        Column(
            // Padding outside the scroll, so a focused field is brought into view above the keyboard.
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Subheader(stringResource(R.string.card_front))
            if (shown == null) FrontForm(state.form, state.invalid, colors.row, readOnly = state.busy, edit) else FrontFace(shown, colors.row)
            Subheader(stringResource(R.string.card_back))
            if (shown == null) BackForm(state.form, state.invalid, line, colors.row, readOnly = state.busy, edit) else BackFace(shown, line, colors.row)
            CardLabels(card, widened = state.extras?.widened ?: card.widened)
            Notes(card, state.source)
        }
    }

    when (state.dialog) {
        CardDialog.None -> Unit
        CardDialog.ConfirmDelete -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.card_delete_title)) },
            text = { Text(stringResource(R.string.card_delete_text)) },
            confirmButton = {
                TextButton(onClick = { vm.dispatch(CardIntent.ConfirmDelete) }) { Text(stringResource(R.string.card_delete_confirm)) }
            },
            dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.card_cancel)) } },
        )
        CardDialog.ConfirmLeave -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.card_leave_title)) },
            confirmButton = {
                TextButton(onClick = { vm.dispatch(CardIntent.ConfirmLeave) }) { Text(stringResource(R.string.card_leave_discard)) }
            },
            dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.card_leave_keep)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Busy(busy: Boolean) {
    Box(Modifier.fillMaxWidth().height(4.dp)) {
        if (busy) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
private fun TopActions(card: CardEntity, state: CardEditState, onPreview: ((Long) -> Unit)?, dispatch: (CardIntent) -> Unit) {
    val status = card.status
    if (onPreview != null && status in PREVIEWABLE) {
        IconButton(onClick = { onPreview(card.id) }, enabled = !state.dirty) {
            Icon(painterResource(R.drawable.ic_visibility), contentDescription = stringResource(R.string.card_preview))
        }
    }
    if (status in EDITABLE) {
        IconButton(onClick = { dispatch(if (state.editing) CardIntent.Done else CardIntent.StartEditing) }, enabled = !state.busy) {
            Icon(
                painterResource(if (state.editing) R.drawable.ic_check else R.drawable.ic_edit),
                contentDescription = stringResource(if (state.editing) R.string.card_done else R.string.card_edit),
            )
        }
    }
    if (status !in EDITABLE) return
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
                    dispatch(CardIntent.TranslateAgain)
                },
                leadingIcon = { Icon(painterResource(R.drawable.ic_translate), contentDescription = null) },
            )
            if (status == CardStatus.SENT) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.card_delete)) },
                    onClick = {
                        open = false
                        dispatch(CardIntent.AskDelete)
                    },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
                )
            }
        }
    }
}

@Composable
private fun BottomActions(card: CardEntity, state: CardEditState, dispatch: (CardIntent) -> Unit) {
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
                Tonal(R.drawable.ic_delete, R.string.card_discard, enabled = idle) { dispatch(CardIntent.Discard) }
                val sendable = state.form.translation.isNotBlank() && valid
                Filled(R.drawable.ic_send, R.string.card_send, enabled = idle && sendable) { dispatch(CardIntent.Send) }
            }
            CardStatus.FAILED -> {
                Tonal(R.drawable.ic_delete, R.string.card_discard, enabled = idle) { dispatch(CardIntent.Discard) }
                Filled(R.drawable.ic_translate, R.string.card_translate_again, enabled = idle && !state.importRunning) { dispatch(CardIntent.TranslateAgain) }
            }
            CardStatus.SENT -> Filled(R.drawable.ic_check, R.string.card_save, enabled = idle && state.dirty && valid && state.source == CardSource.Fresh) {
                dispatch(CardIntent.Save)
            }
            CardStatus.DISCARDED -> Filled(R.drawable.ic_restore_from_trash, R.string.card_restore, enabled = idle) { dispatch(CardIntent.Restore) }
            CardStatus.DELETED_IN_ANKI -> Filled(R.drawable.ic_send, R.string.card_send_as_new, enabled = idle) { dispatch(CardIntent.SendAsNew) }
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
    val local = source is CardSource.Unavailable || source is CardSource.ReadFailed
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

private fun messageText(r: Resources, event: CardMessage): String? = when (event) {
    CardMessage.Sent -> r.getString(R.string.card_snack_sent)
    CardMessage.Discarded -> r.getString(R.string.card_snack_discarded)
    CardMessage.Saved -> r.getString(R.string.card_snack_saved)
    is CardMessage.Conflict -> r.getString(R.string.card_snack_conflict, event.fields.sorted().joinToString(", ") { r.getString(it.label) })
    CardMessage.NoteGone -> r.getString(R.string.card_snack_note_gone)
    CardMessage.NotPending -> r.getString(R.string.card_snack_not_pending)
    CardMessage.Deleted -> r.getString(R.string.card_snack_deleted)
    CardMessage.DeleteRefused -> r.getString(R.string.card_snack_delete_refused)
    CardMessage.Retranslated -> r.getString(R.string.card_snack_retranslated)
    is CardMessage.Stopped -> r.getString(
        when (event.kind) {
            LlmException.Stop.Kind.AUTH -> R.string.inbox_result_stopped_auth
            LlmException.Stop.Kind.CREDITS -> R.string.inbox_result_stopped_credits
            LlmException.Stop.Kind.CONFIG -> R.string.inbox_result_stopped_config
        },
    )
    is CardMessage.Unavailable -> when (event.availability) {
        AnkiAvailability.NOT_INSTALLED -> r.getString(R.string.inbox_banner_not_installed)
        AnkiAvailability.NO_PERMISSION -> r.getString(R.string.inbox_banner_no_permission)
        AnkiAvailability.NOT_SET_UP -> r.getString(R.string.inbox_banner_not_set_up)
        AnkiAvailability.READY -> null
    }
    is CardMessage.SendFailed -> r.getString(R.string.inbox_send_failed, event.message)
    is CardMessage.MissingFields -> r.getString(R.string.inbox_note_type_missing, event.fields.joinToString(", "))
    is CardMessage.UndoKept -> r.getQuantityString(R.plurals.inbox_undo_kept, event.count, event.count)
    is CardMessage.Failed -> r.getString(R.string.inbox_action_failed, event.message)
}

package org.lattenwald.moonvocab.ui.inbox

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.lattenwald.moonvocab.R
import org.lattenwald.moonvocab.data.anki.AnkiAvailability
import org.lattenwald.moonvocab.ui.common.rememberAnkiPermissionRequest

private const val TAG = "InboxScreen"
private const val ANKIDROID_MARKET = "market://details?id=com.ichi2.anki"
private const val ANKIDROID_WEB = "https://play.google.com/store/apps/details?id=com.ichi2.anki"
private const val DISABLED_ALPHA = 0.45f

@Composable
fun InboxScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    vm: InboxViewModel = viewModel(factory = InboxViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    LifecycleResumeEffect(vm) {
        vm.refreshAvailability()
        onPauseOrDispose {}
    }
    val requestAnki = rememberAnkiPermissionRequest(onResult = vm::refreshAvailability)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val colors = inboxColors()
    val list = state.list
    val import = state.import
    val selection = state.selection
    val selecting = selection.active
    val selectedIds = list.visibleIds.filter { it in selection.ids }
    val allSelected = list.visibleIds.isNotEmpty() && selectedIds.size == list.visibleIds.size

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm, resources) {
        var shown: Job? = null
        vm.events.collect { event ->
            val message = eventMessage(resources, event) ?: return@collect
            val undoable = event is InboxEvent.Sent || event is InboxEvent.Discarded
            shown?.cancel()
            shown = launch {
                val result = snackbar.showSnackbar(
                    message,
                    actionLabel = if (undoable) resources.getString(R.string.inbox_undo) else null,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) vm.undo(event)
            }
        }
    }
    BackHandler(enabled = selecting, onBack = vm::clearSelection)

    val listState = rememberLazyListState()
    val drag = rememberDragSelect(listState, onStart = vm::startDrag, onOver = vm::dragOver, onEnd = vm::endDrag)

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = colors.screen,
        topBar = {
            val barColor = if (selecting) MaterialTheme.colorScheme.surfaceContainerHigh else colors.screen
            LargeFlexibleTopAppBar(
                title = {
                    Text(if (selecting) pluralStringResource(R.plurals.inbox_selected, selection.ids.size, selection.ids.size) else stringResource(R.string.inbox_title))
                },
                subtitle = { Text(pluralStringResource(R.plurals.inbox_to_review, list.total, list.total)) },
                navigationIcon = {
                    if (selecting) {
                        IconButton(onClick = vm::clearSelection) {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.inbox_stop_selecting))
                        }
                    }
                },
                actions = {
                    if (selecting) {
                        IconToggleButton(checked = allSelected, onCheckedChange = { vm.toggleAll() }) {
                            Icon(
                                painterResource(R.drawable.ic_select_all),
                                contentDescription = stringResource(if (allSelected) R.string.inbox_clear_selection else R.string.inbox_select_all),
                            )
                        }
                    } else if (list.visibleIds.isNotEmpty()) {
                        IconButton(onClick = vm::toggleAll) {
                            Icon(painterResource(R.drawable.ic_select_all), contentDescription = stringResource(R.string.inbox_select_all))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = barColor, scrolledContainerColor = if (selecting) barColor else Color.Unspecified),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            AnimatedVisibility(
                visible = selecting,
                enter = slideInVertically { it * 2 } + fadeIn(),
                exit = slideOutVertically { it * 2 } + fadeOut(),
            ) {
                SelectionToolbar(
                    count = selectedIds.size,
                    canTranslate = !import.running,
                    onDiscard = { vm.discard(selectedIds) },
                    onTranslateAgain = { vm.translateAgain(selectedIds) },
                    onSend = { vm.send(selectedIds) },
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
    ) { padding ->
        val themeShapes = MaterialTheme.shapes
        MaterialTheme(shapes = remember(themeShapes) { themeShapes.forInboxList() }) {
            InboxLongPress {
                LazyColumn(Modifier.fillMaxSize().dragSelect(drag), state = listState, contentPadding = padding) {
                    val anki = state.anki
                    if (anki != null && anki != AnkiAvailability.READY) {
                        item(key = "banner") {
                            AnkiBanner(
                                anki,
                                onGet = { openAnkiDroidStore(context) },
                                onAllow = requestAnki,
                                modifier = Modifier.padding(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 10.dp),
                            )
                        }
                    }
                    if (state.missingFields.isNotEmpty()) {
                        item(key = "fields") {
                            NoteTypeBanner(state.missingFields, Modifier.padding(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 10.dp))
                        }
                    }
                    if (import.running) {
                        item(key = "import", contentType = "progress") {
                            ImportProgressPanel(import.progress, Modifier.padding(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 10.dp))
                        }
                    } else if (import.summary != null || import.failure != null) {
                        item(key = "import", contentType = "result") {
                            ImportResultCard(
                                import,
                                onDismiss = vm::dismissResult,
                                onOpenSettings = onOpenSettings,
                                modifier = Modifier.padding(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 10.dp),
                            )
                        }
                    }
                    if (list.chips.isNotEmpty()) {
                        item(key = "chips") { LanguageChips(list, onFilter = vm::setFilter, modifier = Modifier.padding(bottom = 6.dp)) }
                    }
                    for (group in list.groups) {
                        item(key = "h${group.bookId}", contentType = "header") {
                            val inGroup = group.cards.count { it.id in selection.ids }
                            val checked = when (inGroup) {
                                0 -> ToggleableState.Off
                                group.cards.size -> ToggleableState.On
                                else -> ToggleableState.Indeterminate
                            }
                            GroupHeader(
                                group,
                                selecting = selecting,
                                checked = checked,
                                onToggle = { vm.toggleGroup(group.bookId) },
                                modifier = Modifier.animateItem().padding(start = 12.dp, top = 14.dp, end = 12.dp),
                            )
                        }
                        itemsIndexed(group.cards, key = { _, card -> card.id }, contentType = { _, _ -> "card" }) { index, card ->
                            SwipeableCardRow(
                                card,
                                index,
                                group.cards.size,
                                colors.row,
                                selecting = selecting,
                                selected = card.id in selection.ids,
                                onToggle = { vm.toggle(card.id) },
                                onSend = { onStayed -> vm.send(listOf(card.id), onStayed) },
                                onDiscard = { onStayed -> vm.discard(listOf(card.id), onStayed) },
                                modifier = Modifier.animateItem().padding(
                                    start = 12.dp,
                                    top = if (index > 0) ListItemDefaults.SegmentedGap else 0.dp,
                                    end = 12.dp,
                                    bottom = if (index == group.cards.lastIndex) 4.dp else 0.dp,
                                ),
                            )
                        }
                    }
                    if (state.loaded && list.total == 0 && !import.running) {
                        item(key = "empty") { EmptyInbox() }
                    }
                    item(key = "bottom") { Spacer(Modifier.height(96.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SelectionToolbar(count: Int, canTranslate: Boolean, onDiscard: () -> Unit, onTranslateAgain: () -> Unit, onSend: () -> Unit) {
    val any = count > 0
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        HorizontalFloatingToolbar(expanded = true, colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors()) {
            IconButton(onClick = onDiscard, enabled = any) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.inbox_discard))
            }
            IconButton(onClick = onTranslateAgain, enabled = any && canTranslate) {
                Icon(painterResource(R.drawable.ic_refresh), contentDescription = stringResource(R.string.inbox_translate_again))
            }
        }
        // Beside the toolbar: its floatingActionButton slot measures a square FAB. FABs have no enabled state.
        ExtendedFloatingActionButton(
            onClick = { if (any) onSend() },
            modifier = Modifier.height(64.dp).alpha(if (any) 1f else DISABLED_ALPHA).semantics { if (!any) disabled() },
            shape = RoundedCornerShape(20.dp),
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
        ) {
            Icon(painterResource(R.drawable.ic_send), contentDescription = null)
            Text(stringResource(R.string.inbox_send_n, count), Modifier.padding(start = 10.dp), fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

private fun eventMessage(r: Resources, event: InboxEvent): String? = when (event) {
    is InboxEvent.Sent -> {
        val sent = r.getQuantityString(R.plurals.inbox_sent, event.ids.size, event.ids.size)
        if (event.stayed > 0) r.getString(R.string.inbox_sent_and_stayed, sent, r.getQuantityString(R.plurals.inbox_stayed, event.stayed, event.stayed)) else sent
    }
    is InboxEvent.Stayed -> r.getQuantityString(R.plurals.inbox_stayed, event.count, event.count)
    is InboxEvent.Discarded -> r.getQuantityString(R.plurals.inbox_discarded, event.ids.size, event.ids.size)
    is InboxEvent.Unavailable -> when (event.availability) {
        AnkiAvailability.NOT_INSTALLED -> r.getString(R.string.inbox_banner_not_installed)
        AnkiAvailability.NO_PERMISSION -> r.getString(R.string.inbox_banner_no_permission)
        AnkiAvailability.READY -> null
    }
    is InboxEvent.SendFailed -> r.getString(R.string.inbox_send_failed, event.message)
    is InboxEvent.UndoKept -> r.getQuantityString(R.plurals.inbox_undo_kept, event.count, event.count)
    is InboxEvent.Retranslating -> r.getQuantityString(R.plurals.inbox_retranslating, event.count, event.count)
}

private fun openAnkiDroidStore(context: Context) {
    for (uri in listOf(ANKIDROID_MARKET, ANKIDROID_WEB)) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()))
            return
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "no app opens $uri", e)
        }
    }
}

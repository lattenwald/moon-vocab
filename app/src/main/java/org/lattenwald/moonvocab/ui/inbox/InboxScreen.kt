package org.lattenwald.moonvocab.ui.inbox

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.lattenwald.moonvocab.R
import org.lattenwald.moonvocab.data.anki.AnkiAvailability
import org.lattenwald.moonvocab.ui.common.rememberAnkiPermissionRequest

private const val TAG = "InboxScreen"
private const val ANKIDROID_MARKET = "market://details?id=com.ichi2.anki"
private const val ANKIDROID_WEB = "https://play.google.com/store/apps/details?id=com.ichi2.anki"

@Composable
fun InboxScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    vm: InboxViewModel = viewModel(factory = InboxViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(vm) {
        vm.refreshAvailability()
        onPauseOrDispose {}
    }
    val requestAnki = rememberAnkiPermissionRequest(onResult = vm::refreshAvailability)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val colors = inboxColors()
    val list = state.list
    val import = state.import

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = colors.screen,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.inbox_title)) },
                subtitle = { Text(pluralStringResource(R.plurals.inbox_to_review, list.total, list.total)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.screen),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val themeShapes = MaterialTheme.shapes
        MaterialTheme(shapes = remember(themeShapes) { themeShapes.forInboxList() }) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
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
                        GroupHeader(group, Modifier.padding(start = 12.dp, top = 14.dp, end = 12.dp))
                    }
                    itemsIndexed(group.cards, key = { _, card -> card.id }, contentType = { _, _ -> "card" }) { index, card ->
                        InboxCardRow(
                            card,
                            index,
                            group.cards.size,
                            colors.row,
                            Modifier.padding(
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

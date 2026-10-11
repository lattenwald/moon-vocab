package org.lattenwald.moonvocab.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.lattenwald.moonvocab.R
import org.lattenwald.moonvocab.ui.debug.DebugScreen
import org.lattenwald.moonvocab.ui.inbox.InboxScreen
import org.lattenwald.moonvocab.ui.inbox.InboxViewModel

private enum class Tab { INBOX, SETTINGS }

@Composable
fun AppShell() {
    var tab by rememberSaveable { mutableStateOf(Tab.INBOX) }
    val inbox: InboxViewModel = viewModel(factory = InboxViewModel.Factory)
    val total = inbox.state.collectAsStateWithLifecycle().value.list.total

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            ShortNavigationBar {
                ShortNavigationBarItem(
                    selected = tab == Tab.INBOX,
                    onClick = { tab = Tab.INBOX },
                    icon = {
                        BadgedBox(badge = { if (total > 0) Badge { Text(total.toString()) } }) {
                            Icon(painterResource(R.drawable.ic_inbox), contentDescription = null)
                        }
                    },
                    label = { Text(stringResource(R.string.nav_inbox)) },
                )
                ShortNavigationBarItem(
                    selected = tab == Tab.SETTINGS,
                    onClick = { tab = Tab.SETTINGS },
                    icon = { Icon(painterResource(R.drawable.ic_settings), contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_settings)) },
                )
            }
        },
    ) { padding ->
        when (tab) {
            Tab.INBOX -> {
                // The Inbox's own top bar handles the top insets.
                val bottom = PaddingValues(bottom = padding.calculateBottomPadding())
                InboxScreen(
                    onOpenSettings = { tab = Tab.SETTINGS },
                    modifier = Modifier.padding(bottom).consumeWindowInsets(bottom),
                    vm = inbox,
                )
            }
            Tab.SETTINGS -> DebugScreen(contentPadding = padding)
        }
    }
}

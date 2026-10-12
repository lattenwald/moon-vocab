package org.lattenwald.moonvocab.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import org.lattenwald.moonvocab.R
import org.lattenwald.moonvocab.ui.card.CardPage
import org.lattenwald.moonvocab.ui.card.PreviewPage
import org.lattenwald.moonvocab.ui.debug.DebugScreen
import org.lattenwald.moonvocab.ui.debug.DebugViewModel
import org.lattenwald.moonvocab.ui.inbox.InboxScreen
import org.lattenwald.moonvocab.ui.inbox.InboxViewModel
import org.lattenwald.moonvocab.ui.library.BookPage
import org.lattenwald.moonvocab.ui.library.LibraryScreen
import org.lattenwald.moonvocab.ui.library.LibraryViewModel
import org.lattenwald.moonvocab.ui.settings.LanguagePicker

private val TabTransitions =
    NavDisplay.transitionSpec { fadeIn() togetherWith fadeOut() } +
        NavDisplay.popTransitionSpec { fadeIn() togetherWith fadeOut() } +
        NavDisplay.predictivePopTransitionSpec { fadeIn() togetherWith fadeOut() }

@Composable
fun AppShell() {
    val backStack = rememberNavBackStack(InboxKey)
    val top = backStack.last()
    val inbox: InboxViewModel = viewModel(factory = InboxViewModel.Factory)
    val library: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory)
    val debug: DebugViewModel = viewModel(factory = DebugViewModel.Factory)
    val inboxState by inbox.state.collectAsStateWithLifecycle()
    val total = inboxState.list.total
    val selecting = top == InboxKey && inboxState.selection.active

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            AnimatedVisibility(
                visible = top in TabKeys && !selecting,
                enter = slideInVertically { it } + expandVertically(expandFrom = Alignment.Top),
                exit = slideOutVertically { it } + shrinkVertically(shrinkTowards = Alignment.Top),
            ) {
                ShortNavigationBar {
                    ShortNavigationBarItem(
                        selected = top == InboxKey,
                        onClick = { backStack.selectTab(InboxKey) },
                        icon = {
                            BadgedBox(badge = { if (total > 0) Badge { Text(total.toString()) } }) {
                                Icon(painterResource(R.drawable.ic_inbox), contentDescription = null)
                            }
                        },
                        label = { Text(stringResource(R.string.nav_inbox)) },
                    )
                    ShortNavigationBarItem(
                        selected = top == LibraryKey,
                        onClick = { backStack.selectTab(LibraryKey) },
                        icon = { Icon(painterResource(R.drawable.ic_menu_book), contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_library)) },
                    )
                    ShortNavigationBarItem(
                        selected = top == SettingsKey,
                        onClick = { backStack.selectTab(SettingsKey) },
                        icon = { Icon(painterResource(R.drawable.ic_settings), contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_settings)) },
                    )
                }
            }
        },
    ) { padding ->
        // NavDisplay keeps each entry's content lambda until the back stack changes.
        val shellPadding by rememberUpdatedState(padding)
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<InboxKey>(metadata = TabTransitions) {
                    // The Inbox's own top bar handles the top insets.
                    val bottom = PaddingValues(bottom = shellPadding.calculateBottomPadding())
                    InboxScreen(
                        onOpenSettings = { backStack.selectTab(SettingsKey) },
                        // A second tap during the transition must not push another page.
                        onOpenCard = { id -> if (backStack.lastOrNull() == InboxKey) backStack.add(CardKey(id)) },
                        modifier = Modifier.padding(bottom).consumeWindowInsets(bottom),
                        vm = inbox,
                    )
                }
                entry<LibraryKey>(metadata = TabTransitions) {
                    val bottom = PaddingValues(bottom = shellPadding.calculateBottomPadding())
                    LibraryScreen(
                        onOpenBook = { id -> if (backStack.lastOrNull() == LibraryKey) backStack.add(BookKey(id)) },
                        modifier = Modifier.padding(bottom).consumeWindowInsets(bottom),
                        vm = library,
                    )
                }
                // The page's own Scaffold handles the insets; a late onBack pops only this page.
                entry<BookKey> { key ->
                    BookPage(
                        key.bookId,
                        onBack = { if (backStack.lastOrNull() == key) backStack.removeLastOrNull() },
                        onOpenCard = { id -> if (backStack.lastOrNull() == key) backStack.add(CardKey(id)) },
                    )
                }
                entry<CardKey> { key ->
                    CardPage(
                        key.cardId,
                        onBack = { if (backStack.lastOrNull() == key) backStack.removeLastOrNull() },
                        onPreview = { id -> if (backStack.lastOrNull() == key) backStack.add(PreviewKey(id)) },
                    )
                }
                entry<PreviewKey> { key ->
                    PreviewPage(key.cardId, onBack = { if (backStack.lastOrNull() == key) backStack.removeLastOrNull() })
                }
                entry<LanguageKey> { key ->
                    LanguagePicker(key.kind, onBack = { if (backStack.lastOrNull() == key) backStack.removeLastOrNull() })
                }
                entry<SettingsKey>(metadata = TabTransitions) { DebugScreen(contentPadding = shellPadding, vm = debug) }
            },
        )
    }
}

package org.lattenwald.moonvocab.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.flow.first
import org.lattenwald.moonvocab.App
import org.lattenwald.moonvocab.R
import org.lattenwald.moonvocab.ui.card.CardPage
import org.lattenwald.moonvocab.ui.card.PreviewPage
import org.lattenwald.moonvocab.ui.debug.DebugScreen
import org.lattenwald.moonvocab.ui.inbox.ImportSheetViewModel
import org.lattenwald.moonvocab.ui.inbox.InboxScreen
import org.lattenwald.moonvocab.ui.inbox.InboxViewModel
import org.lattenwald.moonvocab.ui.library.BookPage
import org.lattenwald.moonvocab.ui.library.LibraryScreen
import org.lattenwald.moonvocab.ui.library.LibraryViewModel
import org.lattenwald.moonvocab.ui.settings.CardsPage
import org.lattenwald.moonvocab.ui.settings.LanguagePicker
import org.lattenwald.moonvocab.ui.settings.ServicePage
import org.lattenwald.moonvocab.ui.settings.ServicesPage
import org.lattenwald.moonvocab.ui.settings.SettingsScreen
import org.lattenwald.moonvocab.ui.setup.SetupFlow

private val TabTransitions =
    NavDisplay.transitionSpec { fadeIn() togetherWith fadeOut() } +
        NavDisplay.popTransitionSpec { fadeIn() togetherWith fadeOut() } +
        NavDisplay.predictivePopTransitionSpec { fadeIn() togetherWith fadeOut() }

private fun pageSlide(push: Boolean, spec: FiniteAnimationSpec<IntOffset>, layoutDirection: LayoutDirection): ContentTransform {
    val end = if (layoutDirection == LayoutDirection.Ltr) 1 else -1
    return if (push) {
        slideInHorizontally(spec) { width -> end * width } togetherWith slideOutHorizontally(spec) { width -> -end * width / 3 }
    } else {
        slideInHorizontally(spec) { width -> -end * width / 3 } togetherWith slideOutHorizontally(spec) { width -> end * width }
    }
}

@Composable
fun AppShell(share: ShareViewModel) {
    val app = LocalContext.current.applicationContext as App
    val backStack = rememberNavBackStack(if (app.setupDone) InboxKey else SetupKey)
    val navigator = remember(backStack) { Navigator(backStack) }
    val top = navigator.top
    val inbox: InboxViewModel = viewModel(factory = InboxViewModel.Factory)
    val sheet: ImportSheetViewModel = viewModel(factory = ImportSheetViewModel.Factory)
    val library: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory)
    val inboxState by inbox.state.collectAsStateWithLifecycle()
    val total = inboxState.list.total
    val selecting = top == InboxKey && inboxState.selection.active

    val shared by share.pending.collectAsStateWithLifecycle()
    val leaveForShare by rememberUpdatedState(shared != null && app.setupDone)
    LaunchedEffect(shared) {
        val uri = shared ?: return@LaunchedEffect
        if (app.setupDone) {
            snapshotFlow { navigator.top }.first { it !is CardKey }
            sheet.share(uri)
            inbox.clearSelection()
            navigator.selectTab(InboxKey)
            inbox.requestImportSheet()
        }
        share.clear()
    }

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
                        onClick = { navigator.selectTab(InboxKey) },
                        icon = {
                            BadgedBox(badge = { if (total > 0) Badge { Text(total.toString()) } }) {
                                Icon(painterResource(R.drawable.ic_inbox), contentDescription = null)
                            }
                        },
                        label = { Text(stringResource(R.string.nav_inbox)) },
                    )
                    ShortNavigationBarItem(
                        selected = top == LibraryKey,
                        onClick = { navigator.selectTab(LibraryKey) },
                        icon = { Icon(painterResource(R.drawable.ic_menu_book), contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_library)) },
                    )
                    ShortNavigationBarItem(
                        selected = top == SettingsKey,
                        onClick = { navigator.selectTab(SettingsKey) },
                        icon = { Icon(painterResource(R.drawable.ic_settings), contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_settings)) },
                    )
                }
            }
        },
    ) { padding ->
        // NavDisplay keeps each entry's content lambda until the back stack changes.
        val shellPadding by rememberUpdatedState(padding)
        // No bounce: an overshooting full-width slide shows the shell's background at the edge.
        val slideSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow, IntOffset.VisibilityThreshold)
        val layoutDirection = LocalLayoutDirection.current
        NavDisplay(
            backStack = backStack,
            onBack = navigator::popTop,
            transitionSpec = { pageSlide(push = true, slideSpec, layoutDirection) },
            popTransitionSpec = { pageSlide(push = false, slideSpec, layoutDirection) },
            predictivePopTransitionSpec = { _ -> pageSlide(push = false, slideSpec, layoutDirection) },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<InboxKey>(metadata = TabTransitions) {
                    // The Inbox's own top bar handles the top insets.
                    val bottom = PaddingValues(bottom = shellPadding.calculateBottomPadding())
                    InboxScreen(
                        onOpenServices = { navigator.at(InboxKey).openAbove(SettingsKey, ServicesKey) },
                        onOpenCard = { id -> navigator.at(InboxKey).open(CardKey(id)) },
                        modifier = Modifier.padding(bottom).consumeWindowInsets(bottom),
                        vm = inbox,
                        sheetVm = sheet,
                    )
                }
                entry<LibraryKey>(metadata = TabTransitions) {
                    val bottom = PaddingValues(bottom = shellPadding.calculateBottomPadding())
                    LibraryScreen(
                        onOpenBook = { id -> navigator.at(LibraryKey).open(BookKey(id)) },
                        modifier = Modifier.padding(bottom).consumeWindowInsets(bottom),
                        vm = library,
                    )
                }
                // The page's own Scaffold handles the insets.
                entry<BookKey> { key ->
                    val nav = navigator.at(key)
                    BookPage(key.bookId, onBack = nav::back, onOpenCard = { id -> nav.open(CardKey(id)) })
                }
                entry<CardKey> { key ->
                    val nav = navigator.at(key)
                    CardPage(
                        key.cardId,
                        onBack = nav::back,
                        onPreview = { id -> nav.open(PreviewKey(id)) },
                        leaveRequested = leaveForShare,
                    )
                }
                entry<PreviewKey> { key ->
                    PreviewPage(key.cardId, onBack = navigator.at(key)::back)
                }
                entry<LanguageKey> { key ->
                    LanguagePicker(key.kind, onBack = navigator.at(key)::back)
                }
                entry<ServicesKey> { key ->
                    val nav = navigator.at(key)
                    ServicesPage(onBack = nav::back, onOpen = { id -> nav.open(ServiceKey(id)) })
                }
                entry<ServiceKey> { key ->
                    val nav = navigator.at(key)
                    ServicePage(key.id, onBack = nav::back, onOpen = { id -> nav.replace(ServiceKey(id)) })
                }
                entry<CardsSettingsKey> { key ->
                    val nav = navigator.at(key)
                    CardsPage(onBack = nav::back, onPreview = { nav.open(PreviewKey(null)) })
                }
                entry<DeveloperKey> { key ->
                    DebugScreen(onBack = navigator.at(key)::back)
                }
                entry<SetupKey> {
                    SetupFlow(
                        onOpen = navigator.at(SetupKey)::open,
                        onFinish = { importNow ->
                            if (navigator.at(SetupKey).startOver(InboxKey) && importNow) {
                                sheet.open()
                                inbox.requestImportSheet()
                            }
                        },
                    )
                }
                entry<SettingsKey>(metadata = TabTransitions) {
                    SettingsScreen(
                        onOpen = navigator.at(SettingsKey)::open,
                        contentPadding = PaddingValues(bottom = shellPadding.calculateBottomPadding()),
                    )
                }
            },
        )
    }
}

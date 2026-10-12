package org.lattenwald.moonvocab.ui.setup

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.net.toUri
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import org.lattenwald.moonvocab.R
import org.lattenwald.moonvocab.data.anki.AnkiAvailability
import org.lattenwald.moonvocab.data.settings.SettingsSnapshot
import org.lattenwald.moonvocab.domain.card.MarkedText
import org.lattenwald.moonvocab.ui.LanguageKey
import org.lattenwald.moonvocab.ui.LanguageKind
import org.lattenwald.moonvocab.ui.ServicesKey
import org.lattenwald.moonvocab.ui.common.MarkedExcerpt
import org.lattenwald.moonvocab.ui.common.languageName
import org.lattenwald.moonvocab.ui.common.openAnkiDroidStore
import org.lattenwald.moonvocab.ui.common.rememberAnkiPermissionRequest
import org.lattenwald.moonvocab.ui.inbox.inboxColors
import org.lattenwald.moonvocab.ui.settings.ServiceField
import org.lattenwald.moonvocab.ui.settings.ServiceForm
import org.lattenwald.moonvocab.ui.settings.ServicesViewModel
import org.lattenwald.moonvocab.ui.settings.SettingsViewModel
import org.lattenwald.moonvocab.ui.settings.folderName
import org.lattenwald.moonvocab.ui.settings.serviceTitle
import org.lattenwald.moonvocab.ui.theme.BookFontFamily

private const val WELCOME = 0
private const val FOLDERS = 1
private const val ANKI = 2
private const val TRANSLATION = 3
private const val READY = 4

private const val OPENROUTER_HOST = "openrouter.ai"
private const val OPENROUTER_KEYS_URL = "https://openrouter.ai/keys"

@Composable
fun SetupFlow(
    onOpen: (NavKey) -> Unit,
    onFinish: (importNow: Boolean) -> Unit,
    vm: SetupViewModel = viewModel(factory = SetupViewModel.Factory),
    settingsVm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
    servicesVm: ServicesViewModel = viewModel(factory = ServicesViewModel.Factory),
) {
    var step by rememberSaveable { mutableIntStateOf(WELCOME) }
    BackHandler(enabled = step > WELCOME) { step-- }

    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val backups by vm.backups.collectAsStateWithLifecycle()
    val newMarks by vm.newMarks.collectAsStateWithLifecycle()
    val anki by settingsVm.ankiAvailability.collectAsStateWithLifecycle()
    LifecycleResumeEffect(settingsVm) {
        settingsVm.refreshAnki()
        onPauseOrDispose {}
    }
    LaunchedEffect(step) { if (step == READY) vm.countNew() }

    val context = LocalContext.current
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { settingsVm.setBackupTree(it, onSaved = vm::rescan) }
    }
    val pickBooks = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(settingsVm::setBooksTree) }
    var ankiAnswered by rememberSaveable { mutableStateOf(false) }
    val requestAnki = rememberAnkiPermissionRequest(onResult = {
        ankiAnswered = true
        settingsVm.refreshAnki()
    })

    val s = snapshot
    val canContinue = when (step) {
        FOLDERS -> s?.backupTree != null && (backups as? BackupScan.Found)?.tree == s.backupTree
        ANKI -> anki == AnkiAvailability.READY
        TRANSLATION -> s != null && s.activeService.apiKey.isNotBlank()
        else -> true
    }
    val finish: (Boolean) -> Unit = { importNow ->
        vm.finish()
        onFinish(importNow)
    }

    val colors = inboxColors()
    Scaffold(
        containerColor = colors.screen,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (step in FOLDERS..TRANSLATION) StepBars(step)
            val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
            AnimatedContent(
                targetState = step,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val sign = if (targetState > initialState) direction else -direction
                    (slideInHorizontally { sign * it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -sign * it / 4 } + fadeOut())
                },
                label = "setup step",
            ) { shown ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (shown) {
                        WELCOME -> WelcomeStep()
                        FOLDERS -> if (s != null) FoldersStep(s, backups, onChooseBackup = { pickBackup.launch(null) }, onChooseBooks = { pickBooks.launch(null) })
                        ANKI -> AnkiStep(anki, ankiAnswered, onAllow = requestAnki, onGet = { openAnkiDroidStore(context) })
                        TRANSLATION -> if (s != null) TranslationStep(s, servicesVm, onOpen)
                        else -> ReadyStep(newMarks)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (step) {
                    WELCOME -> FilledAction(R.string.setup_get_started) { step = FOLDERS }
                    READY -> ReadyActions(newMarks, finish)
                    else -> {
                        TonalAction(R.string.setup_back) { step-- }
                        FilledAction(R.string.setup_continue, enabled = canContinue) { step++ }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.ReadyActions(newMarks: NewMarks, finish: (Boolean) -> Unit) {
    when (newMarks) {
        NewMarks.Counting -> {
            TonalAction(R.string.setup_later, enabled = false) {}
            FilledAction(R.string.setup_import_now, enabled = false) {}
        }
        is NewMarks.Counted -> if (newMarks.count > 0) {
            TonalAction(R.string.setup_later) { finish(false) }
            FilledAction(R.string.setup_import_now) { finish(true) }
        } else {
            FilledAction(R.string.setup_done) { finish(false) }
        }
        NewMarks.Failed -> FilledAction(R.string.setup_done) { finish(false) }
    }
}

@Composable
private fun StepBars(step: Int) {
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, top = 14.dp, end = 24.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (bar in FOLDERS..TRANSLATION) {
            val color by animateColorAsState(
                if (bar <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                label = "step bar",
            )
            Box(Modifier.weight(1f).height(4.dp).background(color, RoundedCornerShape(2.dp)))
        }
    }
}

@Composable
private fun WelcomeStep() {
    Hero()
    Title(R.string.setup_welcome_title)
    Lede(stringResource(R.string.setup_welcome_text))
}

@Composable
private fun Hero() {
    val colors = MaterialTheme.colorScheme
    val excerpt = stringResource(R.string.setup_hero_excerpt)
    val mark = stringResource(R.string.setup_hero_mark)
    val start = excerpt.indexOf(mark)
    Column(
        Modifier.padding(top = 8.dp, bottom = 10.dp).fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(inboxColors().row)
            .padding(start = 24.dp, top = 30.dp, end = 24.dp, bottom = 26.dp),
    ) {
        MarkedExcerpt(
            MarkedText(excerpt, if (start < 0) null else start until start + mark.length),
            style = TextStyle(fontFamily = BookFontFamily, fontSize = 21.sp, lineHeight = 34.sp, localeList = LocaleList("en")),
        )
        Column(
            Modifier.padding(top = 22.dp).clip(RoundedCornerShape(20.dp)).background(colors.primaryContainer)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                stringResource(R.string.setup_hero_word),
                color = colors.onPrimaryContainer,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                style = TextStyle(localeList = LocaleList("en")),
            )
            Text(
                stringResource(R.string.setup_hero_translation),
                color = colors.onPrimaryContainer,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                style = TextStyle(localeList = LocaleList("ru")),
            )
        }
    }
}

@Composable
private fun FoldersStep(s: SettingsSnapshot, backups: BackupScan, onChooseBackup: () -> Unit, onChooseBooks: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Title(R.string.setup_folders_title)
    Lede(stringResource(R.string.setup_folders_text))
    val tree = s.backupTree
    if (tree == null) {
        ChooseButton(onChooseBackup)
    } else {
        when {
            backups is BackupScan.Found && backups.tree == tree ->
                ResultRow(folderName(tree), foundLine(backups), ResultIcon.OK, onClick = onChooseBackup)
            backups is BackupScan.Unreadable && backups.tree == tree -> {
                ResultRow(folderName(tree), stringResource(R.string.settings_folder_unreadable), ResultIcon.ERROR, colors.error, onChooseBackup)
                ChooseButton(onChooseBackup)
            }
            else -> ResultRow(folderName(tree), stringResource(R.string.setup_backups_searching), ResultIcon.BUSY, onClick = onChooseBackup)
        }
    }
    Text(
        stringResource(R.string.settings_books),
        Modifier.padding(top = 12.dp).semantics { heading() },
        color = colors.primary,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Lede(stringResource(R.string.setup_books_text), small = true)
    val books = s.booksTree
    if (books == null) ChooseButton(onChooseBooks) else ResultRow(folderName(books), null, ResultIcon.OK, onClick = onChooseBooks)
}

@Composable
private fun foundLine(found: BackupScan.Found): String = when {
    found.count == 0 -> stringResource(R.string.setup_backups_none)
    found.newest <= 0L -> pluralStringResource(R.plurals.setup_backups_found_undated, found.count, found.count)
    else -> {
        val newest = DateUtils.getRelativeTimeSpanString(found.newest).toString()
        pluralStringResource(R.plurals.setup_backups_found, found.count, found.count, newest)
    }
}

@Composable
private fun AnkiStep(anki: AnkiAvailability?, answered: Boolean, onAllow: () -> Unit, onGet: () -> Unit) {
    Title(R.string.setup_anki_title)
    Lede(stringResource(R.string.setup_anki_text))
    when (anki) {
        AnkiAvailability.READY -> ResultRow(stringResource(R.string.setup_anki_ok), null, ResultIcon.OK)
        AnkiAvailability.NO_PERMISSION -> {
            FitButton(R.drawable.ic_school, R.string.settings_anki_allow, filled = true, onClick = onAllow)
            if (answered) Lede(stringResource(R.string.setup_anki_denied), small = true, color = MaterialTheme.colorScheme.error)
        }
        AnkiAvailability.NOT_INSTALLED -> {
            Lede(stringResource(R.string.setup_anki_missing), small = true)
            FitButton(R.drawable.ic_download, R.string.setup_anki_get, filled = true, onClick = onGet)
        }
        null -> Unit
    }
}

@Composable
private fun TranslationStep(s: SettingsSnapshot, servicesVm: ServicesViewModel, onOpen: (NavKey) -> Unit) {
    val service = s.activeService
    Title(R.string.setup_translation_title)
    Lede(stringResource(R.string.setup_translation_text, serviceTitle(service)))
    val keyHelp = if (isOpenRouter(service.baseUrl)) openRouterKeyHelp() else null
    ServiceForm(service, servicesVm, fields = setOf(ServiceField.KEY), keyHelp = keyHelp)
    TextButton(onClick = { onOpen(ServicesKey) }) { Text(stringResource(R.string.setup_other_service)) }
    LanguageRow(languageName(s.targetLanguage)) { onOpen(LanguageKey(LanguageKind.TARGET)) }
}

private fun isOpenRouter(baseUrl: String): Boolean {
    val host = baseUrl.trim().toUri().host ?: return false
    return host.equals(OPENROUTER_HOST, ignoreCase = true) || host.endsWith(".$OPENROUTER_HOST", ignoreCase = true)
}

@Composable
private fun openRouterKeyHelp(): AnnotatedString {
    val link = stringResource(R.string.setup_openrouter_keys)
    val text = stringResource(R.string.setup_openrouter_key_help, link)
    val linkStyle = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
    return remember(text, link, linkStyle) {
        buildAnnotatedString {
            append(text)
            val start = text.indexOf(link)
            if (start >= 0) addLink(LinkAnnotation.Url(OPENROUTER_KEYS_URL, linkStyle), start, start + link.length)
        }
    }
}

@Composable
private fun LanguageRow(value: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(inboxColors().row).clickable(onClick = onClick)
            .padding(start = 16.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).background(colors.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_language), contentDescription = null, Modifier.size(20.dp), tint = colors.onSecondaryContainer)
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_into), fontSize = 16.sp, lineHeight = 22.sp)
            Text(value, color = colors.onSurfaceVariant, fontSize = 13.5.sp, lineHeight = 19.sp)
        }
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.onSurfaceVariant)
    }
}

@Composable
private fun ReadyStep(newMarks: NewMarks) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.padding(top = 40.dp).size(96.dp).rotate(-8f).background(colors.primaryContainer, RoundedCornerShape(32.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, Modifier.size(48.dp).rotate(8f), tint = colors.onPrimaryContainer)
    }
    Title(R.string.setup_ready_title)
    when (newMarks) {
        NewMarks.Counting -> LinearWavyProgressIndicator(Modifier.padding(top = 8.dp).width(96.dp))
        is NewMarks.Counted -> Lede(
            if (newMarks.count > 0) {
                pluralStringResource(R.plurals.setup_ready_new, newMarks.count, newMarks.count)
            } else {
                stringResource(R.string.setup_ready_none)
            },
        )
        NewMarks.Failed -> Lede(stringResource(R.string.setup_ready_failed), color = colors.error)
    }
}

@Composable
private fun Title(text: Int) {
    Text(
        stringResource(text),
        Modifier.padding(top = 8.dp).semantics { heading() },
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.3).sp,
    )
}

@Composable
private fun Lede(text: String, small: Boolean = false, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, color = color, fontSize = if (small) 14.sp else 16.sp, lineHeight = if (small) 20.sp else 24.sp)
}

private enum class ResultIcon { OK, ERROR, BUSY }

@Composable
private fun ResultRow(
    title: String,
    sub: String?,
    icon: ResultIcon,
    subColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val click = if (onClick == null) Modifier else Modifier.clickable(onClickLabel = stringResource(R.string.setup_choose_folder), onClick = onClick)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(inboxColors().row).then(click)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (icon) {
            ResultIcon.OK -> Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, tint = colors.primary)
            ResultIcon.ERROR -> Icon(painterResource(R.drawable.ic_error), contentDescription = null, tint = colors.error)
            ResultIcon.BUSY -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
            if (sub != null) Text(sub, color = subColor, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun ChooseButton(onClick: () -> Unit) = FitButton(R.drawable.ic_folder_open, R.string.setup_choose_folder, filled = false, onClick = onClick)

@Composable
private fun FitButton(icon: Int, text: Int, filled: Boolean, onClick: () -> Unit) {
    val height = ButtonDefaults.MediumContainerHeight
    val modifier = Modifier.padding(top = 4.dp).heightIn(min = height)
    val padding = PaddingValues(start = 20.dp, end = 24.dp)
    val content: @Composable RowScope.() -> Unit = {
        Icon(painterResource(icon), contentDescription = null, Modifier.size(ButtonDefaults.iconSizeFor(height)))
        Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
        Text(stringResource(text), style = ButtonDefaults.textStyleFor(height))
    }
    if (filled) {
        Button(onClick, modifier, contentPadding = padding, content = content)
    } else {
        FilledTonalButton(onClick, modifier, contentPadding = padding, content = content)
    }
}

@Composable
private fun RowScope.FilledAction(text: Int, enabled: Boolean = true, onClick: () -> Unit) {
    val height = ButtonDefaults.MediumContainerHeight
    Button(onClick, Modifier.weight(1f).heightIn(min = height), enabled = enabled, contentPadding = ButtonDefaults.contentPaddingFor(height)) {
        Text(stringResource(text), style = ButtonDefaults.textStyleFor(height))
    }
}

@Composable
private fun RowScope.TonalAction(text: Int, enabled: Boolean = true, onClick: () -> Unit) {
    val height = ButtonDefaults.MediumContainerHeight
    FilledTonalButton(onClick, Modifier.weight(1f).heightIn(min = height), enabled = enabled, contentPadding = ButtonDefaults.contentPaddingFor(height)) {
        Text(stringResource(text), style = ButtonDefaults.textStyleFor(height))
    }
}

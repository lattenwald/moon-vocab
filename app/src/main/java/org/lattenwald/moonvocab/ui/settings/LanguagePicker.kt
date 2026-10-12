package org.lattenwald.moonvocab.ui.settings

import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.LocaleManagerCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lattenwald.moonvocab.App
import org.lattenwald.moonvocab.R
import org.lattenwald.moonvocab.data.settings.Settings
import org.lattenwald.moonvocab.domain.settings.LANGUAGE_CODES
import org.lattenwald.moonvocab.domain.settings.searchLanguages
import org.lattenwald.moonvocab.domain.settings.sortedLanguages
import org.lattenwald.moonvocab.domain.settings.suggestedLanguages
import org.lattenwald.moonvocab.ui.LanguageKind
import org.lattenwald.moonvocab.ui.common.languageName
import org.lattenwald.moonvocab.ui.inbox.forInboxList
import org.lattenwald.moonvocab.ui.inbox.inboxColors
import org.lattenwald.moonvocab.ui.inbox.segmentedRowShapes
import org.lattenwald.moonvocab.ui.library.LibrarySubheader

class LanguagePickerViewModel(private val settings: Settings, private val io: CoroutineDispatcher) : ViewModel() {
    val target: StateFlow<String?> = settings.snapshot.map { it.targetLanguage }
        .flowOn(io)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Pops after the write: popping clears this ViewModel, which would cancel it.
    fun setTarget(code: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                withContext(io) { settings.update { it.copy(targetLanguage = code) } }
            } catch (e: IOException) {
                Log.e(TAG, "target language write failed", e)
            }
            onDone()
        }
    }

    companion object {
        private const val TAG = "LanguagePickerViewModel"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as App
                val c = app.container
                LanguagePickerViewModel(c.settings, c.io)
            }
        }
    }
}

private const val SAME_AS_PHONE = "system"
private val APP_LANGUAGES = listOf("en", "ru")
private val RowPadding = PaddingValues(start = 18.dp, top = 14.dp, end = 12.dp, bottom = 14.dp)

private data class Choice(val code: String, val title: String, val own: String?)

@Composable
fun LanguagePicker(
    kind: LanguageKind,
    onBack: () -> Unit,
    vm: LanguagePickerViewModel = viewModel(factory = LanguagePickerViewModel.Factory),
) {
    val colors = inboxColors()
    Scaffold(
        containerColor = colors.screen,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (kind == LanguageKind.TARGET) R.string.picker_target else R.string.picker_app)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.card_navigate_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.screen),
            )
        },
    ) { padding ->
        val themeShapes = MaterialTheme.shapes
        MaterialTheme(shapes = remember(themeShapes) { themeShapes.forInboxList() }) {
            when (kind) {
                LanguageKind.TARGET -> TargetList(vm, padding, colors.row, onBack)
                LanguageKind.APP -> AppList(padding, colors.row, onBack)
            }
        }
    }
}

@Composable
private fun TargetList(vm: LanguagePickerViewModel, padding: PaddingValues, rowColor: Color, onBack: () -> Unit) {
    val current by vm.target.collectAsStateWithLifecycle()
    val ui = LocalConfiguration.current.locales[0]
    val context = LocalContext.current
    // Not LocaleListCompat.getAdjustedDefault(): below API 33 AppCompat puts the app language there.
    val device = remember(context) { LocaleManagerCompat.getSystemLocales(context).languageCodes() }
    var query by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val direction = LocalLayoutDirection.current
    Column(
        Modifier.fillMaxSize().padding(
            start = padding.calculateStartPadding(direction),
            top = padding.calculateTopPadding(),
            end = padding.calculateEndPadding(direction),
        ),
    ) {
        SearchField(query, onQueryChange = {
            query = it
            scope.launch { listState.scrollToItem(0) }
        })
        Text(
            stringResource(R.string.settings_target_note),
            Modifier.padding(start = 24.dp, end = 24.dp, bottom = 6.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.5.sp,
            lineHeight = 19.sp,
        )
        val selected = current ?: return@Column
        val q = query.trim()
        val hits = remember(q, ui) { if (q.isEmpty()) emptyList() else searchLanguages(LANGUAGE_CODES, q, ui).map { languageRow(it, ui) } }
        val suggested = remember(selected, device, ui) { suggestedLanguages(selected, device).map { languageRow(it, ui) } }
        val all = remember(ui) { sortedLanguages(LANGUAGE_CODES, ui).map { languageRow(it, ui) } }
        val suggestedTitle = stringResource(R.string.picker_suggested)
        val allTitle = stringResource(R.string.picker_all)
        val pick: (String) -> Unit = { code -> vm.setTarget(code, onDone = onBack) }
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = padding.calculateBottomPadding())) {
            when {
                q.isEmpty() -> {
                    group("s", suggestedTitle, suggested, selected, rowColor, pick)
                    group("a", allTitle, all, selected, rowColor, pick)
                }
                hits.isEmpty() -> item(key = "none") { NoMatches(q) }
                else -> group("h", null, hits, selected, rowColor, pick)
            }
            item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun AppList(padding: PaddingValues, rowColor: Color, onBack: () -> Unit) {
    val ui = LocalConfiguration.current.locales[0]
    val context = LocalContext.current
    val phone = remember(context) { LocaleManagerCompat.getSystemLocales(context).languageCodes().firstOrNull() }
    val current = remember {
        val chosen = AppCompatDelegate.getApplicationLocales()
        if (chosen.isEmpty) SAME_AS_PHONE else chosen[0]?.language
    }
    val rows = listOf(Choice(SAME_AS_PHONE, stringResource(R.string.picker_same_phone, phone?.let { languageName(it, ui) }.orEmpty()), null)) +
        APP_LANGUAGES.map { Choice(it, languageName(it, Locale.forLanguageTag(it)), null) }
    val pick: (String) -> Unit = { code ->
        if (code != current) {
            AppCompatDelegate.setApplicationLocales(
                if (code == SAME_AS_PHONE) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(code),
            )
        }
        onBack()
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        group("app", null, rows, current, rowColor, pick)
    }
}

private fun LocaleListCompat.languageCodes(): List<String> =
    (0 until size()).mapNotNull { get(it)?.toLanguageTag()?.substringBefore('-') }

private fun languageRow(code: String, ui: Locale): Choice {
    val title = languageName(code, ui)
    val own = languageName(code, Locale.forLanguageTag(code))
    return Choice(code, title, own.takeIf { it != title })
}

private fun LazyListScope.group(key: String, title: String?, rows: List<Choice>, selected: String?, rowColor: Color, onPick: (String) -> Unit) {
    if (rows.isEmpty()) return
    if (title != null) item(key = "h$key", contentType = "header") { LibrarySubheader(title) }
    itemsIndexed(rows, key = { _, row -> "$key${row.code}" }, contentType = { _, _ -> "language" }) { index, row ->
        RadioRow(index, rows.size, row, row.code == selected, rowColor, onClick = { onPick(row.code) })
    }
}

@Composable
private fun RadioRow(index: Int, count: Int, row: Choice, selected: Boolean, containerColor: Color, onClick: () -> Unit) {
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        shapes = segmentedRowShapes(index, count),
        modifier = Modifier.padding(
            start = 12.dp,
            top = if (index > 0) ListItemDefaults.SegmentedGap else 0.dp,
            end = 12.dp,
            bottom = if (index == count - 1) 4.dp else 0.dp,
        ),
        colors = ListItemDefaults.segmentedColors(containerColor = containerColor, selectedContainerColor = containerColor),
        contentPadding = RowPadding,
        supportingContent = row.own?.let { own ->
            {
                Text(
                    own,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = TextStyle(fontSize = 13.5.sp, lineHeight = 19.sp, localeList = LocaleList(row.code)),
                )
            }
        },
        trailingContent = { RadioButton(selected = selected, onClick = null) },
    ) {
        Text(row.title, fontSize = 16.sp, lineHeight = 22.sp)
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val container = MaterialTheme.colorScheme.surfaceContainerHighest
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 8.dp),
        placeholder = { Text(stringResource(R.string.picker_search)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
        trailingIcon = if (query.isEmpty()) {
            null
        } else {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.picker_clear))
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = container,
            unfocusedContainerColor = container,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun NoMatches(query: String) {
    Text(
        stringResource(R.string.picker_none, query),
        Modifier.fillMaxWidth().padding(start = 36.dp, top = 48.dp, end = 36.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 14.5.sp,
        lineHeight = 21.sp,
        textAlign = TextAlign.Center,
    )
}

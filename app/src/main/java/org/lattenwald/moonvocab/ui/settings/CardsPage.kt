package org.lattenwald.moonvocab.ui.settings

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.IOException
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.lattenwald.moonvocab.App
import org.lattenwald.moonvocab.R
import org.lattenwald.moonvocab.data.settings.DEFAULT_DECK_PATTERN
import org.lattenwald.moonvocab.data.settings.DEFAULT_TAG_PATTERN
import org.lattenwald.moonvocab.data.settings.Settings
import org.lattenwald.moonvocab.data.settings.SettingsSnapshot
import org.lattenwald.moonvocab.domain.card.EnglishAccent
import org.lattenwald.moonvocab.domain.card.sampleCard
import org.lattenwald.moonvocab.ui.card.FrontFace
import org.lattenwald.moonvocab.ui.inbox.forInboxList
import org.lattenwald.moonvocab.ui.inbox.inboxColors
import org.lattenwald.moonvocab.ui.common.segmentedRowShapes
import org.lattenwald.moonvocab.ui.library.LibrarySubheader

class CardsViewModel(
    private val settings: Settings,
    private val io: CoroutineDispatcher,
    private val appScope: CoroutineScope,
) : ViewModel() {
    val snapshot: StateFlow<SettingsSnapshot?> = settings.snapshot
        .flowOn(io)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // A fair lock reached undispatched from the main thread: writes land in keystroke order.
    private val writes = Mutex()

    fun setMaxWords(words: Int) = write { it.copy(maxExcerptWords = words) }

    fun setAccent(accent: EnglishAccent) = write { it.copy(englishAccent = accent) }

    fun setDeckPattern(pattern: String) = write { it.copy(deckPattern = pattern.ifBlank { DEFAULT_DECK_PATTERN }) }

    fun setTagPattern(pattern: String) = write { it.copy(tagPattern = pattern.ifBlank { DEFAULT_TAG_PATTERN }) }

    // In appScope, so a write queued when the page closes still lands.
    private fun write(transform: (SettingsSnapshot) -> SettingsSnapshot) {
        appScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                writes.withLock { withContext(io) { settings.update(transform) } }
            } catch (e: IOException) {
                Log.e(TAG, "settings write failed", e)
            }
        }
    }

    companion object {
        private const val TAG = "CardsViewModel"
        const val MIN_WORDS = 6
        const val MAX_WORDS = 15

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as App
                val c = app.container
                CardsViewModel(c.settings, c.io, c.appScope)
            }
        }
    }
}

private val GroupShape = RoundedCornerShape(24.dp)
private val RowPadding = PaddingValues(start = 18.dp, top = 14.dp, end = 16.dp, bottom = 14.dp)

@Composable
fun CardsPage(
    onBack: () -> Unit,
    onPreview: () -> Unit,
    vm: CardsViewModel = viewModel(factory = CardsViewModel.Factory),
) {
    val colors = inboxColors()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = colors.screen,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cards_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.card_navigate_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.screen),
            )
        },
    ) { padding ->
        val s = snapshot ?: return@Scaffold
        // Only this page writes it, so the store is never read back.
        var words by remember { mutableIntStateOf(s.maxExcerptWords) }
        val sample = remember(words, s.englishAccent) { sampleCard(words, s.englishAccent) }
        // Padding outside the scroll, so a focused field is brought into view above the keyboard.
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(top = 4.dp, bottom = 24.dp)) {
            LongestExcerpt(words, colors.row, onChange = { words = it }, onDone = { vm.setMaxWords(words) })
            Pronunciation(s.englishAccent, colors.row, onSelect = vm::setAccent)
            LibrarySubheader(stringResource(R.string.cards_example))
            Box(Modifier.padding(horizontal = 12.dp)) { FrontFace(sample, colors.row) }
            Column(Modifier.padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PatternField(
                    s.deckPattern,
                    DEFAULT_DECK_PATTERN,
                    R.string.cards_deck,
                    ImeAction.Next,
                    help = {
                        Column {
                            Text(stringResource(R.string.cards_deck_help))
                            s.deckOverride?.let { Text(stringResource(R.string.cards_test_deck, it), Modifier.padding(top = 4.dp)) }
                        }
                    },
                    onChange = vm::setDeckPattern,
                )
                PatternField(
                    s.tagPattern,
                    DEFAULT_TAG_PATTERN,
                    R.string.cards_tag,
                    ImeAction.Done,
                    help = { Text(stringResource(R.string.cards_tag_help)) },
                    onChange = vm::setTagPattern,
                )
            }
            val themeShapes = MaterialTheme.shapes
            MaterialTheme(shapes = remember(themeShapes) { themeShapes.forInboxList() }) {
                DesignRow(colors.row, onPreview)
            }
        }
    }
}

@Composable
private fun LongestExcerpt(words: Int, rowColor: Color, onChange: (Int) -> Unit, onDone: () -> Unit) {
    val title = pluralStringResource(R.plurals.cards_longest, words, words)
    Column(Modifier.padding(horizontal = 12.dp).fillMaxWidth().background(rowColor, GroupShape).padding(RowPadding)) {
        Text(title, fontSize = 16.sp, lineHeight = 22.sp)
        val state = rememberSliderState(
            words.toFloat(),
            CardsViewModel.MAX_WORDS - CardsViewModel.MIN_WORDS - 1,
            CardsViewModel.MIN_WORDS.toFloat()..CardsViewModel.MAX_WORDS.toFloat(),
        )
        // With onValueChange given, the state leaves updating its value to the caller.
        Slider(
            state,
            onValueChange = {
                state.value = it
                onChange(it.roundToInt())
            },
            modifier = Modifier.padding(vertical = 4.dp).semantics { stateDescription = title },
            onValueChangeFinished = onDone,
        )
        Text(
            stringResource(R.string.cards_longest_help),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.5.sp,
            lineHeight = 19.sp,
        )
    }
}

private val ACCENT_LABELS = listOf(
    EnglishAccent.AMERICAN to R.string.cards_accent_american,
    EnglishAccent.BRITISH to R.string.cards_accent_british,
)

@Composable
private fun Pronunciation(accent: EnglishAccent, rowColor: Color, onSelect: (EnglishAccent) -> Unit) {
    Column(Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp).fillMaxWidth().background(rowColor, GroupShape).padding(RowPadding)) {
        Text(stringResource(R.string.cards_accent), fontSize = 16.sp, lineHeight = 22.sp)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            ACCENT_LABELS.forEachIndexed { i, (option, label) ->
                SegmentedButton(
                    selected = accent == option,
                    onClick = { if (accent != option) onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = ACCENT_LABELS.size),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        Text(
            stringResource(R.string.cards_accent_help),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.5.sp,
            lineHeight = 19.sp,
        )
    }
}

// Local state, so a write never moves the cursor.
@Composable
private fun PatternField(
    stored: String,
    default: String,
    @StringRes label: Int,
    imeAction: ImeAction,
    help: @Composable () -> Unit,
    onChange: (String) -> Unit,
) {
    var value by remember { mutableStateOf(stored) }
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = {
            value = it
            onChange(it)
        },
        modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused && value.isBlank()) value = default },
        label = { Text(stringResource(label)) },
        supportingText = help,
        singleLine = true,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
    )
}

@Composable
private fun DesignRow(containerColor: Color, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    SegmentedListItem(
        onClick = onClick,
        shapes = segmentedRowShapes(0, 1),
        modifier = Modifier.padding(horizontal = 12.dp),
        colors = ListItemDefaults.segmentedColors(containerColor = containerColor),
        contentPadding = RowPadding,
        leadingContent = {
            Box(Modifier.size(40.dp).background(colors.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_style), contentDescription = null, Modifier.size(20.dp), tint = colors.onSecondaryContainer)
            }
        },
        supportingContent = {
            Text(stringResource(R.string.cards_design_help), color = colors.onSurfaceVariant, fontSize = 13.5.sp, lineHeight = 19.sp)
        },
        trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.onSurfaceVariant) },
    ) {
        Text(stringResource(R.string.cards_design), fontSize = 16.sp, lineHeight = 22.sp)
    }
}

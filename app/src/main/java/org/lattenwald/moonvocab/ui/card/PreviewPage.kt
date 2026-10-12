package org.lattenwald.moonvocab.ui.card

import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import org.lattenwald.moonvocab.App
import org.lattenwald.moonvocab.R
import org.lattenwald.moonvocab.data.anki.ankiFields
import org.lattenwald.moonvocab.data.db.AppDatabase
import org.lattenwald.moonvocab.domain.card.previewHtml
import org.lattenwald.moonvocab.domain.import.bookLine
import org.lattenwald.moonvocab.ui.inbox.inboxColors

class PreviewViewModel(cardId: Long, db: AppDatabase, io: CoroutineDispatcher) : ViewModel() {
    val fields: StateFlow<Map<String, String>?> = flow {
        val card = db.cards().byIds(listOf(cardId)).firstOrNull()
        val book = card?.let { db.books().byId(it.bookId) }
        emit(if (card != null && book != null) card.ankiFields(bookLine(book)) else null)
    }
        .flowOn(io)
        .catch { e ->
            Log.e(TAG, "preview query failed", e)
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        private const val TAG = "PreviewViewModel"

        fun factory(cardId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as App
                val c = app.container
                PreviewViewModel(cardId, c.db, c.io)
            }
        }
    }
}

@Composable
fun PreviewPage(
    cardId: Long,
    onBack: () -> Unit,
    vm: PreviewViewModel = viewModel(factory = PreviewViewModel.factory(cardId)),
) {
    val fields by vm.fields.collectAsStateWithLifecycle()
    val colors = inboxColors()
    val background = colors.row
    val text = MaterialTheme.colorScheme.onSurface
    val night = isSystemInDarkTheme()
    var answer by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        containerColor = colors.screen,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.preview_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.card_navigate_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.screen),
            )
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp, vertical = 12.dp)) {
                FilledTonalButton(onClick = { answer = !answer }, modifier = Modifier.fillMaxWidth().height(56.dp), enabled = fields != null) {
                    Text(
                        stringResource(if (answer) R.string.preview_show_question else R.string.preview_show_answer),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 4.dp)
                .clip(RoundedCornerShape(20.dp)).background(background),
        ) {
            val f = fields ?: return@Box
            CardWebView(
                previewHtml(f, answer, night, background.hex(), text.hex()),
                background,
                onTap = { answer = true },
                Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun CardWebView(html: String, background: Color, onTap: () -> Unit, modifier: Modifier) {
    val tap by rememberUpdatedState(onTap)
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = false
                settings.blockNetworkLoads = true
                // WebView never calls its click listener itself, so taps come from its raw touches.
                val taps = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                    override fun onSingleTapUp(e: MotionEvent) = true
                })
                setOnTouchListener { v, e ->
                    if (taps.onTouchEvent(e)) v.performClick()
                    false
                }
                setOnClickListener { tap() }
            }
        },
        modifier = modifier,
        onRelease = { it.destroy() },
        update = { web ->
            web.setBackgroundColor(background.toArgb())
            // update reruns on each recomposition; reloading the same page would flash it.
            if (web.tag != html) {
                web.tag = html
                web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }
        },
    )
}

private fun Color.hex(): String = "#" + (toArgb() and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')

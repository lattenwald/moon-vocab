package org.lattenwald.moonvocab.ui.inbox

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.lattenwald.moonvocab.domain.inbox.Hit
import org.lattenwald.moonvocab.domain.inbox.LongPress
import org.lattenwald.moonvocab.domain.inbox.RowSpan
import org.lattenwald.moonvocab.domain.inbox.autoScrollStep
import org.lattenwald.moonvocab.domain.inbox.hitAt
import org.lattenwald.moonvocab.domain.inbox.longPress

private const val LONG_PRESS_MILLIS = 360L
private val AutoScrollZone = 72.dp
private val AutoScrollMaxStep = 18.dp
private const val GROUP_KEY_PREFIX = "group:"

private class InboxViewConfiguration(base: ViewConfiguration) : ViewConfiguration by base {
    override val longPressTimeoutMillis: Long get() = LONG_PRESS_MILLIS
}

@Composable
internal fun InboxLongPress(content: @Composable () -> Unit) {
    val base = LocalViewConfiguration.current
    val config = remember(base) { InboxViewConfiguration(base) }
    CompositionLocalProvider(LocalViewConfiguration provides config, content = content)
}

internal fun groupKey(bookId: Long): String = GROUP_KEY_PREFIX + bookId

private fun targetOf(key: Any): Hit = when {
    key is Long -> Hit.Card(key)
    key is String && key.startsWith(GROUP_KEY_PREFIX) -> key.removePrefix(GROUP_KEY_PREFIX).toLongOrNull()?.let(Hit::Header) ?: Hit.None
    else -> Hit.None
}

interface SelectHandler {
    val selecting: Boolean
    fun startDrag(id: Long)
    fun dragOver(id: Long)
    fun endDrag()
    fun toggleGroup(bookId: Long)
}

internal class SelectGestures(
    private val listState: LazyListState,
    private val haptics: HapticFeedback,
    private val zone: Float,
    private val maxStep: Float,
    private val handler: SelectHandler,
) {
    private val top get() = listState.layoutInfo.beforeContentPadding
    private val bottom get() = listState.layoutInfo.let { it.viewportSize.height - it.afterContentPadding }

    private fun hit(y: Float, clamp: Boolean): Hit {
        val info = listState.layoutInfo
        val shift = -info.viewportStartOffset
        val rows = info.visibleItemsInfo.map { RowSpan(targetOf(it.key), it.offset + shift, it.offset + shift + it.size) }
        return hitAt(y.toInt(), rows, top, bottom, clamp)
    }

    suspend fun detect(scope: PointerInputScope) = coroutineScope {
        scope.awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val press = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
            val action = longPress(hit(press.position.y, clamp = false), handler.selecting)
            if (action != LongPress.LetGo) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            when (action) {
                is LongPress.Drag -> drag(this@coroutineScope, press, action.id)
                is LongPress.SelectBook -> handler.toggleGroup(action.bookId)
                LongPress.LetGo -> Unit
            }
        }
    }

    private suspend fun AwaitPointerEventScope.drag(scope: CoroutineScope, press: PointerInputChange, id: Long) {
        handler.startDrag(id)
        var lastId = id
        var lastY = press.position.y
        fun over() {
            val over = (hit(lastY, clamp = true) as? Hit.Card)?.id ?: return
            if (over == lastId) return
            lastId = over
            handler.dragOver(over)
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
        val scroller = scope.launch {
            while (true) {
                withFrameNanos {}
                val step = autoScrollStep(lastY, top.toFloat(), bottom.toFloat(), zone, maxStep)
                if (step != 0f) {
                    listState.scrollBy(step)
                    over()
                }
            }
        }
        try {
            // In the main pass the list's scroll sees a move first and takes one past touch slop.
            while (true) {
                val changes = awaitPointerEvent(PointerEventPass.Initial).changes
                changes.forEach { it.consume() }
                val change = changes.firstOrNull { it.id == press.id } ?: break
                if (!change.pressed) break
                lastY = change.position.y
                over()
            }
        } finally {
            scroller.cancel()
            handler.endDrag()
        }
    }
}

@Composable
internal fun rememberSelectGestures(listState: LazyListState, handler: SelectHandler): SelectGestures {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    return remember(listState, haptics, density, handler) {
        with(density) { SelectGestures(listState, haptics, AutoScrollZone.toPx(), AutoScrollMaxStep.toPx(), handler) }
    }
}

internal fun Modifier.selectGestures(gestures: SelectGestures): Modifier =
    pointerInput(gestures) { gestures.detect(this) }

internal fun Modifier.selectAction(label: String, onSelect: () -> Unit): Modifier =
    semantics(mergeDescendants = true) {
        onLongClick(label) {
            onSelect()
            true
        }
    }

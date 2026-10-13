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
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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

    private var swallowUp = false

    // A row's clickable would take the lift after a still long-press as a tap and undo the selection.
    suspend fun guardTap(scope: PointerInputScope) = scope.awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        swallowUp = false
        var event: PointerEvent
        do {
            event = awaitPointerEvent(PointerEventPass.Initial)
        } while (event.changes.any { it.pressed })
        if (swallowUp) event.changes.forEach { it.consume() }
    }

    suspend fun detect(scope: PointerInputScope) = coroutineScope {
        scope.awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val press = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
            when (val action = longPress(hit(press.position.y, clamp = false), handler.selecting)) {
                is LongPress.Drag -> {
                    swallowUp = true
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    handler.startDrag(action.id)
                    var lastId = action.id
                    var lastY = press.position.y
                    fun over() {
                        val id = (hit(lastY, clamp = true) as? Hit.Card)?.id ?: return
                        if (id == lastId) return
                        lastId = id
                        handler.dragOver(id)
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    }
                    val scroller = launch {
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
                is LongPress.SelectBook -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    handler.toggleGroup(action.bookId)
                }
                LongPress.LetGo -> Unit
            }
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
    pointerInput(gestures) { gestures.guardTap(this) }.pointerInput(gestures) { gestures.detect(this) }

internal fun Modifier.selectAction(label: String, onSelect: () -> Unit): Modifier =
    semantics(mergeDescendants = true) {
        onLongClick(label) {
            onSelect()
            true
        }
    }

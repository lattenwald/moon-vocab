package org.lattenwald.moonvocab.ui.inbox

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.lattenwald.moonvocab.domain.inbox.RowSpan
import org.lattenwald.moonvocab.domain.inbox.autoScrollStep
import org.lattenwald.moonvocab.domain.inbox.rowAt

private const val LONG_PRESS_MILLIS = 360L
private val AutoScrollZone = 72.dp
private val AutoScrollMaxStep = 18.dp

private class InboxViewConfiguration(base: ViewConfiguration) : ViewConfiguration by base {
    override val longPressTimeoutMillis: Long get() = LONG_PRESS_MILLIS
}

@Composable
internal fun InboxLongPress(content: @Composable () -> Unit) {
    val base = LocalViewConfiguration.current
    val config = remember(base) { InboxViewConfiguration(base) }
    CompositionLocalProvider(LocalViewConfiguration provides config, content = content)
}

internal class DragSelect(
    private val listState: LazyListState,
    private val haptics: HapticFeedback,
    private val zone: Float,
    private val maxStep: Float,
    private val onStart: (Long) -> Unit,
    private val onOver: (Long) -> Unit,
    private val onEnd: () -> Unit,
) {
    private fun spans(): List<RowSpan> {
        val info = listState.layoutInfo
        val shift = -info.viewportStartOffset
        return info.visibleItemsInfo.map { RowSpan(it.key as? Long, it.offset + shift, it.offset + shift + it.size) }
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
        var dragging = false
        var lastId: Long? = null
        var lastY = 0f
        var scroller: Job? = null

        fun over(y: Float) {
            val id = rowAt(y.toInt(), spans()) ?: return
            if (id == lastId) return
            lastId = id
            onOver(id)
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }

        fun stop() {
            scroller?.cancel()
            scroller = null
            if (dragging) onEnd()
            dragging = false
            lastId = null
        }

        scope.detectDragGesturesAfterLongPress(
            onDragStart = { offset ->
                val id = rowAt(offset.y.toInt(), spans()) ?: return@detectDragGesturesAfterLongPress
                swallowUp = true
                dragging = true
                lastId = id
                lastY = offset.y
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onStart(id)
                scroller = launch {
                    while (true) {
                        withFrameNanos {}
                        val info = listState.layoutInfo
                        val top = info.beforeContentPadding.toFloat()
                        val bottom = (info.viewportSize.height - info.afterContentPadding).toFloat()
                        val step = autoScrollStep(lastY, top, bottom, zone, maxStep)
                        if (step != 0f) {
                            listState.scrollBy(step)
                            over(lastY)
                        }
                    }
                }
            },
            onDragEnd = { stop() },
            onDragCancel = { stop() },
            onDrag = { change, _ ->
                if (dragging) {
                    lastY = change.position.y
                    over(lastY)
                }
            },
        )
    }
}

@Composable
internal fun rememberDragSelect(listState: LazyListState, onStart: (Long) -> Unit, onOver: (Long) -> Unit, onEnd: () -> Unit): DragSelect {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val start by rememberUpdatedState(onStart)
    val over by rememberUpdatedState(onOver)
    val end by rememberUpdatedState(onEnd)
    return remember(listState, haptics, density) {
        with(density) {
            DragSelect(listState, haptics, AutoScrollZone.toPx(), AutoScrollMaxStep.toPx(), { start(it) }, { over(it) }, { end() })
        }
    }
}

internal fun Modifier.dragSelect(drag: DragSelect): Modifier =
    pointerInput(drag) { drag.guardTap(this) }.pointerInput(drag) { drag.detect(this) }

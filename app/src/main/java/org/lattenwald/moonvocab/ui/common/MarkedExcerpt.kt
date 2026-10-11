package org.lattenwald.moonvocab.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import org.lattenwald.moonvocab.domain.card.MarkedText
import org.lattenwald.moonvocab.ui.theme.LocalMarkColor

private val WaveOffset = 3.dp
private val WaveLength = 6.dp
private val WaveAmplitude = 1.25.dp
private val WaveStroke = 1.5.dp

@Composable
fun MarkedExcerpt(
    text: MarkedText,
    modifier: Modifier = Modifier,
    style: TextStyle,
    maxLines: Int = Int.MAX_VALUE,
) {
    val colors = MaterialTheme.colorScheme
    val markColor = LocalMarkColor.current
    val mark = text.mark?.let { maxOf(it.first, 0) until minOf(it.last + 1, text.text.length) }
        ?.takeUnless { it.isEmpty() }
    val annotated = remember(text.text, mark, colors.onSurface) {
        buildAnnotatedString {
            append(text.text)
            if (mark != null) addStyle(SpanStyle(color = colors.onSurface), mark.first, mark.last + 1)
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val wave = if (mark == null) Modifier else Modifier.drawWithCache {
        val path = layout?.let { wavePath(it, mark.first, mark.last + 1, this) }
        val stroke = Stroke(width = WaveStroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        onDrawBehind { if (path != null) drawPath(path, markColor, style = stroke) }
    }
    Text(
        text = annotated,
        modifier = modifier.then(wave),
        color = colors.onSurfaceVariant,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout = it },
    )
}

private fun wavePath(layout: TextLayoutResult, start: Int, end: Int, density: Density): Path = with(density) {
    val path = Path()
    val offset = WaveOffset.toPx()
    val length = WaveLength.toPx()
    val amplitude = WaveAmplitude.toPx()
    val step = length / 12
    for (line in layout.getLineForOffset(start) until layout.lineCount) {
        val lineStart = layout.getLineStart(line)
        if (lineStart >= end) break
        val s = maxOf(start, lineStart)
        val e = minOf(end, layout.getLineEnd(line, visibleEnd = true)) // excludes ellipsized text
        if (s >= e) continue
        // getHorizontalPosition at a line-break offset reports the next line's start
        val first = layout.getBoundingBox(s)
        val last = layout.getBoundingBox(e - 1)
        val x0 = minOf(first.left, last.left)
        val x1 = maxOf(first.right, last.right)
        val y = layout.getLineBaseline(line) + offset
        path.moveTo(x0, y)
        var x = x0
        while (x < x1) {
            x = minOf(x + step, x1)
            path.lineTo(x, y + amplitude * sin(2 * PI * (x - x0) / length).toFloat())
        }
    }
    path
}

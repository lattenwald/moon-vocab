package org.lattenwald.moonvocab.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import org.lattenwald.moonvocab.ui.theme.BookFontFamily
import org.lattenwald.moonvocab.ui.theme.IpaFontFamily

fun slashed(transcription: String): String = "/$transcription/"

@Composable
fun WordNote(transcription: String, grammar: String, modifier: Modifier = Modifier) {
    if (transcription.isBlank() && grammar.isBlank()) return
    val text = buildAnnotatedString {
        if (transcription.isNotBlank()) withStyle(SpanStyle(fontFamily = IpaFontFamily)) { append(slashed(transcription)) }
        if (transcription.isNotBlank() && grammar.isNotBlank()) append("  ")
        if (grammar.isNotBlank()) withStyle(SpanStyle(fontFamily = BookFontFamily, fontStyle = FontStyle.Italic)) { append(grammar) }
    }
    Text(
        text,
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 14.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

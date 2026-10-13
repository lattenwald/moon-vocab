package org.lattenwald.moonvocab.ui.card

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.lattenwald.moonvocab.R
import org.lattenwald.moonvocab.data.db.CardEntity
import org.lattenwald.moonvocab.domain.card.CardField
import org.lattenwald.moonvocab.domain.card.CardFields
import org.lattenwald.moonvocab.domain.card.markedText
import org.lattenwald.moonvocab.ui.common.MarkedExcerpt
import org.lattenwald.moonvocab.ui.common.slashed
import org.lattenwald.moonvocab.ui.inbox.RowFlag
import org.lattenwald.moonvocab.ui.theme.BookFontFamily
import org.lattenwald.moonvocab.ui.theme.IpaFontFamily

private val FaceShape = RoundedCornerShape(28.dp)

internal val CardField.label: Int
    get() = when (this) {
        CardField.WORD -> R.string.card_field_word
        CardField.TRANSCRIPTION -> R.string.card_field_transcription
        CardField.GRAMMAR -> R.string.card_field_grammar
        CardField.EXCERPT -> R.string.card_field_excerpt
        CardField.TRANSLATION -> R.string.card_field_translation
        CardField.MEANING -> R.string.card_field_meaning
        CardField.SENTENCE -> R.string.card_field_sentence
    }

@Composable
private fun Face(color: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(color, FaceShape).padding(start = 24.dp, top = 26.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
internal fun FrontFace(fields: CardFields, color: Color) {
    val colors = MaterialTheme.colorScheme
    Face(color) {
        Text(fields.word, color = colors.onSurface, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.4).sp)
        if (fields.transcription.isNotBlank()) {
            Text(slashed(fields.transcription), color = colors.onSurfaceVariant, fontSize = 16.sp, fontFamily = IpaFontFamily)
        }
        if (fields.grammar.isNotBlank()) {
            Text(fields.grammar, color = colors.onSurfaceVariant, fontSize = 15.sp, fontFamily = BookFontFamily, fontStyle = FontStyle.Italic)
        }
        if (fields.excerptHtml.isNotBlank()) {
            MarkedExcerpt(
                markedText(fields.excerptHtml),
                modifier = Modifier.padding(top = 10.dp),
                style = TextStyle(fontFamily = BookFontFamily, fontSize = 18.sp, lineHeight = 28.sp),
            )
        }
    }
}

@Composable
internal fun BackFace(fields: CardFields, bookLine: String?, color: Color) {
    val colors = MaterialTheme.colorScheme
    Face(color) {
        if (fields.translation.isBlank()) {
            Text(stringResource(R.string.card_no_translation), color = colors.error, fontSize = 24.sp, lineHeight = 31.sp, fontWeight = FontWeight.Medium)
        } else {
            Text(fields.translation, color = colors.onSurface, fontSize = 24.sp, lineHeight = 31.sp, fontWeight = FontWeight.Medium)
        }
        if (fields.meaning.isNotBlank()) {
            Text(fields.meaning, color = colors.onSurfaceVariant, fontSize = 15.sp, lineHeight = 22.sp)
        }
        HorizontalDivider(Modifier.padding(top = 2.dp), color = colors.outlineVariant)
        val context = fields.sentenceHtml.ifBlank { fields.excerptHtml }
        if (context.isNotBlank()) {
            MarkedExcerpt(markedText(context), style = TextStyle(fontFamily = BookFontFamily, fontSize = 16.sp, lineHeight = 26.sp))
        }
        BookLine(bookLine)
    }
}

@Composable
internal fun FrontForm(form: CardFields, invalid: Set<CardField>, color: Color, readOnly: Boolean, onEdit: (CardField, String) -> Unit) {
    Face(color) {
        FormField(CardField.WORD, form, invalid, readOnly, onEdit, singleLine = true)
        FormField(CardField.TRANSCRIPTION, form, invalid, readOnly, onEdit, singleLine = true)
        FormField(CardField.GRAMMAR, form, invalid, readOnly, onEdit, singleLine = true)
        FormField(CardField.EXCERPT, form, invalid, readOnly, onEdit)
    }
}

@Composable
internal fun BackForm(form: CardFields, invalid: Set<CardField>, bookLine: String?, color: Color, readOnly: Boolean, onEdit: (CardField, String) -> Unit) {
    Face(color) {
        FormField(CardField.TRANSLATION, form, invalid, readOnly, onEdit)
        FormField(CardField.MEANING, form, invalid, readOnly, onEdit)
        FormField(CardField.SENTENCE, form, invalid, readOnly, onEdit)
        BookLine(bookLine)
    }
}

@Composable
private fun FormField(
    field: CardField,
    form: CardFields,
    invalid: Set<CardField>,
    readOnly: Boolean,
    onEdit: (CardField, String) -> Unit,
    singleLine: Boolean = false,
) {
    val bracketed = field == CardField.EXCERPT || field == CardField.SENTENCE
    val error = field in invalid
    OutlinedTextField(
        value = form[field],
        onValueChange = { onEdit(field, it) },
        modifier = Modifier.fillMaxWidth(),
        readOnly = readOnly,
        textStyle = when {
            bracketed -> LocalTextStyle.current.copy(fontFamily = BookFontFamily)
            field == CardField.TRANSCRIPTION -> LocalTextStyle.current.copy(fontFamily = IpaFontFamily)
            else -> LocalTextStyle.current
        },
        label = { Text(stringResource(field.label)) },
        supportingText = when {
            bracketed -> {
                { Text(stringResource(if (error) R.string.card_brackets_invalid else R.string.card_brackets_help)) }
            }
            error -> {
                { Text(stringResource(R.string.card_field_required)) }
            }
            else -> null
        },
        isError = error,
        singleLine = singleLine,
    )
}

@Composable
private fun BookLine(bookLine: String?) {
    if (bookLine == null) return
    Text(bookLine, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
}

@Composable
internal fun CardLabels(card: CardEntity, widened: Boolean) {
    Column(Modifier.padding(horizontal = 8.dp)) {
        if (widened) RowFlag(R.drawable.ic_add, stringResource(R.string.inbox_label_phrase))
        if (card.invented) RowFlag(R.drawable.ic_edit, stringResource(R.string.inbox_label_invented))
        card.elsewhereDeck?.let { deck ->
            RowFlag(
                R.drawable.ic_school,
                if (deck.isEmpty()) stringResource(R.string.inbox_label_elsewhere_unknown) else stringResource(R.string.inbox_label_elsewhere, deck),
            )
        }
    }
}

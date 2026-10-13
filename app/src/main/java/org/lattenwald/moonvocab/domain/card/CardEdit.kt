package org.lattenwald.moonvocab.domain.card

import org.lattenwald.moonvocab.data.anki.NoteTypeSpec
import org.lattenwald.moonvocab.data.anki.ankiToPlain
import org.lattenwald.moonvocab.data.db.CardEntity

enum class CardField(val ankiName: String) {
    WORD("Word"), TRANSCRIPTION("Transcription"), GRAMMAR("Grammar"), EXCERPT("Excerpt"), TRANSLATION("Translation"), MEANING("Meaning"), SENTENCE("Sentence")
}

private val CardField.isHtml: Boolean get() = ankiName in NoteTypeSpec.HTML_FIELDS

// Stored form: excerpt and sentence are HTML with a mark span; the others are plain text.
data class CardFields(
    val word: String,
    val transcription: String,
    val grammar: String,
    val excerptHtml: String,
    val translation: String,
    val meaning: String,
    val sentenceHtml: String,
) {
    operator fun get(f: CardField): String = when (f) {
        CardField.WORD -> word
        CardField.TRANSCRIPTION -> transcription
        CardField.GRAMMAR -> grammar
        CardField.EXCERPT -> excerptHtml
        CardField.TRANSLATION -> translation
        CardField.MEANING -> meaning
        CardField.SENTENCE -> sentenceHtml
    }
}

fun CardFields.with(f: CardField, value: String): CardFields = when (f) {
    CardField.WORD -> copy(word = value)
    CardField.TRANSCRIPTION -> copy(transcription = value)
    CardField.GRAMMAR -> copy(grammar = value)
    CardField.EXCERPT -> copy(excerptHtml = value)
    CardField.TRANSLATION -> copy(translation = value)
    CardField.MEANING -> copy(meaning = value)
    CardField.SENTENCE -> copy(sentenceHtml = value)
}

data class CardExtras(val lemma: String, val widened: Boolean)

fun CardEntity.cardFields(): CardFields = CardFields(word, transcription, grammar, excerptHtml, translation, meaning, sentenceHtml)

fun CardEntity.withFields(f: CardFields, extras: CardExtras? = null): CardEntity = copy(
    word = f.word,
    transcription = f.transcription,
    grammar = f.grammar,
    excerptHtml = f.excerptHtml,
    translation = f.translation,
    meaning = f.meaning,
    sentenceHtml = f.sentenceHtml,
    lemma = extras?.lemma ?: lemma,
    widened = extras?.widened ?: widened,
)

// Edit form: same shape, excerpt and sentence as text with the focus in [brackets].
fun CardFields.toForm(): CardFields = copy(excerptHtml = bracketed(excerptHtml), sentenceHtml = bracketed(sentenceHtml))

private fun bracketed(html: String): String {
    val (text, mark) = markedText(html)
    if (mark == null) return text
    return text.substring(0, mark.first) + "[" + text.substring(mark.first, mark.last + 1) + "]" + text.substring(mark.last + 1)
}

sealed interface FormResult {
    data class Ok(val fields: CardFields) : FormResult
    data class Invalid(val fields: Set<CardField>) : FormResult
}

private val REQUIRED = setOf(CardField.WORD, CardField.TRANSLATION)

fun parseForm(form: CardFields, original: CardFields): FormResult {
    val shown = original.toForm()
    val invalid = mutableSetOf<CardField>()
    var out = original
    for (f in CardField.entries) {
        val value = form[f]
        if (value == shown[f]) continue
        val parsed = parseField(f, value)
        if (parsed == null) invalid += f else out = out.with(f, parsed)
    }
    REQUIRED.filterTo(invalid) { out[it].isBlank() }
    return if (invalid.isEmpty()) FormResult.Ok(out) else FormResult.Invalid(invalid)
}

private fun parseField(f: CardField, value: String): String? = when (f) {
    CardField.EXCERPT -> markFromBrackets(value.trim())
    CardField.SENTENCE -> if (value.isBlank()) "" else markFromBrackets(value.trim())
    CardField.TRANSCRIPTION -> cleanTranscription(value)
    else -> value.trim()
}

// An unedited field takes AnkiDroid's text too, or the next Save would write the stale value back.
fun mergeAfterConflict(form: CardFields, oldBaseline: CardFields, fresh: CardFields, conflicts: Set<CardField>): CardFields {
    val shown = oldBaseline.toForm()
    val freshForm = fresh.toForm()
    return CardField.entries.fold(form) { acc, f ->
        val edited = form[f] != shown[f] && parseField(f, form[f]) != oldBaseline[f]
        if (f in conflicts || !edited) acc.with(f, freshForm[f]) else acc
    }
}

private fun markFromBrackets(s: String): String? {
    val (open, close) = singleBracketPair(s) ?: return null
    return markHtml(s.removeRange(close, close + 1).removeRange(open, open + 1), open, close - 1)
}

sealed interface SavePlan {
    /** [anki]: AnkiDroid field name → HTML value, only the fields to write. [result]: what the card stores afterwards. */
    data class Write(val anki: Map<String, String>, val result: CardFields) : SavePlan
    data class Conflict(val fields: Set<CardField>, val fresh: CardFields) : SavePlan
}

fun planSave(baseline: CardFields, edited: CardFields, note: Map<String, String>): SavePlan {
    val fresh = CardField.entries.fold(baseline) { acc, f ->
        val value = note[f.ankiName] ?: return@fold acc
        acc.with(f, if (f.isHtml) value else ankiToPlain(value))
    }
    val changed = CardField.entries.filter { edited[it] != baseline[it] }
    val conflicting = changed.filterTo(mutableSetOf()) { fresh[it] != baseline[it] }
    if (conflicting.isNotEmpty()) return SavePlan.Conflict(conflicting, fresh)
    return SavePlan.Write(
        anki = changed.associate { it.ankiName to if (it.isHtml) edited[it] else escapeHtml(edited[it]) },
        result = changed.fold(fresh) { acc, f -> acc.with(f, edited[f]) },
    )
}

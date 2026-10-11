package org.lattenwald.moonvocab.domain.card

import kotlin.math.abs
import org.lattenwald.moonvocab.domain.text.NormalizedText
import org.lattenwald.moonvocab.domain.text.SentenceResult
import org.lattenwald.moonvocab.domain.text.normalize
import org.lattenwald.moonvocab.domain.text.normalizeForMatch

data class CardDraft(
    val word: String,
    val lemma: String,
    val grammar: String,
    val excerptHtml: String,
    val translation: String,
    val meaning: String,
    val sentenceHtml: String,
    val sourceNote: String,
    val widened: Boolean,
    val invented: Boolean,
    val sourceLanguage: String?,
)

sealed interface DraftResult {
    data class Ok(val draft: CardDraft) : DraftResult
    data class Invalid(val reason: String) : DraftResult
}

fun buildDraft(sentence: SentenceResult, t: Translation, maxWords: Int): DraftResult = when (sentence) {
    is SentenceResult.Found -> fromBook(sentence, t, maxWords)
    SentenceResult.NotFound -> fromInventedExample(t)
}

private fun fromBook(found: SentenceResult.Found, t: Translation, maxWords: Int): DraftResult {
    val norm = normalize(found.sentence)
    val lead = norm.text.length - norm.text.trimStart(' ').length
    val s = norm.text.trim(' ')
    val mark = normalizedIndex(norm, found.markStart) - lead
    val focus = normalizeForMatch(t.focus).trim()
    if (focus.isEmpty()) return DraftResult.Invalid("focus not in sentence")

    var start = -1
    for (i in 0..s.length - focus.length) {
        if (s.regionMatches(i, focus, 0, focus.length, ignoreCase = true) && (start < 0 || abs(i - mark) < abs(start - mark))) start = i
    }
    if (start < 0) return DraftResult.Invalid("focus not in sentence")
    val end = start + focus.length

    val excerpt = if (wordCount(s) <= maxWords) {
        Excerpt(s, start, end)
    } else {
        acceptModelExcerpt(t.excerpt, s, focus, start, maxWords) ?: cutExcerpt(s, start, end, maxWords)
    }
    return DraftResult.Ok(
        draft(t, markHtml(excerpt.text, excerpt.focusStart, excerpt.focusEnd), markHtml(s, start, end), sourceNote = "", invented = false),
    )
}

private fun fromInventedExample(t: Translation): DraftResult {
    val example = t.inventedExample?.let { normalizeForMatch(it).trim() } ?: return DraftResult.Invalid("no example")
    val (open, close) = singleBracketPair(example) ?: return DraftResult.Invalid("no example")
    val text = example.removeRange(close, close + 1).removeRange(open, open + 1)
    return DraftResult.Ok(draft(t, markHtml(text, open, close - 1), sentenceHtml = "", sourceNote = t.sourceNote.orEmpty(), invented = true))
}

private fun draft(t: Translation, excerptHtml: String, sentenceHtml: String, sourceNote: String, invented: Boolean) = CardDraft(
    word = t.headword,
    lemma = t.lemma,
    grammar = t.grammar,
    excerptHtml = excerptHtml,
    translation = t.translation,
    meaning = t.meaning,
    sentenceHtml = sentenceHtml,
    sourceNote = sourceNote,
    widened = t.widened,
    invented = invented,
    sourceLanguage = t.sourceLanguage,
)

private fun normalizedIndex(norm: NormalizedText, original: Int): Int =
    (0..norm.text.length).firstOrNull { norm.originalIndex(it) >= original } ?: norm.text.length

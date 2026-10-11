package org.lattenwald.moonvocab.domain.card

import java.util.Locale
import org.lattenwald.moonvocab.domain.text.normalizeForMatch

private val whitespace = Regex("\\s+")

fun senseKey(lemma: String): String = normalizeForMatch(lemma).trim().replace(whitespace, " ").lowercase(Locale.ROOT)

data class SenseCandidate(val ref: String, val word: String, val translation: String, val meaning: String)

data class SenseQuestion(
    val id: String, val word: String, val focus: String, val sentence: String?,
    val translation: String, val meaning: String, val candidates: List<SenseCandidate>,
)

/** Returns question id → the ref of the candidate with the same sense. No entry means no match. */
fun interface SenseJudge { suspend fun judge(questions: List<SenseQuestion>): Map<String, String> }

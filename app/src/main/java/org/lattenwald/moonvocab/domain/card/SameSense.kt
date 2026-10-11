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

/** A pending card of the current batch, at [index] in the batch's card list. */
data class NewCard(
    val index: Int, val language: String?, val lemma: String, val word: String, val focus: String,
    val sentence: String?, val translation: String, val meaning: String,
)

data class KnownCard(
    val id: Long, val status: CardStatus, val language: String?, val lemma: String,
    val word: String, val translation: String, val meaning: String,
)

sealed interface Merge {
    data object New : Merge
    data class IntoKnown(val cardId: Long, val status: CardStatus) : Merge
    /** Into the batch card at [index], whose own result is New. */
    data class IntoBatch(val index: Int) : Merge
}

private const val MAX_SENSE_CANDIDATES = 8
private val liveStatuses = setOf(CardStatus.SENT, CardStatus.PENDING)

fun senseQuestions(batch: List<NewCard>, known: List<KnownCard>): List<SenseQuestion> {
    val knownByKey = known
        .filter { it.status != CardStatus.FAILED && it.lemma.isNotBlank() }
        .sortedWith(compareBy({ it.status !in liveStatuses }, { it.id }))
        .groupBy { it.language to senseKey(it.lemma) }
    val ordered = batch.sortedBy { it.index }
    val batchKeys = ordered.map { it.language to senseKey(it.lemma) }
    return ordered.mapIndexedNotNull { position, card ->
        val key = batchKeys[position]
        val knownCandidates = knownByKey[key].orEmpty().map { SenseCandidate("k${it.id}", it.word, it.translation, it.meaning) }
        val batchCandidates = ordered.take(position).filterIndexed { i, _ -> batchKeys[i] == key }
            .map { SenseCandidate("b${it.index}", it.word, it.translation, it.meaning) }
        val candidates = (knownCandidates + batchCandidates).take(MAX_SENSE_CANDIDATES)
        if (candidates.isEmpty()) null
        else SenseQuestion(card.index.toString(), card.word, card.focus, card.sentence, card.translation, card.meaning, candidates)
    }
}

/** A ref naming no known card or earlier batch card counts as no answer. */
fun resolveMerges(batch: List<NewCard>, answers: Map<String, String>, known: List<KnownCard>): Map<Int, Merge> {
    val statusById = known.associate { it.id to it.status }
    val result = mutableMapOf<Int, Merge>()
    for (card in batch.sortedBy { it.index }) {
        val ref = answers[card.index.toString()]
        val target = ref?.drop(1)
        result[card.index] = when (ref?.firstOrNull()) {
            'k' -> target?.toLongOrNull()?.let { id -> statusById[id]?.let { Merge.IntoKnown(id, it) } }
            'b' -> target?.toIntOrNull()?.let { j -> result[j]?.let { if (it == Merge.New) Merge.IntoBatch(j) else it } }
            else -> null
        } ?: Merge.New
    }
    return result
}

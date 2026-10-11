package org.lattenwald.moonvocab.domain.import

import android.util.Log
import kotlin.coroutines.cancellation.CancellationException
import org.lattenwald.moonvocab.data.anki.AnkiAvailability
import org.lattenwald.moonvocab.data.anki.AnkiGateway
import org.lattenwald.moonvocab.data.anki.ankiToPlain
import org.lattenwald.moonvocab.data.anki.storedFields
import org.lattenwald.moonvocab.data.db.AppDatabase
import org.lattenwald.moonvocab.data.db.CardEntity
import org.lattenwald.moonvocab.domain.card.CardEvent
import org.lattenwald.moonvocab.domain.card.CardStatus
import org.lattenwald.moonvocab.domain.card.ReconcileAction
import org.lattenwald.moonvocab.domain.card.SentCard
import org.lattenwald.moonvocab.domain.card.next
import org.lattenwald.moonvocab.domain.card.reconcile

sealed interface CheckResult {
    data class Changed(val count: Int) : CheckResult
    data class Unavailable(val availability: AnkiAvailability) : CheckResult
}

class AnkiCheck(private val db: AppDatabase, private val anki: AnkiGateway) {
    suspend fun run(): Int = try {
        (check() as? CheckResult.Changed)?.count ?: 0
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "AnkiDroid check failed", e)
        0
    }

    suspend fun check(): CheckResult {
        val availability = anki.availability()
        return if (availability == AnkiAvailability.READY) CheckResult.Changed(reconcileSent()) else CheckResult.Unavailable(availability)
    }

    private suspend fun reconcileSent(): Int {
        val sent = db.cards().byStatus(CardStatus.SENT).filter { it.ankiNoteId != null }
        if (sent.isEmpty()) return 0
        val sentCards = sent.map { SentCard(it.id, checkNotNull(it.ankiNoteId), it.storedFields()) }
        val byId = sent.associateBy { it.id }
        val actions = reconcile(sentCards, anki.read(sentCards.map { it.noteId }))
        for (action in actions) {
            when (action) {
                is ReconcileAction.Missing -> byId.getValue(action.cardId).let {
                    db.cards().update(it.copy(status = next(it.status, CardEvent.NOTE_MISSING)))
                }
                is ReconcileAction.Refresh -> db.cards().update(byId.getValue(action.cardId).refreshed(action.fields, action.mod))
            }
        }
        return actions.size
    }

    private fun CardEntity.refreshed(fields: Map<String, String>, mod: Long) = copy(
        word = fields["Word"]?.let(::ankiToPlain) ?: word,
        grammar = fields["Grammar"]?.let(::ankiToPlain) ?: grammar,
        excerptHtml = fields["Excerpt"] ?: excerptHtml,
        translation = fields["Translation"]?.let(::ankiToPlain) ?: translation,
        meaning = fields["Meaning"]?.let(::ankiToPlain) ?: meaning,
        sentenceHtml = fields["Sentence"] ?: sentenceHtml,
        sourceNote = fields["Source"]?.let(::ankiToPlain) ?: sourceNote,
        ankiMod = mod,
    )

    private companion object {
        const val TAG = "AnkiCheck"
    }
}

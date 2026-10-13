package org.lattenwald.moonvocab.domain.import

import android.util.Log
import kotlin.coroutines.cancellation.CancellationException
import org.lattenwald.moonvocab.data.anki.AnkiAvailability
import org.lattenwald.moonvocab.data.anki.AnkiGateway
import org.lattenwald.moonvocab.data.anki.storedFields
import org.lattenwald.moonvocab.data.db.AppDatabase
import org.lattenwald.moonvocab.data.settings.Settings
import org.lattenwald.moonvocab.domain.card.CardStatus
import org.lattenwald.moonvocab.domain.card.CardStore
import org.lattenwald.moonvocab.domain.card.CardWrites
import org.lattenwald.moonvocab.domain.card.ReconcileAction
import org.lattenwald.moonvocab.domain.card.SentCard
import org.lattenwald.moonvocab.domain.card.noteMissing
import org.lattenwald.moonvocab.domain.card.reconcile
import org.lattenwald.moonvocab.domain.card.refreshed

sealed interface CheckResult {
    data class Changed(val count: Int) : CheckResult
    data class Unavailable(val availability: AnkiAvailability) : CheckResult
}

class AnkiCheck(
    private val db: AppDatabase,
    private val anki: AnkiGateway,
    private val store: CardStore,
    private val settings: Settings,
    private val clock: () -> Long,
) {
    suspend fun runIfDue(): Boolean {
        if (clock() - settings.current().lastCheckAt < START_INTERVAL_MS) return false
        run()
        return true
    }

    suspend fun run(): Int = try {
        (check() as? CheckResult.Changed)?.count ?: 0
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "AnkiDroid check failed", e)
        0
    }

    suspend fun check(): CheckResult = store.locked { writes ->
        val availability = anki.availability()
        if (availability != AnkiAvailability.READY) return@locked CheckResult.Unavailable(availability)
        val changed = writes.reconcileSent()
        settings.update { it.copy(lastCheckAt = clock()) }
        CheckResult.Changed(changed)
    }

    private suspend fun CardWrites.reconcileSent(): Int {
        val sent = db.cards().byStatus(CardStatus.SENT).filter { it.ankiNoteId != null }
        if (sent.isEmpty()) return 0
        val sentCards = sent.map { SentCard(it.id, checkNotNull(it.ankiNoteId), it.storedFields()) }
        val byId = sent.associateBy { it.id }
        val actions = reconcile(sentCards, anki.read(sentCards.map { it.noteId }))
        for (action in actions) {
            when (action) {
                is ReconcileAction.Missing -> save(byId.getValue(action.cardId).noteMissing())
                is ReconcileAction.Refresh -> save(byId.getValue(action.cardId).refreshed(action.fields, action.mod))
            }
        }
        return actions.size
    }

    private companion object {
        const val TAG = "AnkiCheck"
        const val START_INTERVAL_MS = 10 * 60_000L
    }
}

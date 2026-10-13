package org.lattenwald.moonvocab.domain.import

import android.util.Log
import kotlin.coroutines.cancellation.CancellationException
import org.lattenwald.moonvocab.data.settings.Settings
import org.lattenwald.moonvocab.domain.card.CheckResult
import org.lattenwald.moonvocab.domain.card.NoteSync

class AnkiCheck(
    private val sync: NoteSync,
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

    suspend fun check(): CheckResult = sync.checkAll().also {
        if (it is CheckResult.Changed) settings.update { s -> s.copy(lastCheckAt = clock()) }
    }

    private companion object {
        const val TAG = "AnkiCheck"
        const val START_INTERVAL_MS = 10 * 60_000L
    }
}

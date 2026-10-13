package org.lattenwald.moonvocab.data.anki

import android.util.Log
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class AnkiStatus(private val gateway: AnkiGateway, private val io: CoroutineDispatcher) : AnkiGateway by gateway {
    private val known = MutableStateFlow<AnkiAvailability?>(null)
    val state: StateFlow<AnkiAvailability?> = known.asStateFlow()

    override fun availability(): AnkiAvailability = gateway.availability().also { known.value = it }

    suspend fun refresh() {
        try {
            withContext(io) { availability() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AnkiDroid availability check failed", e)
        }
    }

    private companion object {
        const val TAG = "AnkiStatus"
    }
}

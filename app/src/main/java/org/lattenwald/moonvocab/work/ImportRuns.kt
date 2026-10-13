package org.lattenwald.moonvocab.work

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.lattenwald.moonvocab.domain.import.ImportProgress
import org.lattenwald.moonvocab.domain.import.ImportRequest
import org.lattenwald.moonvocab.domain.import.ImportSummary

val Context.importResultStore: DataStore<Preferences> by preferencesDataStore(name = "import_result")

data class ImportState(
    val running: Boolean = false,
    val progress: ImportProgress? = null,
    val summary: ImportSummary? = null,
    val failure: String? = null,
) {
    val canRetryFailed: Boolean get() = summary?.let { it.failed > 0 && it.stopped == null } == true
}

sealed interface RunKind {
    data class Import(val request: ImportRequest) : RunKind
    data object RetryFailed : RunKind
}

enum class Start { STARTED, ALREADY_RUNNING }

data class ActiveRun(val progress: ImportProgress?)

interface RunQueue {
    val active: Flow<ActiveRun?>

    suspend fun enqueue(kind: RunKind): Boolean
}

class ImportRuns(private val queue: RunQueue, private val store: DataStore<Preferences>) {
    private val result: Flow<StoredResult?> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[RESULT]?.let(::decode) }

    val state: Flow<ImportState> = combine(queue.active, result) { run, r ->
        if (run != null) ImportState(running = true, progress = run.progress) else ImportState(summary = r?.summary, failure = r?.failure)
    }

    suspend fun start(request: ImportRequest): Start = launch(RunKind.Import(request))

    /** Imports the marks that have no card yet, from the backup already read. */
    suspend fun resume(): Start = start(ImportRequest(backup = null, styles = emptySet(), onlyBookFileKey = null, sendToAnki = false))

    suspend fun retryFailed(): Start = launch(RunKind.RetryFailed)

    suspend fun dismiss() {
        store.edit { it.remove(RESULT) }
    }

    suspend fun finished(summary: ImportSummary) = keep(StoredResult(summary = summary))

    suspend fun failed(message: String) = keep(StoredResult(failure = message))

    private suspend fun launch(kind: RunKind): Start {
        dismiss()
        return if (queue.enqueue(kind)) Start.STARTED else Start.ALREADY_RUNNING
    }

    private suspend fun keep(r: StoredResult) {
        store.edit { it[RESULT] = json.encodeToString(r) }
    }

    private fun decode(text: String): StoredResult? = try {
        json.decodeFromString<StoredResult>(text)
    } catch (e: SerializationException) {
        Log.w(TAG, "stored import result unreadable", e)
        null
    }

    @Serializable
    private data class StoredResult(val summary: ImportSummary? = null, val failure: String? = null)

    private companion object {
        const val TAG = "ImportRuns"
        val RESULT = stringPreferencesKey("result")
        val json = Json { ignoreUnknownKeys = true }
    }
}

package org.lattenwald.moonvocab.work

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.lattenwald.moonvocab.data.llm.LlmException
import org.lattenwald.moonvocab.domain.import.ImportProgress
import org.lattenwald.moonvocab.domain.import.ImportSummary
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImportRunsTest {
    @get:Rule val folder = TemporaryFolder()

    private class FakeQueue : RunQueue {
        override val active = MutableStateFlow<ActiveRun?>(null)

        override suspend fun enqueue(kind: RunKind): Boolean {
            if (active.value != null) return false
            active.value = ActiveRun(null)
            return true
        }
    }

    private val queue = FakeQueue()
    private lateinit var store: DataStore<Preferences>

    private fun TestScope.runs(): ImportRuns {
        store = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "import_result.preferences_pb") }
        return ImportRuns(queue, store)
    }

    private val summary = ImportSummary(3, 1, 4, 5, 6, 2, "truncated", LlmException.Stop.Kind.CREDITS, "AnkiDroid refused")

    @Test fun aResultStaysWithoutAnyWorkLeftUntilDismissed() = runTest {
        val runs = runs()
        runs.retryFailed()
        queue.active.value = ActiveRun(ImportProgress(2, 5, "Diaspora"))
        assertEquals(ImportState(running = true, progress = ImportProgress(2, 5, "Diaspora")), runs.state.first())

        runs.finished(summary)
        queue.active.value = null
        assertEquals(ImportState(summary = summary), runs.state.first())

        runs.dismiss()
        assertEquals(ImportState(), runs.state.first())
    }

    @Test fun aRunHidesTheLastResultAndItsFailureReplacesIt() = runTest {
        val runs = runs()
        runs.finished(summary)
        runs.resume()
        assertEquals(ImportState(running = true), runs.state.first())

        runs.failed("disk full")
        queue.active.value = null
        assertEquals(ImportState(failure = "disk full"), runs.state.first())
    }

    @Test fun aRunThatEndsWithoutAResultLeavesNoneBehind() = runTest {
        val runs = runs()
        runs.finished(summary)
        runs.resume()
        queue.active.value = null
        assertEquals(ImportState(), runs.state.first())
    }

    @Test fun aStoredResultWithAStopReasonThisVersionLacksShowsNothing() = runTest {
        val runs = runs()
        val stored = """{"summary":{"newCards":1,"failed":0,"alreadyCard":0,"skippedDiscarded":0,"senseFailed":0,"sent":0,""" +
            """"incompleteBackup":null,"stopped":"GONE","sendError":null}}"""
        store.edit { it[stringPreferencesKey("result")] = stored }
        assertEquals(ImportState(), runs.state.first())
    }

    @Test fun onlyFailedCardsFromARunTheServiceDidNotStopCanBeRetried() {
        assertTrue(ImportState(summary = summary.copy(stopped = null)).canRetryFailed)
        assertFalse(ImportState(summary = summary).canRetryFailed)
        assertFalse(ImportState(summary = summary.copy(failed = 0, stopped = null)).canRetryFailed)
        assertFalse(ImportState(failure = "disk full").canRetryFailed)
    }
}

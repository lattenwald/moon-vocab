package org.lattenwald.moonvocab.domain.import

import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lattenwald.moonvocab.data.anki.AnkiAvailability
import org.lattenwald.moonvocab.domain.card.CheckResult

class AnkiCheckTest : ImportTestBase() {
    @Test fun checkHoldsTheAnkiLock() = runTest {
        pipeline().run(req(backup(1), send = true), {})
        var locked = false
        fakeAnki.onRead = { locked = store.isLocked }
        check.check()
        assertTrue(locked)
    }

    @Test fun failingReadThrowsFromCheckButNotFromRun() = runTest {
        pipeline().run(req(backup(1), send = true), {})
        fakeAnki.failReads = true
        assertFailsWith<NullPointerException> { check.check() }
        assertEquals(0, check.run())
    }

    @Test fun checkReportsUnavailableAnkiDroid() = runTest {
        fakeAnki.availability = AnkiAvailability.NO_PERMISSION
        assertEquals(CheckResult.Unavailable(AnkiAvailability.NO_PERMISSION), check.check())
    }

    private var now = T0
    private val clocked by lazy { AnkiCheck(sync, settings) { now } }

    private suspend fun TestScope.sendOne() = pipeline().run(req(backup(1), send = true), {})

    @Test fun aFinishedCheckRecordsItsTime() = runTest {
        clocked.check()
        assertEquals(now, settings.current().lastCheckAt)
    }

    @Test fun anUnavailableCheckRecordsNoTime() = runTest {
        fakeAnki.availability = AnkiAvailability.NOT_INSTALLED
        clocked.check()
        assertEquals(0L, settings.current().lastCheckAt)
    }

    @Test fun runIfDueSkipsWithinTenMinutes() = runTest {
        sendOne()
        clocked.check()
        now += 10 * 60_000L - 1
        val reads = fakeAnki.reads
        assertFalse(clocked.runIfDue())
        assertEquals(reads, fakeAnki.reads)
    }

    @Test fun runIfDueRunsAfterTenMinutes() = runTest {
        sendOne()
        clocked.check()
        now += 10 * 60_000L
        val reads = fakeAnki.reads
        assertTrue(clocked.runIfDue())
        assertEquals(reads + 1, fakeAnki.reads)
        assertEquals(now, settings.current().lastCheckAt)
    }

    @Test fun runIfDueRunsWhenTheClockMovedBack() = runTest {
        sendOne()
        clocked.check()
        now -= 1
        assertTrue(clocked.runIfDue())
    }
}

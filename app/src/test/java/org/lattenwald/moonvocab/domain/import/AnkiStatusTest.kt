package org.lattenwald.moonvocab.domain.import

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.lattenwald.moonvocab.data.anki.AnkiAvailability
import org.lattenwald.moonvocab.data.anki.AnkiStatus
import org.lattenwald.moonvocab.domain.card.CardStatus.PENDING

class AnkiStatusTest : ImportTestBase() {
    @Test fun aSendThatFindsAnkiDroidUnavailableUpdatesTheStatus() = runTest {
        val status = AnkiStatus(fakeAnki, StandardTestDispatcher(testScheduler))
        val sender = CardSender(db, status, settings, store) { NOW }
        pipeline().run(req(backup(1)), {})
        fakeAnki.availability = AnkiAvailability.NO_PERMISSION

        sender.send(db.cards().byStatus(PENDING).map { it.id })

        assertEquals(AnkiAvailability.NO_PERMISSION, status.state.value)
    }
}

package org.lattenwald.moonvocab.data.anki

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.lattenwald.moonvocab.domain.card.CardStatus.PENDING
import org.lattenwald.moonvocab.domain.import.CardSender
import org.lattenwald.moonvocab.domain.import.ImportTestBase

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

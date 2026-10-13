package org.lattenwald.moonvocab.domain.card

import kotlin.test.assertFailsWith
import org.junit.Test
import org.lattenwald.moonvocab.domain.card.CardEvent.RESTORE
import org.lattenwald.moonvocab.domain.card.CardEvent.SEND
import org.lattenwald.moonvocab.domain.card.CardStatus.DISCARDED
import org.lattenwald.moonvocab.domain.card.CardStatus.FAILED

class CardStatusTest {
    @Test fun cannotSendFailed() { assertFailsWith<IllegalStateException> { next(FAILED, SEND) } }

    @Test fun restoreWithoutPreviousFails() { assertFailsWith<IllegalStateException> { next(DISCARDED, RESTORE) } }
}

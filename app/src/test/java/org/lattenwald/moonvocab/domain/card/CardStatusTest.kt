package org.lattenwald.moonvocab.domain.card

import kotlin.test.assertFailsWith
import org.junit.Assert.assertEquals
import org.junit.Test
import org.lattenwald.moonvocab.domain.card.CardEvent.DELETE_FROM_ANKI
import org.lattenwald.moonvocab.domain.card.CardEvent.DISCARD
import org.lattenwald.moonvocab.domain.card.CardEvent.NOTE_MISSING
import org.lattenwald.moonvocab.domain.card.CardEvent.RESTORE
import org.lattenwald.moonvocab.domain.card.CardEvent.SEND
import org.lattenwald.moonvocab.domain.card.CardEvent.SEND_AS_NEW
import org.lattenwald.moonvocab.domain.card.CardEvent.TRANSLATED
import org.lattenwald.moonvocab.domain.card.CardEvent.TRANSLATION_FAILED
import org.lattenwald.moonvocab.domain.card.CardEvent.UNDO_SEND
import org.lattenwald.moonvocab.domain.card.CardStatus.DELETED_IN_ANKI
import org.lattenwald.moonvocab.domain.card.CardStatus.DISCARDED
import org.lattenwald.moonvocab.domain.card.CardStatus.FAILED
import org.lattenwald.moonvocab.domain.card.CardStatus.PENDING
import org.lattenwald.moonvocab.domain.card.CardStatus.SENT

class CardStatusTest {
    @Test fun spec58Table() {
        assertEquals(PENDING, next(null, TRANSLATED)); assertEquals(FAILED, next(null, TRANSLATION_FAILED))
        assertEquals(PENDING, next(FAILED, TRANSLATED)); assertEquals(SENT, next(PENDING, SEND)); assertEquals(PENDING, next(SENT, UNDO_SEND))
        assertEquals(DISCARDED, next(PENDING, DISCARD)); assertEquals(DISCARDED, next(FAILED, DISCARD)); assertEquals(FAILED, next(DISCARDED, RESTORE, previous = FAILED))
        assertEquals(DISCARDED, next(SENT, DELETE_FROM_ANKI)); assertEquals(DELETED_IN_ANKI, next(SENT, NOTE_MISSING)); assertEquals(SENT, next(DELETED_IN_ANKI, SEND_AS_NEW))
        assertEquals(PENDING, next(PENDING, TRANSLATED)); assertEquals(SENT, next(SENT, TRANSLATED))
    }

    @Test fun cannotSendFailed() { assertFailsWith<IllegalStateException> { next(FAILED, SEND) } }

    @Test fun restoreWithoutPreviousFails() { assertFailsWith<IllegalStateException> { next(DISCARDED, RESTORE) } }
}

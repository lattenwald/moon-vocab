package org.lattenwald.moonvocab.domain.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lattenwald.moonvocab.domain.card.CardStatus.DISCARDED
import org.lattenwald.moonvocab.domain.card.CardStatus.FAILED
import org.lattenwald.moonvocab.domain.card.CardStatus.PENDING
import org.lattenwald.moonvocab.domain.card.CardStatus.SENT

class SameSenseTest {
    private val nbsp = Char(0xA0)
    private val iDiaeresis = Char(0xEF)
    private val combiningDiaeresis = Char(0x308)
    private val rightQuote = Char(0x2019)

    @Test fun senseKeyIgnoresCaseSpacingUnicodeFormAndQuotes() {
        assertEquals(senseKey("schnalle"), senseKey(" Schnalle "))
        assertEquals(senseKey("get the upper hand"), senseKey("get  the${nbsp}upper hand"))
        assertEquals(senseKey("na${iDiaeresis}ve"), senseKey("nai${combiningDiaeresis}ve"))
        assertEquals(senseKey("don't"), senseKey("don${rightQuote}t"))
        assertNotEquals(senseKey("Schnalle"), senseKey("Schnallen"))
    }

    private fun new(index: Int, lemma: String, language: String? = "de") =
        NewCard(index, language, lemma, lemma, lemma, "s $index", "t $index", "m $index")

    private fun known(id: Long, lemma: String, language: String? = "de", status: CardStatus = PENDING) =
        KnownCard(id, status, language, lemma, lemma, "t $id", "m $id")

    @Test fun noCandidatesNoQuestion() = assertTrue(senseQuestions(listOf(new(0, "Schnalle")), listOf(known(1, "Falte"))).isEmpty())

    @Test fun candidatesMatchLanguageAndSenseKeyAndSkipFailed() {
        val qs = senseQuestions(
            listOf(new(0, "Schnalle")),
            listOf(known(1, "schnalle"), known(2, "Schnalle", language = "nl"), known(3, "Schnalle", status = FAILED)),
        )
        assertEquals(listOf("k1"), qs.single().candidates.map { it.ref })
    }

    @Test fun liveKnownCardsComeFirstThenEarlierBatchCards() {
        val qs = senseQuestions(
            listOf(new(0, "Schnalle"), new(1, "Schnalle")),
            listOf(known(5, "Schnalle", status = DISCARDED), known(9, "Schnalle", status = SENT)),
        )
        assertEquals(listOf("k9", "k5", "b0"), qs.single { it.id == "1" }.candidates.map { it.ref })
    }

    @Test fun atMostEightCandidates() =
        assertEquals(8, senseQuestions(listOf(new(0, "a")), (1L..12L).map { known(it, "a") }).single().candidates.size)

    @Test fun secondMarkFollowsFirstIntoDiscardedCard() {
        val batch = listOf(new(0, "Schnalle"), new(1, "Schnalle"))
        val known = listOf(known(5, "Schnalle", status = DISCARDED))
        assertEquals(
            mapOf(0 to Merge.IntoKnown(5, DISCARDED), 1 to Merge.IntoKnown(5, DISCARDED)),
            resolveMerges(batch, mapOf("0" to "k5", "1" to "b0"), known),
        )
    }

    @Test fun sameSenseInsideBatchJoinsTheFirst() = assertEquals(
        mapOf(0 to Merge.New, 1 to Merge.IntoBatch(0), 2 to Merge.IntoBatch(0)),
        resolveMerges(listOf(new(0, "a"), new(1, "a"), new(2, "a")), mapOf("1" to "b0", "2" to "b1"), emptyList()),
    )
}

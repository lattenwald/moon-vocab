package org.lattenwald.moonvocab.domain.import

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lattenwald.moonvocab.data.anki.AnkiAvailability
import org.lattenwald.moonvocab.data.backup.MrproFixture
import org.lattenwald.moonvocab.data.backup.NoteRow
import org.lattenwald.moonvocab.data.db.CardEntity
import org.lattenwald.moonvocab.data.llm.LlmException
import org.lattenwald.moonvocab.domain.card.CardStatus.DISCARDED
import org.lattenwald.moonvocab.domain.card.CardStatus.PENDING
import org.lattenwald.moonvocab.domain.card.SenseJudge

class ImportMergeTest : ImportTestBase() {
    private val firstCandidate = SenseJudge { questions -> questions.associate { it.id to it.candidates.first().ref } }

    private fun again(i: Int): NoteRow = rows(1, from = i).single().copy(time = T0 + 1_000 + i)

    private suspend fun cards(): List<CardEntity> = db.cards().observeAll().first()

    private fun markCardIds(): List<Long?> = db.query("SELECT cardId FROM mark ORDER BY id", null).use { c ->
        buildList { while (c.moveToNext()) add(if (c.isNull(0)) null else c.getLong(0)) }
    }

    @Test fun sameSenseMarkJoinsExistingCard() = runTest {
        pipeline().run(req(MrproFixture.build(tmp, rows(1))), {})
        val s = pipeline(judge = firstCandidate).run(req(MrproFixture.build(tmp, rows(1) + again(0))), {})
        val card = cards().single()
        assertEquals(listOf(card.id, card.id), markCardIds())
        assertEquals(1, s.alreadyCard)
        assertEquals(0, s.newCards)
    }

    @Test fun markMatchingDiscardedCardIsSkipped() = runTest {
        pipeline().run(req(MrproFixture.build(tmp, rows(1))), {})
        db.cards().update(cards().single().copy(status = DISCARDED))
        val s = pipeline(judge = firstCandidate).run(req(MrproFixture.build(tmp, rows(1) + again(0))), {})
        val card = cards().single()
        assertEquals(DISCARDED, card.status)
        assertEquals(listOf(card.id, card.id), markCardIds())
        assertEquals(1, s.skippedDiscarded)
        assertEquals(0, s.alreadyCard)
        assertEquals(0, s.newCards)
    }

    @Test fun sameSenseWithinBatchMakesOneCard() = runTest {
        val s = pipeline(judge = firstCandidate).run(req(MrproFixture.build(tmp, rows(1) + again(0))), {})
        val card = cards().single()
        assertEquals(listOf(card.id, card.id), markCardIds())
        assertEquals(1, s.newCards)
        assertEquals(1, s.alreadyCard)
    }

    @Test fun differentSenseMakesTwoCards() = runTest {
        var calls = 0
        val s = pipeline(judge = { calls++; emptyMap() }).run(req(MrproFixture.build(tmp, rows(1) + again(0))), {})
        assertEquals(1, calls)
        assertEquals(2, cards().size)
        assertEquals(2, s.newCards)
        assertEquals(0, s.alreadyCard)
    }

    @Test fun judgeIsNotAskedWithoutCandidates() = runTest {
        var calls = 0
        val s = pipeline(judge = { calls++; emptyMap() }).run(req(backup(3)), {})
        assertEquals(0, calls)
        assertEquals(3, s.newCards)
    }

    @Test fun senseStopWritesNothingForTheBatch() = runTest {
        val s = pipeline(judge = { throw LlmException.Stop(LlmException.Stop.Kind.AUTH, "401") })
            .run(req(MrproFixture.build(tmp, rows(1) + again(0))), {})
        assertEquals(LlmException.Stop.Kind.AUTH, s.stopped)
        assertTrue(cards().isEmpty())
        assertEquals(listOf(null, null), markCardIds())
    }

    @Test fun senseFailureMakesNewCards() = runTest {
        val s = pipeline(judge = { throw LlmException.Failed("503") }).run(req(MrproFixture.build(tmp, rows(1) + again(0))), {})
        assertEquals(2, db.cards().byStatus(PENDING).size)
        assertEquals(2, s.newCards)
        assertEquals(1, s.senseFailed)
    }

    @Test fun everyMarkIsAccountedFor() = runTest {
        pipeline().run(req(MrproFixture.build(tmp, rows(1))), {})
        db.cards().update(cards().single().copy(status = DISCARDED))
        // Repeats: of the discarded word, within batch 1 (w9), and across batches (w1).
        val backup = MrproFixture.build(tmp, rows(10) + again(0) + again(1) + again(9))
        val s = pipeline(
            translate = { items, _, _, _ -> items.filter { it.marked != word(5) }.associate { it.id to tr(it) } },
            judge = firstCandidate,
        ).run(req(backup), {})
        assertEquals(1, s.skippedDiscarded)
        assertEquals(2, s.alreadyCard)
        assertEquals(1, s.failed)
        assertEquals(8, s.newCards)
        assertEquals(12, s.newCards + s.alreadyCard + s.skippedDiscarded + s.failed)
        assertTrue(db.marks().withoutCard(null).isEmpty())
    }

    @Test fun elsewhereHitLabelsCardAndHoldsItBack() = runTest {
        fakeAnki.elsewhere[word(0)] = "German::Core"
        val s = pipeline().run(req(backup(1), send = true), {})
        val card = cards().single()
        assertEquals(PENDING, card.status)
        assertEquals("German::Core", card.elsewhereDeck)
        assertEquals(0, fakeAnki.adds)
        assertEquals(0, s.sent)
    }

    @Test fun elsewhereSearchesLemmaAndDifferentFocus() = runTest {
        pipeline(
            translate = { items, _, _, _ -> items.associate { it.id to tr(it).copy(lemma = "Schnalle", focus = "Schnallen") } },
            books = { FakeBooks(emptyMap()) },
        ).run(req(backup(1)), {})
        assertEquals(listOf(listOf("Schnalle", "Schnallen")), fakeAnki.searches)
    }

    @Test fun elsewhereSkippedWhenAnkiUnavailable() = runTest {
        fakeAnki.availability = AnkiAvailability.NO_PERMISSION
        fakeAnki.elsewhere[word(0)] = "German::Core"
        pipeline().run(req(backup(1)), {})
        assertTrue(fakeAnki.searches.isEmpty())
        assertNull(cards().single().elsewhereDeck)
    }

    @Test fun elsewhereFailureMeansNoLabel() = runTest {
        fakeAnki.failElsewhere = true
        val s = pipeline().run(req(backup(1)), {})
        assertEquals(1, s.newCards)
        assertEquals(1, fakeAnki.searches.size)
        assertNull(cards().single().elsewhereDeck)
    }
}

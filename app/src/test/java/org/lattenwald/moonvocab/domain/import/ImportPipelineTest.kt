package org.lattenwald.moonvocab.domain.import

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertFailsWith
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lattenwald.moonvocab.data.anki.AnkiAvailability
import org.lattenwald.moonvocab.data.backup.Cut
import org.lattenwald.moonvocab.data.backup.MrproFixture
import org.lattenwald.moonvocab.data.backup.NoteRow
import org.lattenwald.moonvocab.data.llm.LlmException
import org.lattenwald.moonvocab.domain.card.CardStatus.FAILED
import org.lattenwald.moonvocab.domain.card.CardStatus.PENDING
import org.lattenwald.moonvocab.domain.card.CardStatus.SENT
import org.lattenwald.moonvocab.domain.card.TranslationItem
import org.lattenwald.moonvocab.domain.card.buildDraft

class ImportPipelineTest : ImportTestBase() {
    @Test fun interruptedImportResumesWithoutLossOrDuplicates() = runTest {
        val backup = MrproFixture.build(tmp, rows(25))
        var calls = 0
        val p = pipeline(translate = { items, _, _ ->
            if (++calls == 2) throw CancellationException("killed") else items.associate { it.id to tr(it) }
        })
        assertFailsWith<CancellationException> { p.run(req(backup), {}) }
        assertEquals(10, db.cards().byStatus(PENDING).size)
        val s = pipeline().run(req(backup), {})
        assertEquals(15, s.newCards)
        assertEquals((0 until 25).map(::word).sorted(), db.cards().byStatus(PENDING).map { it.word }.sorted())
        assertTrue(db.marks().withoutCard(null).isEmpty())
    }

    @Test fun keyRejectedStopsAndLeavesMarksResumable() = runTest {
        var calls = 0
        val s = pipeline(translate = { _, _, _ -> calls++; throw LlmException.Stop(LlmException.Stop.Kind.AUTH, "401") })
            .run(req(backup(12)), {})
        assertEquals(LlmException.Stop.Kind.AUTH, s.stopped)
        assertEquals(1, calls)
        assertEquals(12, db.marks().withoutCard(null).size)
        assertTrue(db.cards().observeAll().first().isEmpty())
    }

    @Test fun resumeLocatesTheBookByItsStoredMoonPath() = runTest {
        pipeline(translate = { _, _, _ -> throw LlmException.Stop(LlmException.Stop.Kind.CREDITS, "402") }).run(req(backup(2)), {})
        pipeline().run(req(null), {})
        assertEquals(setOf(PATH), fakeBooks.reads.keys)
        assertTrue(db.cards().byStatus(PENDING).none { it.invented })
    }

    @Test fun incompleteBackupStillResumesPendingMarks() = runTest {
        pipeline(translate = { _, _, _ -> throw LlmException.Stop(LlmException.Stop.Kind.CREDITS, "402") }).run(req(backup(2)), {})
        val s = pipeline().run(req(MrproFixture.build(tmp, rows(3), Cut.BEFORE_DB)), {})
        assertEquals("no database in backup", s.incompleteBackup)
        assertEquals(2, s.newCards)
        assertTrue(db.marks().withoutCard(null).isEmpty())
    }

    @Test fun failedBatchMakesFailedCards() = runTest {
        val s = pipeline(translate = { _, _, _ -> throw LlmException.Failed("503") }).run(req(backup(3)), {})
        val failed = db.cards().byStatus(FAILED)
        assertEquals(3, s.failed)
        assertEquals(listOf("503"), failed.map { it.error }.distinct())
        assertEquals((0 until 3).map(::word).sorted(), failed.map { it.word }.sorted())
        assertTrue(db.marks().withoutCard(null).isEmpty())
    }

    @Test fun missingTranslationMakesFailedCard() = runTest {
        pipeline(translate = { items, _, _ -> items.drop(1).associate { it.id to tr(it) } }).run(req(backup(3)), {})
        assertEquals(listOf("no translation returned"), db.cards().byStatus(FAILED).map { it.error })
        assertEquals(2, db.cards().byStatus(PENDING).size)
    }

    @Test fun throwingDraftFailsOnlyItsCard() = runTest {
        val s = pipeline(draft = { sentence, t, max ->
            if (t.focus == word(1)) throw IllegalStateException("boom") else buildDraft(sentence, t, max)
        }).run(req(backup(3)), {})
        assertEquals(listOf(word(1) to "internal error: boom"), db.cards().byStatus(FAILED).map { it.word to it.error })
        assertEquals(listOf(word(0), word(2)), db.cards().byStatus(PENDING).map { it.word }.sorted())
        assertEquals(2, s.newCards)
        assertEquals(1, s.failed)
    }

    @Test fun retryFailedTranslatesFailedMarksAgain() = runTest {
        pipeline(translate = { _, _, _ -> throw LlmException.Failed("503") }).run(req(backup(3)), {})
        val s = pipeline().retryFailed()
        assertEquals(3, s.newCards)
        assertTrue(db.cards().byStatus(FAILED).isEmpty())
        assertEquals(3, db.cards().byStatus(PENDING).size)
    }

    @Test fun eachBookIsReadOnce() = runTest {
        val progress = mutableListOf<ImportProgress>()
        pipeline(books = { fakeBooks }).run(req(backup(25)), { progress += it })
        assertEquals(1, fakeBooks.reads.getValue(PATH))
        assertEquals(ImportProgress(25, 25, TITLE), progress.last())
    }

    @Test fun threeBooksStayInMemory() = runTest {
        val books = FakeBooks(listOf(A, B, C).associateWith { bookText(30) })
        pipeline(books = { books }).run(req(MrproFixture.build(tmp, marksInBookOrder(A, B, C, A))), {})
        assertEquals(1, books.reads.getValue(A))
    }

    @Test fun fourthBookEvictsTheOldest() = runTest {
        val books = FakeBooks(listOf(A, B, C, D).associateWith { bookText(30) })
        pipeline(books = { books }).run(req(MrproFixture.build(tmp, marksInBookOrder(A, B, C, D, A))), {})
        assertEquals(2, books.reads.getValue(A))
    }

    // The pipeline takes marks newest first.
    private fun marksInBookOrder(vararg paths: String) = paths.mapIndexed { i, path ->
        val title = path.substringAfterLast('/')
        NoteRow(title, path, chapter = 0, position = i * SENTENCE.length, color = ORANGE, time = T0 + paths.size - i, original = word(i), bak = "1")
    }

    @Test fun bookTextGivesSentenceAndBookLanguage() = runTest {
        val seen = mutableListOf<TranslationItem>()
        pipeline(translate = { items, _, _ -> seen += items; items.associate { it.id to tr(it) } }).run(req(backup(2)), {})
        assertEquals("On the page the reader found ${word(1)} and smiled at it.", seen.first().sentence)
        assertEquals("en", seen.first().sourceLanguage)
        val card = db.cards().byStatus(PENDING).first()
        assertEquals("en", db.books().byId(card.bookId)?.language)
        assertEquals(false, card.invented)
    }

    @Test fun modelSourceLanguageIsNormalizedOnTheCard() = runTest {
        pipeline(translate = { items, _, _ -> items.associate { it.id to tr(it).copy(sourceLanguage = "ger") } }).run(req(backup(1)), {})
        assertEquals("de", db.cards().byStatus(PENDING).single().sourceLanguage)
    }

    @Test fun bookWithoutTextGetsInventedExamples() = runTest {
        val seen = mutableListOf<TranslationItem>()
        pipeline(translate = { items, _, _ -> seen += items; items.associate { it.id to tr(it) } }, books = { FakeBooks(emptyMap()) })
            .run(req(backup(3)), {})
        assertTrue(seen.all { it.sentence == null })
        val cards = db.cards().byStatus(PENDING)
        assertEquals(3, cards.size)
        assertTrue(cards.all { it.invented && it.sentenceHtml.isEmpty() })
        assertEquals("de", db.books().byId(cards.first().bookId)?.language)
    }

    @Test fun ankiUnavailableKeepsCardsPending() = runTest {
        fakeAnki.availability = AnkiAvailability.NO_PERMISSION
        val s = pipeline().run(req(backup(3), send = true), {})
        assertEquals(0, s.sent)
        assertEquals(3, db.cards().byStatus(PENDING).size)
        assertTrue(fakeAnki.notes.isEmpty())
        assertEquals(0, fakeAnki.reads)
    }

    @Test fun sendModeSendsToOverrideDeckWithTags() = runTest {
        val s = pipeline().run(req(backup(2), send = true), {})
        assertEquals(2, s.sent)
        assertEquals(setOf("Moon+::Test"), fakeAnki.notes.values.map { it.deck }.toSet())
        assertTrue(fakeAnki.notes.values.all { "moon-vocab" in it.tags })
        assertEquals(2, db.cards().byStatus(SENT).size)
    }

    @Test fun sendErrorStopsLaterSendsInTheRun() = runTest {
        fakeAnki.failOnNthAdd = 1
        val s = pipeline().run(req(backup(25), send = true), {})
        assertEquals(1, fakeAnki.adds)
        assertEquals("AnkiDroid refused to add the note", s.sendError)
        assertEquals(25, db.cards().byStatus(PENDING).size)
    }

    @Test fun bookFieldHasTheAuthorFromTheBookText() = runTest {
        pipeline().run(req(backup(1), send = true), {})
        assertEquals("$TITLE, Greg Egan", fakeAnki.notes.values.single().fields["Book"])
    }

    @Test fun markOutsideSelectedStylesIsIgnored() = runTest {
        val grey = NoteRow(TITLE, PATH, chapter = 0, position = 0, color = -7829368, time = T0 + 100, original = word(1), underline = 1)
        pipeline().run(req(MrproFixture.build(tmp, rows(1) + grey), styles = setOf(WAVY_ORANGE)), {})
        assertEquals(1, db.cards().observeAll().first().size)
    }

    @Test fun onlySelectedBookIsProcessed() = runTest {
        val backup = MrproFixture.build(tmp, rows(2) + rows(3, path = "/sdcard/Books/Other.fb2", title = "Other", from = 10))
        pipeline(translate = { _, _, _ -> throw LlmException.Stop(LlmException.Stop.Kind.CREDITS, "402") }).run(req(backup), {})
        val s = pipeline().run(req(null, only = "other.fb2"), {})
        assertEquals(3, s.newCards)
        assertEquals(listOf(word(10), word(11), word(12)), db.cards().byStatus(PENDING).map { it.word }.sorted())
        assertEquals(2, db.marks().withoutCard(null).size)
        assertNull(s.stopped)
    }

    private companion object {
        const val A = "/sdcard/Books/A.epub"
        const val B = "/sdcard/Books/B.epub"
        const val C = "/sdcard/Books/C.epub"
        const val D = "/sdcard/Books/D.epub"
    }
}

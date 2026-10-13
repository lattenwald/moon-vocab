package org.lattenwald.moonvocab.domain.cardpage

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lattenwald.moonvocab.data.db.CardEntity
import org.lattenwald.moonvocab.data.llm.LlmException
import org.lattenwald.moonvocab.domain.card.CardStatus.PENDING
import org.lattenwald.moonvocab.domain.card.EnglishAccent
import org.lattenwald.moonvocab.domain.card.TranslationItem
import org.lattenwald.moonvocab.domain.card.Translator
import org.lattenwald.moonvocab.domain.card.markedText
import org.lattenwald.moonvocab.domain.import.ImportTestBase

class CardRetranslatorTest : ImportTestBase() {
    private fun retranslator(translator: Translator) = CardRetranslator(db, translator, settings)

    private suspend fun TestScope.pendingCard(headword: String? = null): CardEntity {
        pipeline(translate = { items, _, _, _ -> items.associate { it.id to tr(it).let { t -> t.copy(headword = headword ?: t.headword) } } })
            .run(req(backup(1)), {})
        return db.cards().byStatus(PENDING).single()
    }

    private suspend fun stored(id: Long): CardEntity = db.cards().byIds(listOf(id)).single()

    @Test fun retranslateSendsTheMarkTheStoredSentenceAndTheCardsTarget() = runTest {
        val card = pendingCard(headword = "edited")
        settings.update { it.copy(targetLanguage = "de", englishAccent = EnglishAccent.BRITISH) }
        var sent: List<TranslationItem> = emptyList()
        var target = ""
        var accent: EnglishAccent? = null
        val translator = Translator { items, targetLanguage, _, englishAccent ->
            sent = items
            target = targetLanguage
            accent = englishAccent
            items.associate { it.id to tr(it) }
        }

        retranslator(translator).retranslate(card.id)

        val item = sent.single()
        assertEquals(card.id.toString(), item.id)
        assertEquals("edited", card.word)
        assertEquals(word(0), item.marked)
        assertTrue(card.sentenceHtml.isNotEmpty())
        assertEquals(markedText(card.sentenceHtml).text, item.sentence)
        assertEquals(TITLE, item.bookTitle)
        assertEquals(card.sourceLanguage, item.sourceLanguage)
        assertEquals("ru", card.targetLanguage)
        assertEquals(card.targetLanguage, target)
        assertEquals(EnglishAccent.BRITISH, accent)
    }

    @Test fun retranslateReturnsTheNewFieldsAndWritesNothing() = runTest {
        val card = pendingCard()
        val translator = Translator { items, _, _, _ ->
            items.associate { it.id to tr(it).copy(transcription = "ˈnoʊvɨj", translation = "новый", widened = true) }
        }

        val ok = retranslator(translator).retranslate(card.id) as RetranslateResult.Ok

        assertEquals("новый", ok.retranslation.translation)
        assertEquals("ˈnoʊvɨj", ok.retranslation.transcription)
        assertTrue(ok.retranslation.extras.widened)
        assertEquals(card, stored(card.id))
    }

    @Test fun aStopAndAFailureAreReported() = runTest {
        val card = pendingCard()

        val stopped = retranslator { _, _, _, _ -> throw LlmException.Stop(LlmException.Stop.Kind.AUTH, "401") }.retranslate(card.id)
        val failed = retranslator { _, _, _, _ -> throw LlmException.Failed("boom") }.retranslate(card.id)

        assertEquals(RetranslateResult.Stopped(LlmException.Stop.Kind.AUTH), stopped)
        assertEquals(RetranslateResult.Failed("boom"), failed)
    }
}

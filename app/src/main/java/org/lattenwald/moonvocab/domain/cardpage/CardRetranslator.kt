package org.lattenwald.moonvocab.domain.cardpage

import org.lattenwald.moonvocab.data.db.AppDatabase
import org.lattenwald.moonvocab.data.llm.LlmException
import org.lattenwald.moonvocab.data.settings.Settings
import org.lattenwald.moonvocab.domain.card.CardExtras
import org.lattenwald.moonvocab.domain.card.TranslationItem
import org.lattenwald.moonvocab.domain.card.Translator
import org.lattenwald.moonvocab.domain.card.markedText

data class Retranslation(val word: String, val grammar: String, val translation: String, val meaning: String, val extras: CardExtras)

sealed interface RetranslateResult {
    data class Ok(val retranslation: Retranslation) : RetranslateResult
    data class Stopped(val kind: LlmException.Stop.Kind) : RetranslateResult
    data class Failed(val message: String) : RetranslateResult
}

class CardRetranslator(
    private val db: AppDatabase,
    private val translator: Translator,
    private val settings: Settings,
) {
    suspend fun retranslate(cardId: Long): RetranslateResult {
        val card = db.cards().byIds(listOf(cardId)).singleOrNull() ?: return RetranslateResult.Failed("card not found")
        // A card with an invented example has an empty sentence.
        val context = markedText(card.sentenceHtml).text.ifEmpty { markedText(card.excerptHtml).text }
        val item = TranslationItem(
            id = cardId.toString(),
            marked = db.marks().firstTextForCard(cardId) ?: card.word,
            sentence = context.ifEmpty { null },
            bookTitle = db.books().byId(card.bookId)?.title.orEmpty(),
            sourceLanguage = card.sourceLanguage,
        )
        val t = try {
            translator.translate(listOf(item), card.targetLanguage, settings.current().maxExcerptWords)[item.id]
        } catch (e: LlmException.Stop) {
            return RetranslateResult.Stopped(e.kind)
        } catch (e: LlmException.Failed) {
            return RetranslateResult.Failed(e.message ?: e::class.java.simpleName)
        } ?: return RetranslateResult.Failed("no translation returned")
        return RetranslateResult.Ok(Retranslation(t.headword, t.grammar, t.translation, t.meaning, CardExtras(t.lemma, t.widened)))
    }
}

package org.lattenwald.moonvocab.data.llm

import java.util.Locale
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import org.lattenwald.moonvocab.domain.card.EnglishAccent
import org.lattenwald.moonvocab.domain.card.Translation
import org.lattenwald.moonvocab.domain.card.TranslationItem
import org.lattenwald.moonvocab.domain.card.Translator
import org.lattenwald.moonvocab.domain.card.cleanTranscription

class TranslationService(private val llm: LlmClient) : Translator {
    override suspend fun translate(
        items: List<TranslationItem>,
        targetLanguage: String,
        maxExcerptWords: Int,
        accent: EnglishAccent,
    ): Map<String, Translation> {
        val target = Locale.forLanguageTag(targetLanguage).getDisplayLanguage(Locale.ENGLISH)
        val system = Prompt.system(target, maxExcerptWords, accent)
        val content = llm.complete(system, userMessage(items), Prompt.schema, TOKENS_PER_ITEM * items.size)
        val results = replyItems(content) ?: return emptyMap()
        val wanted = items.mapTo(HashSet()) { it.id }
        val translations = LinkedHashMap<String, Translation>()
        for (element in results) {
            val translation = (element as? JsonObject)?.let(::toTranslation) ?: continue
            if (translation.id in wanted) translations.putIfAbsent(translation.id, translation)
        }
        return translations
    }

    private fun userMessage(items: List<TranslationItem>): String = buildJsonArray {
        for (item in items) addJsonObject {
            put("id", item.id)
            put("marked", item.marked)
            put("sentence", item.sentence)
            put("bookTitle", item.bookTitle)
            put("sourceLanguage", item.sourceLanguage)
        }
    }.toString()

    private fun toTranslation(o: JsonObject): Translation? {
        val id = o.text("id")?.ifEmpty { null } ?: return null
        val focus = o.text("focus")?.ifEmpty { null } ?: return null
        val headword = o.text("headword")?.ifEmpty { null } ?: return null
        val translation = o.text("translation")?.ifEmpty { null } ?: return null
        return Translation(
            id = id,
            sourceLanguage = o.text("sourceLanguage")?.ifEmpty { null },
            focus = focus,
            headword = headword,
            transcription = cleanTranscription(o.text("transcription").orEmpty()),
            lemma = o.text("lemma")?.ifEmpty { null } ?: headword,
            widened = (o["widened"] as? JsonPrimitive)?.booleanOrNull ?: false,
            grammar = o.text("grammar").orEmpty(),
            translation = translation,
            meaning = o.text("meaning").orEmpty(),
            excerpt = o.text("excerpt")?.ifEmpty { null },
            inventedExample = o.text("inventedExample")?.ifEmpty { null },
            sourceNote = o.text("sourceNote")?.ifEmpty { null },
        )
    }

    private companion object {
        const val TOKENS_PER_ITEM = 400
    }
}

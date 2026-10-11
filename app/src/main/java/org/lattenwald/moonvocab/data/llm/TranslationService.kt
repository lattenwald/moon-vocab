package org.lattenwald.moonvocab.data.llm

import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import org.lattenwald.moonvocab.domain.card.Translation
import org.lattenwald.moonvocab.domain.card.TranslationItem
import org.lattenwald.moonvocab.domain.card.Translator

class TranslationService(private val llm: LlmClient) : Translator {
    override suspend fun translate(items: List<TranslationItem>, targetLanguage: String, maxExcerptWords: Int): Map<String, Translation> {
        val target = Locale.forLanguageTag(targetLanguage).getDisplayLanguage(Locale.ENGLISH)
        val content = llm.complete(Prompt.system(target, maxExcerptWords), userMessage(items), Prompt.schema, TOKENS_PER_ITEM * items.size)
        val results = parseReply(content) ?: return emptyMap()
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

    private fun parseReply(content: String): JsonArray? {
        val root = parseObject(content) ?: firstObject(content)?.let(::parseObject)
        return root?.get("items") as? JsonArray
    }

    private fun parseObject(text: String): JsonObject? = try {
        Json.parseToJsonElement(text) as? JsonObject
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun firstObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
            } else when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> if (--depth == 0) return text.substring(start, i + 1)
            }
        }
        return null
    }

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

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()

    private companion object {
        const val TOKENS_PER_ITEM = 400
    }
}

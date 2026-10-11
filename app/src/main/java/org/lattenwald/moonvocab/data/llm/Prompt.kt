package org.lattenwald.moonvocab.data.llm

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object Prompt {
    fun system(target: String, maxExcerptWords: Int): String =
        SYSTEM.replace("{target}", target).replace("{max}", maxExcerptWords.toString())

    val schema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("items") {
                put("type", "array")
                put("items", strictObject(itemProperties()))
            }
        }
        putJsonArray("required") { add("items") }
        put("additionalProperties", false)
    }

    private fun itemProperties(): Map<String, JsonElement> {
        val string = JsonPrimitive("string")
        val nullableString = buildJsonArray { add("string"); add("null") }
        return linkedMapOf(
            "id" to string,
            "sourceLanguage" to string,
            "focus" to string,
            "headword" to string,
            "lemma" to string,
            "widened" to JsonPrimitive("boolean"),
            "grammar" to string,
            "translation" to string,
            "meaning" to string,
            "excerpt" to nullableString,
            "inventedExample" to nullableString,
            "sourceNote" to nullableString,
        )
    }

    private fun strictObject(properties: Map<String, JsonElement>) = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { properties.forEach { (name, type) -> putJsonObject(name) { put("type", type) } } }
        putJsonArray("required") { properties.keys.forEach { add(it) } }
        put("additionalProperties", false)
    }

    private val SYSTEM = """
You help a reader learn vocabulary from the books they read. Each item has the text the reader marked ("marked"), usually the sentence it came from ("sentence"), the book title, and sometimes the book's language. Reply with JSON matching the schema: one result per item, with the same id.

For each item:
- sourceLanguage: ISO 639-1 code of the language the marked text is in.
- focus: the word or phrase worth learning, copied exactly as it appears in the sentence (same letters, same case). Usually this is the marked text. If the marked text is a long phrase or a whole sentence, pick the one word or short phrase in it that a learner most likely looked up.
- If the focus is part of a fixed phrase or idiom whose meaning differs from its words, widen the focus to that phrase as it appears in the sentence and set widened to true. Otherwise widened is false.
- headword: the dictionary form of the focus, with the article for languages that put one before nouns (German "die Schnalle"). For a phrase, its dictionary form ("get the upper hand").
- lemma: the headword without article or particle.
- grammar: a short grammar note in the style of the source language's dictionaries, with abbreviations such as "n.", "v.", "adj.", "phr.", "f., -n".
- translation: translation into {target}: a few comma-separated options, the one that fits this sentence first.
- meaning: one short line in {target} saying what it means in this sentence; empty string if the translation already says it.
- excerpt: only when the sentence has more than {max} words: a contiguous part of the sentence, at most {max} words, containing the focus, with "…" where text was cut and the focus in [square brackets]. Otherwise null.
- inventedExample: only when there is no sentence: a natural sentence of 6 to 15 words in the source language that uses the focus, with the focus in [square brackets]. Otherwise null.
- sourceNote: only when inventedExample is set: the phrase "Example written by the model" in {target}. Otherwise null.
""".trim()
}

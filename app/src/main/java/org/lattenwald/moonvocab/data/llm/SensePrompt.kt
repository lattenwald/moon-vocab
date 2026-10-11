package org.lattenwald.moonvocab.data.llm

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object SensePrompt {
    val schema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("items") {
                put("type", "array")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("id") { put("type", "string") }
                        putJsonObject("match") { putJsonArray("type") { add("string"); add("null") } }
                    }
                    putJsonArray("required") { add("id"); add("match") }
                    put("additionalProperties", false)
                }
            }
        }
        putJsonArray("required") { add("items") }
        put("additionalProperties", false)
    }

    val SYSTEM = """
A reader learns vocabulary from the books they read and keeps one flashcard per meaning of a word. Each question has a word the reader just marked: how it appears in the text ("focus"), the sentence ("sentence", or null), its dictionary form ("word"), translation and meaning. It also has flashcards the reader already has for the same dictionary form ("candidates"), each with a ref. Reply with JSON matching the schema: one result per question, with the same id.

For each question, set match to the ref of the candidate that teaches the meaning the word has in this sentence. Set match to null when no candidate has that meaning: a different sense, a different part of speech, or a fixed phrase whose meaning differs from the single word. If several candidates fit, pick the first of them.
""".trim()
}

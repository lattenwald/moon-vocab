package org.lattenwald.moonvocab.data.llm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal fun replyItems(content: String): JsonArray? {
    val root = parseObject(content) ?: firstObject(content)?.let(::parseObject)
    return root?.get("items") as? JsonArray
}

internal fun JsonObject.text(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()

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

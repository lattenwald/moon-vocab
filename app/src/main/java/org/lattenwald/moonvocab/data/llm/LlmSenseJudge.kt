package org.lattenwald.moonvocab.data.llm

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.lattenwald.moonvocab.domain.card.SenseJudge
import org.lattenwald.moonvocab.domain.card.SenseQuestion

class LlmSenseJudge(private val llm: LlmClient) : SenseJudge {
    override suspend fun judge(questions: List<SenseQuestion>): Map<String, String> {
        if (questions.isEmpty()) return emptyMap()
        val content = llm.complete(SensePrompt.SYSTEM, userMessage(questions), SensePrompt.schema, TOKENS_PER_QUESTION * questions.size + TOKENS_BASE)
        val results = replyItems(content) ?: return emptyMap()
        val refs = questions.associate { q -> q.id to q.candidates.mapTo(HashSet()) { it.ref } }
        val matches = LinkedHashMap<String, String>()
        val answered = HashSet<String>()
        for (element in results) {
            val o = element as? JsonObject ?: continue
            val id = o.text("id") ?: continue
            val own = refs[id] ?: continue
            if (!answered.add(id)) continue
            val match = o.text("match") ?: continue
            if (match in own) matches[id] = match
        }
        return matches
    }

    private fun userMessage(questions: List<SenseQuestion>): String = buildJsonArray {
        for (q in questions) addJsonObject {
            put("id", q.id)
            put("word", q.word)
            put("focus", q.focus)
            put("sentence", q.sentence)
            put("translation", q.translation)
            put("meaning", q.meaning)
            putJsonArray("candidates") {
                for (c in q.candidates) addJsonObject {
                    put("ref", c.ref)
                    put("word", c.word)
                    put("translation", c.translation)
                    put("meaning", c.meaning)
                }
            }
        }
    }.toString()

    private companion object {
        const val TOKENS_PER_QUESTION = 60
        const val TOKENS_BASE = 100
    }
}

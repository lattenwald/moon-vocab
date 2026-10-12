package org.lattenwald.moonvocab.data.llm

import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.lattenwald.moonvocab.domain.card.SenseCandidate
import org.lattenwald.moonvocab.domain.card.SenseQuestion
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LlmSenseJudgeTest {
    private val server = MockWebServer()

    @Before fun start() = server.start()
    @After fun stop() = server.close()

    private fun judge() = LlmSenseJudge(LlmClient(
        OkHttpClient.Builder().retryOnConnectionFailure(false).build(),
        { LlmConfig(server.url("/api/v1/").toString(), "sk-test", "test/model") },
        {},
    ))

    private fun reply(content: String) = server.enqueue(MockResponse(code = 200, body = buildJsonObject {
        putJsonArray("choices") { addJsonObject { putJsonObject("message") { put("content", content) } } }
    }.toString()))

    private fun q(id: String, vararg refs: String) = SenseQuestion(
        id, "bank", "banks", "Two banks of the river.", "берег", "",
        refs.map { SenseCandidate(it, "bank", "банк", "a money institution") },
    )

    @Test fun noQuestionsNoRequest() = runTest { assertTrue(judge().judge(emptyList()).isEmpty()); assertEquals(0, server.requestCount) }

    @Test fun keepsOnlyOwnCandidateRefs() = runTest {
        reply("""{"items":[{"id":"0","match":"k7"},{"id":"1","match":"k7"},{"id":"2","match":"k999"},{"id":"zz","match":"k7"}]}""")
        assertEquals(mapOf("0" to "k7"), judge().judge(listOf(q("0", "k7"), q("1", "b0"), q("2", "k8"))))
    }

    @Test fun nullMatchAndFirstDuplicateWins() = runTest {
        reply("""{"items":[{"id":"0","match":null},{"id":"0","match":"k7"},{"id":"1","match":"b0"},{"id":"1","match":null}]}""")
        assertEquals(mapOf("1" to "b0"), judge().judge(listOf(q("0", "k7"), q("1", "b0"))))
    }

    @Test fun malformedReplyIsNoMatch() = runTest { reply("{\"items\":[{\"id\":\"0\""); assertTrue(judge().judge(listOf(q("0", "k7"))).isEmpty()) }

    @Test fun requestCarriesCandidatesAndBudget() = runTest {
        reply("""{"items":[]}"""); judge().judge(listOf(q("0", "k7"), q("1", "b0")))
        val body = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertEquals(2220, body["max_tokens"]!!.jsonPrimitive.int)
        assertTrue("\"ref\":\"k7\"" in body["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test fun sendsQuestionsWithFieldNamesAndExplicitNulls() = runTest {
        reply("""{"items":[]}""")
        judge().judge(listOf(SenseQuestion("0", "bank", "banks", null, "берег", "edge of a river", listOf(SenseCandidate("k7", "bank", "банк", "")))))
        val body = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        val expected = Json.parseToJsonElement("""[{"id":"0","word":"bank","focus":"banks","sentence":null,"translation":"берег",
            "meaning":"edge of a river","candidates":[{"ref":"k7","word":"bank","translation":"банк","meaning":""}]}]""")
        assertEquals(expected, Json.parseToJsonElement(body["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonPrimitive.content))
    }

    @Test fun llmStopPropagates() = runTest {
        server.enqueue(MockResponse(code = 402))
        assertFailsWith<LlmException.Stop> { judge().judge(listOf(q("0", "k7"))) }
    }

    // Strict structured outputs reject an object unless all its properties are required and additionalProperties is false.
    @Test fun schemaSatisfiesStrictStructuredOutputs() {
        fun check(schema: JsonObject) {
            schema["items"]?.let { check(it.jsonObject) }
            val properties = schema["properties"]?.jsonObject ?: return
            assertEquals(JsonPrimitive(false), schema["additionalProperties"])
            assertEquals(properties.keys, schema["required"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet())
            properties.values.forEach { check(it.jsonObject) }
        }
        check(SensePrompt.schema)
        val item = SensePrompt.schema["properties"]!!.jsonObject["items"]!!.jsonObject["items"]!!.jsonObject
        assertEquals(setOf("id", "match"), item["properties"]!!.jsonObject.keys)
    }
}

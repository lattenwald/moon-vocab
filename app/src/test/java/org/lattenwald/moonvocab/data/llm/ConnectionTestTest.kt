package org.lattenwald.moonvocab.data.llm

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.lattenwald.moonvocab.data.settings.LlmService
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConnectionTestTest {
    private val server = MockWebServer()
    private val apiKey = "sk-or-secret-key"

    @Before fun start() = server.start()
    @After fun stop() = server.close()

    private fun service() = LlmService("s1", "Mine", server.url("/api/v1/").toString(), apiKey, "own/model")

    private fun tester(clock: () -> Long = { 0L }) = ConnectionTest(OkHttpClient.Builder().retryOnConnectionFailure(false).build(), clock)

    private fun reply(content: String) = server.enqueue(MockResponse(code = 200, body = buildJsonObject {
        putJsonArray("choices") { addJsonObject { putJsonObject("message") { put("content", content) } } }
    }.toString()))

    @Test fun okCarriesHeadwordAndTranslation() = runTest {
        reply("""{"items":[{"id":"test","focus":"Schnallen","headword":"die Schnalle","translation":"пряжка"}]}""")
        val times = ArrayDeque(listOf(1_000L, 2_400L))
        assertEquals(TestResult.Ok(1_400, "die Schnalle", "пряжка"), tester { times.removeFirst() }.run(service(), "ru"))
        val body = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertEquals("own/model", body["model"]!!.jsonPrimitive.content)
        val user = body["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonPrimitive.content
        val item = Json.parseToJsonElement(user).jsonArray.single().jsonObject
        assertEquals("test", item["id"]!!.jsonPrimitive.content)
        assertEquals("Schnallen", item["marked"]!!.jsonPrimitive.content)
        assertEquals("Der Mantel hatte viele Taschen und Schnallen.", item["sentence"]!!.jsonPrimitive.content)
        assertEquals("Test", item["bookTitle"]!!.jsonPrimitive.content)
        assertEquals("de", item["sourceLanguage"]!!.jsonPrimitive.content)
    }

    @Test fun testReportsARejectedKey() = runTest {
        server.enqueue(MockResponse(code = 401))
        assertEquals(LlmException.Stop.Kind.AUTH, (tester().run(service(), "ru") as TestResult.Failed).kind)
    }

    @Test fun testReportsAWrongAddress() = runTest {
        server.enqueue(MockResponse(code = 404))
        assertEquals(LlmException.Stop.Kind.CONFIG, (tester().run(service(), "ru") as TestResult.Failed).kind)
    }

    @Test fun anEmptyReplyIsNoTranslation() = runTest {
        reply("""{"items":[]}""")
        assertEquals(TestResult.NoTranslation, tester().run(service(), "ru"))
    }

    @Test fun testNeverEchoesTheKey() = runTest {
        server.enqueue(MockResponse(code = 400, body = """{"error":"key $apiKey is not valid"}"""))
        val failed = tester().run(service(), "ru") as TestResult.Failed
        assertNull(failed.kind)
        assertTrue(failed.message, "***" in failed.message)
        assertFalse(failed.message, apiKey in failed.message)
    }
}

package org.lattenwald.moonvocab.data.llm

import java.net.InetAddress
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.Headers.Companion.headersOf
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LlmClientTest {
    private val server = MockWebServer()
    private val schema = buildJsonObject { put("type", "object") }
    private val apiKey = "sk-or-secret-key"

    @Before fun start() = server.start()
    @After fun stop() = server.close()

    // OkHttp's own reconnect retry would hide LlmClient's retry count.
    private fun client(sleep: suspend (Long) -> Unit = {}, key: String = apiKey) = LlmClient(
        OkHttpClient.Builder().retryOnConnectionFailure(false).build(),
        { LlmConfig(server.url("/api/v1/").toString(), key, "test/model") },
        sleep,
    )

    private fun ok(content: String) = MockResponse(code = 200, body = buildJsonObject {
        putJsonArray("choices") { addJsonObject { putJsonObject("message") { put("content", content) } } }
    }.toString())

    @Test fun returnsContent() = runTest {
        server.enqueue(ok("""{"items":[]}"""))
        assertEquals("""{"items":[]}""", client().complete("s", "u", schema, 100))
    }

    @Test fun sendsChatCompletionWithSchema() = runTest {
        server.enqueue(ok("x"))
        client().complete("sys", "usr", schema, 123)
        val request = server.takeRequest()
        assertEquals("/api/v1/chat/completions", request.url.encodedPath)
        assertEquals("Bearer $apiKey", request.headers["Authorization"])
        val expected = buildJsonObject {
            put("model", "test/model")
            putJsonArray("messages") {
                addJsonObject { put("role", "system"); put("content", "sys") }
                addJsonObject { put("role", "user"); put("content", "usr") }
            }
            put("max_tokens", 2123)
            putJsonObject("response_format") {
                put("type", "json_schema")
                putJsonObject("json_schema") { put("name", "cards"); put("strict", true); put("schema", schema) }
            }
        }
        assertEquals(expected, Json.parseToJsonElement(request.body!!.utf8()).jsonObject)
    }

    @Test fun openRouterRequestTurnsReasoningOff() = runTest {
        server.enqueue(ok("x"))
        val openRouter = LlmClient(
            OkHttpClient.Builder().dns { listOf(InetAddress.getLoopbackAddress()) }.build(),
            { LlmConfig("http://openrouter.ai:${server.port}/api/v1", apiKey, "test/model") },
            {},
        )
        openRouter.complete("s", "u", schema, 100)
        val body = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertEquals(buildJsonObject { put("enabled", false) }, body["reasoning"])
    }

    @Test fun replyCutOffAtTokenLimitFails() = runTest {
        server.enqueue(MockResponse(code = 200, body = """{"choices":[{"finish_reason":"length","message":{"content":"{\"items\":[{\"id\":\"a\""}}]}"""))
        val message = assertFailsWith<LlmException.Failed> { client().complete("s", "u", schema, 100) }.message!!
        assertTrue(message, "cut off" in message)
    }

    @Test fun retriesAfterRetryAfter() = runTest {
        server.enqueue(MockResponse(code = 429, headers = headersOf("Retry-After", "3"))); server.enqueue(ok("x"))
        val slept = mutableListOf<Long>()
        assertEquals("x", client(sleep = { slept += it }).complete("s", "u", schema, 100))
        assertEquals(listOf(3000L), slept)
    }

    @Test fun retryAfterIsCappedAtSixtySeconds() = runTest {
        server.enqueue(MockResponse(code = 503, headers = headersOf("Retry-After", "3600"))); server.enqueue(ok("x"))
        val slept = mutableListOf<Long>()
        client(sleep = { slept += it }).complete("s", "u", schema, 100)
        assertEquals(listOf(60_000L), slept)
    }

    @Test fun backsOffTwoThenEightSecondsWithoutRetryAfter() = runTest {
        server.enqueue(MockResponse(code = 429)); server.enqueue(MockResponse(code = 500)); server.enqueue(ok("x"))
        val slept = mutableListOf<Long>()
        assertEquals("x", client(sleep = { slept += it }).complete("s", "u", schema, 100))
        assertEquals(listOf(2000L, 8000L), slept)
    }

    @Test fun failsAfterTwoRetries() = runTest {
        repeat(3) { server.enqueue(MockResponse(code = 503)) }
        assertFailsWith<LlmException.Failed> { client(sleep = {}).complete("s", "u", schema, 100) }
        assertEquals(3, server.requestCount)
    }

    @Test fun networkErrorIsRetried() = runTest {
        server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()); server.enqueue(ok("x"))
        val slept = mutableListOf<Long>()
        assertEquals("x", client(sleep = { slept += it }).complete("s", "u", schema, 100))
        assertEquals(listOf(2000L), slept)
    }

    @Test fun noCreditsStops() = runTest {
        server.enqueue(MockResponse(code = 402))
        assertEquals(LlmException.Stop.Kind.CREDITS, assertFailsWith<LlmException.Stop> { client().complete("s", "u", schema, 100) }.kind)
    }

    @Test fun rejectedKeyStops() = runTest {
        for (code in listOf(401, 403)) {
            server.enqueue(MockResponse(code = code))
            assertEquals(LlmException.Stop.Kind.AUTH, assertFailsWith<LlmException.Stop> { client().complete("s", "u", schema, 100) }.kind)
        }
        assertEquals(2, server.requestCount)
    }

    @Test fun unsupportedSchemaRetriesWithoutIt() = runTest {
        server.enqueue(MockResponse(code = 400, body = """{"error":{"message":"response_format json_schema not supported"}}""")); server.enqueue(ok("y"))
        assertEquals("y", client().complete("s", "u", schema, 100))
        server.takeRequest(); assertFalse("response_format" in server.takeRequest().body!!.utf8())
    }

    @Test fun secondBadRequestFails() = runTest {
        repeat(2) { server.enqueue(MockResponse(code = 400, body = """{"error":{"message":"json_schema not supported"}}""")) }
        assertFailsWith<LlmException.Failed> { client().complete("s", "u", schema, 100) }
        assertEquals(2, server.requestCount)
    }

    @Test fun otherBadRequestFailsWithoutResend() = runTest {
        server.enqueue(MockResponse(code = 400, body = """{"error":{"message":"max_tokens too large"}}"""))
        assertFailsWith<LlmException.Failed> { client().complete("s", "u", schema, 100) }
        assertEquals(1, server.requestCount)
    }

    @Test fun otherClientErrorFailsWithoutRetry() = runTest {
        server.enqueue(MockResponse(code = 422))
        assertFailsWith<LlmException.Failed> { client().complete("s", "u", schema, 100) }
        assertEquals(1, server.requestCount)
    }

    @Test fun notFoundStopsAsConfigurationError() = runTest {
        server.enqueue(MockResponse(code = 404, body = """{"error":{"message":"No endpoints found for test/model"}}"""))
        val e = assertFailsWith<LlmException.Stop> { client().complete("s", "u", schema, 100) }
        assertEquals(LlmException.Stop.Kind.CONFIG, e.kind)
        assertTrue(e.message!!, "No endpoints found" in e.message!!)
        assertEquals(1, server.requestCount)
    }

    @Test fun invalidOrEmptyBaseUrlStopsAsConfigurationError() = runTest {
        for (baseUrl in listOf("", "openrouter.ai/api/v1")) {
            val client = LlmClient(OkHttpClient(), { LlmConfig(baseUrl, apiKey, "test/model") })
            val e = assertFailsWith<LlmException.Stop> { client.complete("s", "u", schema, 100) }
            assertEquals(LlmException.Stop.Kind.CONFIG, e.kind)
            assertEquals("Invalid base URL", e.message)
        }
    }

    @Test fun okWithoutContentFailsWithoutRetry() = runTest {
        server.enqueue(MockResponse(code = 200, body = """{"choices":[]}""")); server.enqueue(MockResponse(code = 200, body = "<html>"))
        assertFailsWith<LlmException.Failed> { client().complete("s", "u", schema, 100) }
        assertFailsWith<LlmException.Failed> { client().complete("s", "u", schema, 100) }
        assertEquals(2, server.requestCount)
    }

    @Test fun errorMessageHasCodeAndShortExcerpt() = runTest {
        server.enqueue(MockResponse(code = 402, body = "insufficient credits " + "z".repeat(1000)))
        val message = assertFailsWith<LlmException.Stop> { client().complete("s", "u", schema, 100) }.message!!
        assertTrue(message, "402" in message && "insufficient credits" in message)
        assertTrue(message, message.length < 260)
    }

    @Test fun echoedKeyIsRedacted() = runTest {
        server.enqueue(MockResponse(code = 401, body = """{"error":"key $apiKey is not valid"}"""))
        val message = assertFailsWith<LlmException.Stop> { client().complete("s", "u", schema, 100) }.message!!
        assertFalse(message, apiKey in message)
        assertTrue(message, "key *** is not valid" in message)
    }

    @Test fun malformedKeyStopsWithoutSending() = runTest {
        val key = "sk-or-pasted\n"
        val e = assertFailsWith<LlmException.Stop> { client(key = key).complete("s", "u", schema, 100) }
        assertEquals(LlmException.Stop.Kind.AUTH, e.kind)
        assertFalse(e.message!!, key.trim() in e.message!!)
        assertEquals(0, server.requestCount)
    }

    @Test fun cancellationIsNotFailure() = runTest {
        server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.Stall).build())
        var thrown: Throwable? = null
        val job = launch(start = CoroutineStart.UNDISPATCHED) { try { client().complete("s", "u", schema, 100) } catch (e: Throwable) { thrown = e; throw e } }
        server.takeRequest()
        job.cancelAndJoin()
        assertTrue("$thrown", thrown is CancellationException)
    }
}

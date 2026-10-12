package org.lattenwald.moonvocab.data.llm

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ModelCatalogTest {
    private val server = MockWebServer()
    private val catalog = ModelCatalog(OkHttpClient.Builder().retryOnConnectionFailure(false).build())

    @Before fun start() = server.start()
    @After fun stop() = server.close()

    private fun baseUrl() = server.url("/api/v1/").toString()

    @Test fun parsesIdsSortedWithoutRepeats() = runTest {
        server.enqueue(MockResponse(code = 200, body = """{"data":[{"id":"z/model"},{"id":"a/model"},{"id":"z/model"},{"name":"no id"}]}"""))
        assertEquals(listOf("a/model", "z/model"), catalog.models(baseUrl(), "sk-test"))
    }

    @Test fun sendsTheKeyAsBearer() = runTest {
        repeat(2) { server.enqueue(MockResponse(code = 200, body = """{"data":[]}""")) }
        catalog.models(baseUrl(), "sk-test")
        catalog.models(baseUrl(), " ")
        assertEquals(2, server.requestCount)
        val withKey = server.takeRequest()
        assertEquals("/api/v1/models", withKey.url.encodedPath)
        assertEquals("Bearer sk-test", withKey.headers["Authorization"])
        assertNull(server.takeRequest().headers["Authorization"])
    }

    @Test fun failureGivesAnEmptyList() = runTest {
        server.enqueue(MockResponse(code = 401, body = """{"data":[{"id":"a/model"}]}"""))
        assertEquals(emptyList<String>(), catalog.models(baseUrl(), "sk-test"))
        server.enqueue(MockResponse(code = 200, body = "not json"))
        assertEquals(emptyList<String>(), catalog.models(baseUrl(), "sk-test"))
        assertEquals(emptyList<String>(), catalog.models("nonsense", "sk-test"))
    }
}

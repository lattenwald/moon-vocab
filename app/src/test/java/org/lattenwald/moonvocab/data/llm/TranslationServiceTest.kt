package org.lattenwald.moonvocab.data.llm

import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
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
import org.lattenwald.moonvocab.domain.card.EnglishAccent.AMERICAN
import org.lattenwald.moonvocab.domain.card.EnglishAccent.BRITISH
import org.lattenwald.moonvocab.domain.card.Translation
import org.lattenwald.moonvocab.domain.card.TranslationItem
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TranslationServiceTest {
    private val server = MockWebServer()

    @Before fun start() = server.start()
    @After fun stop() = server.close()

    private fun service() = TranslationService(LlmClient(
        OkHttpClient.Builder().retryOnConnectionFailure(false).build(),
        { LlmConfig(server.url("/api/v1/").toString(), "sk-test", "test/model") },
        {},
    ))

    private fun reply(content: String) = server.enqueue(MockResponse(code = 200, body = buildJsonObject {
        putJsonArray("choices") { addJsonObject { putJsonObject("message") { put("content", content) } } }
    }.toString()))

    private fun item(id: String, focus: String) = buildJsonObject {
        put("id", id); put("sourceLanguage", "en"); put("focus", focus); put("headword", focus); put("lemma", focus)
        put("widened", false); put("grammar", "n."); put("translation", "тоска"); put("meaning", "")
        put("excerpt", JsonNull); put("inventedExample", JsonNull); put("sourceNote", JsonNull)
    }.toString()

    private fun ti(id: String) = TranslationItem(id, "w", "a w b", "Book", null)

    @Test fun parsesItemsAndIgnoresUnknownAndDuplicateIds() = runTest {
        reply("""{"items":[${item("a","hypos")},${item("zz","x")},${item("a","second")}]}""")
        val r = service().translate(listOf(TranslationItem("a","hypos","…whenever my hypos get…","Moby-Dick",null),
                                           TranslationItem("b","spleen",null,"Moby-Dick",null)), "ru", 15, AMERICAN)
        assertEquals(setOf("a"), r.keys); assertEquals("hypos", r["a"]!!.focus)
    }

    @Test fun extractsJsonFromProse() = runTest { reply("Sure! {\"items\":[${item("a","w")}]} Hope this helps"); assertEquals(1, service().translate(listOf(ti("a")), "ru", 15, AMERICAN).size) }

    @Test fun malformedJsonIsEmpty() = runTest { reply("{\"items\":[{\"id\":\"a\""); assertTrue(service().translate(listOf(ti("a")), "ru", 15, AMERICAN).isEmpty()) }

    @Test fun promptCarriesTargetLimitAndAccent() = runTest { reply("""{"items":[]}"""); service().translate(listOf(ti("a")), "ru", 12, BRITISH)
        val body = server.takeRequest().body!!.utf8(); assertTrue("into Russian" in body); assertTrue("more than 12 words" in body)
        assertTrue("the British (Received Pronunciation) pronunciation" in body) }

    @Test fun fillsOnlyTheBatchsOwnValidIds() = runTest {
        val malformed = """{"id":"b","focus":["w"],"headword":"w","translation":"t"}"""
        reply("""{"items":[${item("a","first")},${item("zz","stranger")},${item("a","again")},42,$malformed,${item("c","  ")},${item("d","fine")}]}""")
        val r = service().translate(listOf(ti("a"), ti("b"), ti("c"), ti("d"), ti("e")), "ru", 15, AMERICAN)
        assertEquals(setOf("a", "d"), r.keys)
        assertEquals("first", r["a"]!!.focus)
        assertEquals("fine", r["d"]!!.focus)
    }

    @Test fun bracesInsideStringsDoNotEndTheObject() = runTest {
        reply("""Here you go: {"items":[{"id":"a","focus":"w","headword":"w","translation":"t","meaning":"a \" } here"}]} Done :}""")
        assertEquals("a \" } here",service().translate(listOf(ti("a")), "ru", 15, AMERICAN)["a"]?.meaning)
    }

    @Test fun trimsFieldsAndFillsDefaults() = runTest {
        reply("""{"items":[{"id":"a","focus":" Schnallen ","headword":"die Schnalle\n","transcription":" /di 'ʃnalə/ ","lemma":"  ","grammar":" f., -n ","translation":" пряжка ","excerpt":null,"sourceNote":"  "}]}""")
        val expected = Translation("a", null, "Schnallen", "die Schnalle", "di ˈʃnalə", "die Schnalle", false, "f., -n", "пряжка", "", null, null, null)
        assertEquals(expected, service().translate(listOf(ti("a")), "ru", 15, AMERICAN)["a"])
    }

    @Test fun sendsItemsWithExplicitNullsAndTokenBudget() = runTest {
        reply("""{"items":[]}""")
        service().translate(listOf(TranslationItem("a", "hypos", "my hypos", "Moby-Dick", "en"), TranslationItem("b", "spleen", null, "Moby-Dick", null)), "ru", 15, AMERICAN)
        val body = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertEquals(2800, body["max_tokens"]!!.jsonPrimitive.int)
        val expected = Json.parseToJsonElement("""[
            {"id":"a","marked":"hypos","sentence":"my hypos","bookTitle":"Moby-Dick","sourceLanguage":"en"},
            {"id":"b","marked":"spleen","sentence":null,"bookTitle":"Moby-Dick","sourceLanguage":null}]""")
        assertEquals(expected, Json.parseToJsonElement(body["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonPrimitive.content))
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
        check(Prompt.schema)
        val item = Prompt.schema["properties"]!!.jsonObject["items"]!!.jsonObject["items"]!!.jsonObject
        assertEquals(13, item["properties"]!!.jsonObject.size)
    }

    @Test fun llmStopPropagates() = runTest {
        server.enqueue(MockResponse(code = 402))
        assertFailsWith<LlmException.Stop> { service().translate(listOf(ti("a")), "ru", 15, AMERICAN) }
    }
}

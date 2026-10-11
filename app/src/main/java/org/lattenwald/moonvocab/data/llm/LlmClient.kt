package org.lattenwald.moonvocab.data.llm

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

data class LlmConfig(val baseUrl: String, val apiKey: String, val model: String)

sealed class LlmException(msg: String) : Exception(msg) {
    class Stop(val kind: Kind, msg: String) : LlmException(msg) { enum class Kind { AUTH, CREDITS, CONFIG } }
    class Failed(msg: String) : LlmException(msg)
}

class LlmClient(
    private val http: OkHttpClient,
    private val config: suspend () -> LlmConfig,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun complete(system: String, user: String, schema: JsonObject, maxTokens: Int): String {
        val cfg = config()
        val url = (cfg.baseUrl.trimEnd('/') + "/chat/completions").toHttpUrlOrNull()
            ?: throw LlmException.Stop(LlmException.Stop.Kind.CONFIG, "Invalid base URL")
        var reply = sendWithRetries(url, cfg.apiKey, requestBody(cfg.model, system, user, maxTokens, schema))
        if (reply.code == 400 && ("response_format" in reply.body || "json_schema" in reply.body)) {
            reply = sendWithRetries(url, cfg.apiKey, requestBody(cfg.model, system, user, maxTokens, schema = null))
        }
        return when (reply.code) {
            in 200..299 -> content(reply.body, cfg.apiKey)
            401, 403 -> throw LlmException.Stop(LlmException.Stop.Kind.AUTH, describe(reply, cfg.apiKey))
            402 -> throw LlmException.Stop(LlmException.Stop.Kind.CREDITS, describe(reply, cfg.apiKey))
            // A wrong path, or OpenRouter's answer for an unknown model.
            404 -> throw LlmException.Stop(LlmException.Stop.Kind.CONFIG, describe(reply, cfg.apiKey))
            else -> throw LlmException.Failed(describe(reply, cfg.apiKey))
        }
    }

    private class Reply(val code: Int, val body: String, val retryAfter: String?)

    private suspend fun sendWithRetries(url: HttpUrl, apiKey: String, json: String): Reply {
        val builder = Request.Builder().url(url).post(json.toRequestBody(JSON))
        try {
            builder.header("Authorization", "Bearer $apiKey")
        } catch (_: IllegalArgumentException) {
            throw LlmException.Stop(LlmException.Stop.Kind.AUTH, "API key contains invalid characters")
        }
        val request = builder.build()
        var retry = 0
        while (true) {
            val reply = try {
                execute(request)
            } catch (e: IOException) {
                if (retry == BACKOFF_MS.size) throw LlmException.Failed("Network error: ${e.message ?: e.javaClass.simpleName}")
                sleep(BACKOFF_MS[retry])
                retry++
                continue
            }
            if (reply.code != 429 && reply.code !in 500..599) return reply
            if (retry == BACKOFF_MS.size) throw LlmException.Failed(describe(reply, apiKey))
            sleep(retryAfterMs(reply.retryAfter) ?: BACKOFF_MS[retry])
            retry++
        }
    }

    private suspend fun execute(request: Request): Reply = suspendCancellableCoroutine { cont ->
        val call = http.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)

            override fun onResponse(call: Call, response: Response) {
                val reply = try {
                    response.use { Reply(it.code, it.body.string(), it.header("Retry-After")) }
                } catch (e: IOException) {
                    cont.resumeWithException(e)
                    return
                } catch (e: Throwable) {
                    cont.resumeWithException(LlmException.Failed("Could not read the response: ${e.javaClass.simpleName}"))
                    return
                }
                cont.resume(reply)
            }
        })
    }

    private fun requestBody(model: String, system: String, user: String, maxTokens: Int, schema: JsonObject?): String =
        buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                addJsonObject { put("role", "system"); put("content", system) }
                addJsonObject { put("role", "user"); put("content", user) }
            }
            put("max_tokens", maxTokens)
            if (schema != null) putJsonObject("response_format") {
                put("type", "json_schema")
                putJsonObject("json_schema") {
                    put("name", "cards")
                    put("strict", true)
                    put("schema", schema)
                }
            }
        }.toString()

    private fun content(body: String, apiKey: String): String {
        val content = try {
            Json.parseToJsonElement(body).jsonObject["choices"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
        } catch (_: IllegalArgumentException) {
            null
        }
        return content ?: throw LlmException.Failed("Response has no message content: ${excerpt(body, apiKey)}")
    }

    private fun describe(reply: Reply, apiKey: String) = "HTTP ${reply.code}: ${excerpt(reply.body, apiKey)}"

    private fun excerpt(body: String, apiKey: String): String {
        val redacted = if (apiKey.isBlank()) body else body.replace(apiKey, "***")
        val flat = redacted.replace(WHITESPACE, " ").trim()
        return if (flat.length <= EXCERPT_CHARS) flat else flat.take(EXCERPT_CHARS) + "..."
    }

    private fun retryAfterMs(header: String?): Long? =
        header?.trim()?.toLongOrNull()?.takeIf { it >= 0 }?.coerceAtMost(MAX_RETRY_AFTER_S)?.times(1000)

    private companion object {
        val JSON = "application/json".toMediaType()
        val BACKOFF_MS = longArrayOf(2_000, 8_000)
        const val MAX_RETRY_AFTER_S = 60L
        const val EXCERPT_CHARS = 200
        val WHITESPACE = Regex("\\s+")
    }
}

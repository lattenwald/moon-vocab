package org.lattenwald.moonvocab.data.llm

import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class ModelCatalog(private val http: OkHttpClient) {
    suspend fun models(baseUrl: String, apiKey: String): List<String> {
        val url = (baseUrl.trimEnd('/') + "/models").toHttpUrlOrNull() ?: return emptyList()
        val builder = Request.Builder().url(url).get()
        if (apiKey.isNotBlank()) {
            try {
                builder.header("Authorization", "Bearer $apiKey")
            } catch (_: IllegalArgumentException) {
                return emptyList()
            }
        }
        val body = fetch(builder.build()) ?: return emptyList()
        return ids(body)
    }

    private suspend fun fetch(request: Request): String? = suspendCancellableCoroutine { cont ->
        val call = http.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = cont.resume(null)

            override fun onResponse(call: Call, response: Response) {
                val body = try {
                    response.use { if (it.isSuccessful) it.body.string() else null }
                } catch (_: Throwable) {
                    null
                }
                cont.resume(body)
            }
        })
    }

    private fun ids(body: String): List<String> {
        val data = try {
            Json.parseToJsonElement(body).jsonObject["data"]?.jsonArray
        } catch (_: IllegalArgumentException) {
            null
        } ?: return emptyList()
        return data.mapNotNull { entry ->
            ((entry as? JsonObject)?.get("id") as? JsonPrimitive)?.takeIf { it.isString }?.content?.ifBlank { null }
        }.distinct().sorted()
    }
}

package org.lattenwald.moonvocab.data.llm

import okhttp3.OkHttpClient
import org.lattenwald.moonvocab.data.settings.LlmService
import org.lattenwald.moonvocab.domain.card.TranslationItem

sealed interface TestResult {
    data class Ok(val millis: Long, val word: String, val translation: String) : TestResult
    data class Failed(val kind: LlmException.Stop.Kind?, val message: String) : TestResult
    data object NoTranslation : TestResult
}

class ConnectionTest(private val http: OkHttpClient, private val clock: () -> Long) {
    suspend fun run(service: LlmService, targetLanguage: String): TestResult {
        val config = LlmConfig(service.baseUrl, service.apiKey, service.model)
        val translator = TranslationService(LlmClient(http, { config }, sleep = {}))
        val start = clock()
        val translation = try {
            translator.translate(listOf(SAMPLE), targetLanguage, MAX_EXCERPT_WORDS)[SAMPLE.id]
        } catch (e: LlmException.Stop) {
            return TestResult.Failed(e.kind, e.message.orEmpty())
        } catch (e: LlmException.Failed) {
            return TestResult.Failed(null, e.message.orEmpty())
        }
        val millis = clock() - start
        return translation?.let { TestResult.Ok(millis, it.headword, it.translation) }
            ?: TestResult.NoTranslation
    }

    private companion object {
        val SAMPLE = TranslationItem(
            id = "test",
            marked = "Schnallen",
            sentence = "Der Mantel hatte viele Taschen und Schnallen.",
            bookTitle = "Test",
            sourceLanguage = "de",
        )
        const val MAX_EXCERPT_WORDS = 15
    }
}

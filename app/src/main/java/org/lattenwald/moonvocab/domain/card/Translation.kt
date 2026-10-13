package org.lattenwald.moonvocab.domain.card

data class Translation(
    val id: String,
    val sourceLanguage: String?,
    val focus: String,
    val headword: String,
    val transcription: String,
    val lemma: String,
    val widened: Boolean,
    val grammar: String,
    val translation: String,
    val meaning: String,
    val excerpt: String?,
    val inventedExample: String?,
    val sourceNote: String?,
)

data class TranslationItem(
    val id: String,
    val marked: String,
    val sentence: String?,
    val bookTitle: String,
    val sourceLanguage: String?,
)

enum class EnglishAccent { AMERICAN, BRITISH }

// Models sometimes wrap IPA in /slashes/ or [brackets] and mark stress with an apostrophe.
fun cleanTranscription(raw: String): String =
    raw.trim().removeSurrounding("/").removeSurrounding("[", "]").trim().replace('\'', 'ˈ').replace(WHITESPACE, " ")

private val WHITESPACE = Regex("\\s+")

fun interface Translator {
    suspend fun translate(
        items: List<TranslationItem>,
        targetLanguage: String,
        maxExcerptWords: Int,
        accent: EnglishAccent,
    ): Map<String, Translation>
}

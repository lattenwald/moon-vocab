package org.lattenwald.moonvocab.domain.card

data class Translation(
    val id: String,
    val sourceLanguage: String?,
    val focus: String,
    val headword: String,
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

fun interface Translator {
    suspend fun translate(items: List<TranslationItem>, targetLanguage: String, maxExcerptWords: Int): Map<String, Translation>
}

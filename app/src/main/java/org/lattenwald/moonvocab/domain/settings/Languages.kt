package org.lattenwald.moonvocab.domain.settings

import java.text.Collator
import java.util.Locale

val LANGUAGE_CODES: List<String> = listOf(
    "af", "am", "ar", "az", "be", "bg", "bn", "bs", "ca", "cs", "cy", "da", "de", "el", "en", "eo", "es", "et", "eu", "fa",
    "fi", "fil", "fr", "ga", "gl", "gu", "he", "hi", "hr", "hu", "hy", "id", "is", "it", "ja", "ka", "kk", "km", "kn", "ko",
    "ky", "la", "lo", "lt", "lv", "mk", "ml", "mn", "mr", "ms", "mt", "my", "nb", "ne", "nl", "pa", "pl", "ps", "pt", "ro",
    "ru", "si", "sk", "sl", "sq", "sr", "sv", "sw", "ta", "te", "tg", "th", "tk", "tr", "uk", "ur", "uz", "vi", "yi", "zh",
)

private val RUSSIAN = Locale.forLanguageTag("ru")

fun suggestedLanguages(current: String, device: List<String>): List<String> =
    (listOf(current) + device + "en").filter { it in LANGUAGE_CODES }.distinct()

fun searchLanguages(codes: List<String>, query: String, ui: Locale): List<String> {
    val q = query.trim()
    if (q.isEmpty()) return sortedLanguages(codes, ui)
    val matches = codes.filter { code ->
        val locale = Locale.forLanguageTag(code)
        listOf(locale.getDisplayLanguage(ui), locale.getDisplayLanguage(locale), locale.getDisplayLanguage(Locale.ENGLISH), locale.getDisplayLanguage(RUSSIAN), code)
            .any { it.contains(q, ignoreCase = true) }
    }
    return sortedLanguages(matches, ui)
}

fun sortedLanguages(codes: List<String>, ui: Locale): List<String> {
    val collator = Collator.getInstance(ui)
    return codes.sortedWith(compareBy(collator) { Locale.forLanguageTag(it).getDisplayLanguage(ui) })
}

package org.lattenwald.moonvocab.domain.card

import java.util.Locale

const val EXTRA_TAG = "moon-vocab"

fun deckName(pattern: String, sourceLanguage: String?, override: String?): String {
    if (!override.isNullOrBlank()) return override
    val name = sourceLanguage?.let { Locale.forLanguageTag(it.replace('_', '-')).getDisplayLanguage(Locale.ENGLISH) }
    return pattern.replace("{language}", if (name.isNullOrBlank()) "Other" else name)
}

fun bookTags(pattern: String, title: String): Set<String> {
    val sanitized = title.map { if (it.isLetterOrDigit() || it == '-') it else '_' }.joinToString("")
    return setOf(pattern.replace("{title}", sanitized), EXTRA_TAG)
}

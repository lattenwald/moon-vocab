package org.lattenwald.moonvocab.domain.card

import java.util.Locale
import org.lattenwald.moonvocab.domain.text.normalizeForMatch

private val whitespace = Regex("\\s+")

fun senseKey(lemma: String): String = normalizeForMatch(lemma).trim().replace(whitespace, " ").lowercase(Locale.ROOT)

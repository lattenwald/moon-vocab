package org.lattenwald.moonvocab.domain.card

import org.lattenwald.moonvocab.data.anki.noteFields

// The book's sentence runs on past both ends, hence the ellipses.
private const val SENTENCE = "…and especially whenever my hypos get such an upper hand of me, that it requires a strong moral principle " +
    "to prevent me from deliberately stepping into the street, and methodically knocking people's hats off…"
private const val FOCUS = "upper hand"
private const val BOOK = "Moby-Dick, Herman Melville"

fun sampleCard(maxExcerptWords: Int, accent: EnglishAccent): CardFields {
    val start = SENTENCE.indexOf(FOCUS)
    val end = start + FOCUS.length
    val excerpt = cutExcerpt(SENTENCE, start, end, maxExcerptWords)
    return CardFields(
        word = "get the upper hand",
        transcription = when (accent) {
            EnglishAccent.AMERICAN -> "ɡɛt ði ˈʌpɚ ˈhænd"
            EnglishAccent.BRITISH -> "ɡɛt ði ˈʌpə ˈhænd"
        },
        grammar = "phr.",
        excerptHtml = markHtml(excerpt.text, excerpt.focusStart, excerpt.focusEnd),
        translation = "взять верх, одолеть",
        meaning = "",
        sentenceHtml = markHtml(SENTENCE, start, end),
    )
}

fun sampleCardFields(maxExcerptWords: Int, accent: EnglishAccent): Map<String, String> =
    noteFields(sampleCard(maxExcerptWords, accent), source = "", bookLine = BOOK, appId = "")

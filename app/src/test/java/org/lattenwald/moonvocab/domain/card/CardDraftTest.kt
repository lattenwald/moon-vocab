package org.lattenwald.moonvocab.domain.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lattenwald.moonvocab.domain.text.SentenceResult

private val long = "Er war schlank und doch fest gebaut, er trug ein anliegendes schwarzes Kleid, das mit verschiedenen Falten, Taschen, Schnallen, Knöpfen und einem Gürtel versehen war."
private val SHY = Char(0x00AD)

private fun found(sentence: String, word: String): SentenceResult.Found {
    val start = sentence.indexOf(word)
    return SentenceResult.Found(sentence, start, start + word.length)
}

private fun t(
    focus: String,
    excerpt: String? = null,
    inventedExample: String? = null,
    sourceNote: String? = null,
) = Translation(
    id = "1", sourceLanguage = "de", focus = focus, headword = focus, lemma = focus, widened = false,
    grammar = "", translation = "", meaning = "", excerpt = excerpt, inventedExample = inventedExample, sourceNote = sourceNote,
)

private fun ok(r: DraftResult): CardDraft = (r as DraftResult.Ok).draft

class CardDraftTest {
    @Test fun shortSentenceIsTheExcerpt() {
        val d = (buildDraft(found("It is a way I have of driving off the spleen.", "spleen"), t(focus = "spleen"), 15) as DraftResult.Ok).draft
        assertEquals("It is a way I have of driving off the <span class=\"mark\">spleen</span>.", d.excerptHtml)
    }

    @Test fun validModelExcerptIsUsed() {
        val d = ok(buildDraft(found(long, "Schnallen"), t(focus = "Schnallen", excerpt = "…mit verschiedenen Falten, Taschen, [Schnallen]…"), 15))
        assertEquals("…mit verschiedenen Falten, Taschen, <span class=\"mark\">Schnallen</span>…", d.excerptHtml)
    }

    @Test fun modelExcerptNotInSentenceFallsBackToWindow() {
        val d = ok(buildDraft(found(long, "Schnallen"), t(focus = "Schnallen", excerpt = "…mit [Schnallen] aus Gold…"), 8))
        assertTrue(d.excerptHtml.startsWith("…")); assertTrue(wordCount(d.excerptHtml.replace(Regex("<[^>]+>"), "")) <= 8)
    }

    @Test fun focusMissingIsInvalid() = assertTrue(buildDraft(found(long, "Schnallen"), t(focus = "Gürtelschnalle"), 15) is DraftResult.Invalid)

    @Test fun softHyphenAndLineBreakNeverReachTheCard() {
        val book = "Mit Falten,\n  Taschen, Schnal${SHY}len und Knöpfen."
        val d = ok(buildDraft(found(book, "Schnal${SHY}len"), t(focus = "Schnallen"), 15))
        assertEquals("Mit Falten, Taschen, <span class=\"mark\">Schnallen</span> und Knöpfen.", d.sentenceHtml)
    }

    @Test fun lowercasedFocusKeepsSentenceCasing() {
        val d = ok(buildDraft(found("Spleen is what he drove off.", "Spleen"), t(focus = "spleen"), 15))
        assertEquals("<span class=\"mark\">Spleen</span> is what he drove off.", d.sentenceHtml)
    }

    @Test fun repeatedFocusUsesOccurrenceNearestTheMark() {
        val s = "The mole saw a mole."
        val d = ok(buildDraft(SentenceResult.Found(s, s.lastIndexOf("mole"), s.length - 1), t(focus = "mole"), 15))
        assertEquals("The mole saw a <span class=\"mark\">mole</span>.", d.sentenceHtml)
    }

    @Test fun modelExcerptMarkingAnotherOccurrenceFallsBackToWindowAroundTheMark() {
        val s = "Die Schnallen glänzten, und an seinem Gürtel hingen noch mehr Schnallen aus Messing und Silber."
        val second = s.lastIndexOf("Schnallen")
        val d = ok(buildDraft(SentenceResult.Found(s, second, second + 9), t(focus = "Schnallen", excerpt = "Die [Schnallen] glänzten, und an seinem…"), 8))
        assertEquals("…hingen noch mehr <span class=\"mark\">Schnallen</span> aus Messing und Silber.", d.excerptHtml)
    }

    @Test fun inventedExampleIsEscaped() {
        val d = ok(buildDraft(SentenceResult.NotFound, t(focus = "loiter", inventedExample = "Tom & Jerry [loiter] near <the> pier."), 15))
        assertEquals("Tom &amp; Jerry <span class=\"mark\">loiter</span> near &lt;the&gt; pier.", d.excerptHtml)
    }

    @Test fun inventedExampleWhenNoSentence() {
        val d = ok(buildDraft(SentenceResult.NotFound, t(focus = "loiter", inventedExample = "Tourists [loiter] along the pier at dusk.", sourceNote = "Пример придуман моделью"), 15))
        assertTrue(d.invented); assertEquals("", d.sentenceHtml); assertTrue("<span class=\"mark\">loiter</span>" in d.excerptHtml)
    }

    @Test fun inventedExampleNeedsExactlyOneBracketPair() {
        for (ex in listOf(null, "Tourists loiter.", "[Tourists] [loiter].", "Tourists ]loiter[.", "Tourists [] loiter.")) {
            assertTrue(ex.toString(), buildDraft(SentenceResult.NotFound, t(focus = "loiter", inventedExample = ex), 15) is DraftResult.Invalid)
        }
    }
}

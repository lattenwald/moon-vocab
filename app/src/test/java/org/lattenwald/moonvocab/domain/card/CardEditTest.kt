package org.lattenwald.moonvocab.domain.card

import org.junit.Assert.assertEquals
import org.junit.Test

class CardEditTest {
    private val stored = CardFields(
        word = "get the upper hand", grammar = "phr.",
        excerptHtml = """such an <span class="mark">upper hand</span> of me""",
        translation = "взять верх", meaning = "",
        sentenceHtml = """whenever my hypos get such an <span class="mark">upper hand</span> of me [sic]""",
    )
    private fun note(f: CardFields) = mapOf(
        "Word" to f.word, "Grammar" to f.grammar, "Excerpt" to f.excerptHtml, "Translation" to f.translation,
        "Meaning" to f.meaning, "Sentence" to f.sentenceHtml, "Book" to "Moby-Dick", "AppId" to "u1",
    )

    @Test fun formShowsTheFocusInBrackets() =
        assertEquals("such an [upper hand] of me", stored.toForm().excerptHtml)
    @Test fun anUnchangedFormGivesBackTheStoredHtml() =
        assertEquals(FormResult.Ok(stored), parseForm(stored.toForm(), stored)) // the sentence's literal [sic] must not invalidate it
    @Test fun bracketsBecomeTheMarkAndTypedHtmlIsEscaped() {
        val r = parseForm(stored.toForm().copy(excerptHtml = "a < b [upper hand]"), stored) as FormResult.Ok
        assertEquals("""a &lt; b <span class="mark">upper hand</span>""", r.fields.excerptHtml)
    }
    @Test fun anExcerptWithoutOneBracketPairIsInvalid() {
        for (bad in listOf("no focus", "[a] and [b]", "[ ] x", "a ]b[")) {
            assertEquals(FormResult.Invalid(setOf(CardField.EXCERPT)), parseForm(stored.toForm().copy(excerptHtml = bad), stored))
        }
    }
    @Test fun anEmptySentenceIsValid() =
        assertEquals("", (parseForm(stored.toForm().copy(sentenceHtml = ""), stored) as FormResult.Ok).fields.sentenceHtml)
    @Test fun blankWordAndTranslationAreInvalid() = assertEquals(
        FormResult.Invalid(setOf(CardField.WORD, CardField.TRANSLATION)),
        parseForm(stored.toForm().copy(word = " ", translation = ""), stored),
    )
    @Test fun planWritesOnlyChangedFieldsEscaped() {
        val edited = stored.copy(translation = "одолеть <редк.>")
        assertEquals(
            SavePlan.Write(mapOf("Translation" to "одолеть &lt;редк.&gt;"), edited),
            planSave(stored, edited, note(stored)),
        )
    }
    @Test fun planKeepsAnkiEditsToUntouchedFields() {
        val edited = stored.copy(translation = "одолеть")
        val plan = planSave(stored, edited, note(stored.copy(meaning = "<b>to win</b>"))) as SavePlan.Write
        assertEquals(setOf("Translation"), plan.anki.keys)
        assertEquals("to win", plan.result.meaning)
        assertEquals("одолеть", plan.result.translation)
    }
    @Test fun planReportsAConflictOnAFieldChangedOnBothSides() {
        val edited = stored.copy(translation = "одолеть", grammar = "idiom")
        val inAnki = stored.copy(translation = "взять верх, одержать верх")
        assertEquals(
            SavePlan.Conflict(setOf(CardField.TRANSLATION), inAnki),
            planSave(stored, edited, note(inAnki)),
        )
    }
    @Test fun planWithNoChangesWritesNothing() =
        assertEquals(SavePlan.Write(emptyMap(), stored), planSave(stored, stored, note(stored)))
    @Test fun mergeAfterConflictKeepsEditedFieldsAndTakesFreshForTheRest() {
        val form = stored.toForm().copy(grammar = "idiom", translation = "одолеть")
        val fresh = stored.copy(translation = "взять верх, одержать верх", meaning = "to win")
        assertEquals(
            fresh.toForm().copy(grammar = "idiom"),
            mergeAfterConflict(form, stored, fresh, setOf(CardField.TRANSLATION)),
        )
    }
    @Test fun aWhitespaceOnlyEditCountsAsUntouched() {
        val form = stored.toForm().copy(word = "get the upper hand ", excerptHtml = " such an [upper hand] of me ", translation = "одолеть")
        val fresh = stored.copy(
            word = "gain the upper hand", excerptHtml = """gain such an <span class="mark">upper hand</span> of me""",
            translation = "взять верх, одержать верх",
        )
        val merged = mergeAfterConflict(form, stored, fresh, setOf(CardField.TRANSLATION))
        assertEquals("gain the upper hand", merged.word)
        assertEquals("gain such an [upper hand] of me", merged.excerptHtml)
    }
    @Test fun aConflictingFieldTakesAnkiDroidsText() {
        val form = stored.toForm().copy(excerptHtml = "such an [upper] hand of me", translation = "одолеть")
        val fresh = stored.copy(excerptHtml = """such a <span class="mark">upper hand</span> of mine""", translation = "взять верх, одержать верх")
        val merged = mergeAfterConflict(form, stored, fresh, setOf(CardField.EXCERPT, CardField.TRANSLATION))
        assertEquals("such a [upper hand] of mine", merged.excerptHtml)
        assertEquals("взять верх, одержать верх", merged.translation)
    }
}

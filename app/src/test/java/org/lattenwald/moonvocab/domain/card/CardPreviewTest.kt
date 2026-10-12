package org.lattenwald.moonvocab.domain.card

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lattenwald.moonvocab.data.anki.NoteTypeSpec

class CardPreviewTest {
    private val fields = mapOf("Word" to "die Schnalle", "Grammar" to "f., -n", "Excerpt" to """eine <span class="mark">Schnalle</span>""",
        "Translation" to "пряжка", "Meaning" to "", "Sentence" to "", "Book" to "Title", "Source" to "")

    @Test fun fieldsAreInsertedAsHtml() = assertTrue(fillTemplate(NoteTypeSpec.FRONT, fields).contains("""<span class="mark">Schnalle</span>"""))

    @Test fun aSectionForABlankFieldIsDropped() = assertFalse(fillTemplate(NoteTypeSpec.BACK, fields).contains("class=\"mean\""))

    @Test fun aSectionForAFilledFieldIsKept() =
        assertTrue(fillTemplate(NoteTypeSpec.BACK, fields + ("Meaning" to "a buckle")).contains("""<div class="mean">a buckle</div>"""))

    @Test fun theBackStartsWithTheFront() {
        val front = fillTemplate(NoteTypeSpec.FRONT, fields)
        assertTrue(fillTemplate(NoteTypeSpec.BACK, fields, front).startsWith(front))
    }

    @Test fun nightModeAddsTheClass() {
        assertTrue(previewHtml(fields, showAnswer = false, night = true, background = "#000000", text = "#FFFFFF").contains("class=\"card nightMode\""))
        assertTrue(previewHtml(fields, showAnswer = false, night = false, background = "#FFFFFF", text = "#000000").contains("class=\"card\""))
    }
}

package org.lattenwald.moonvocab.data.anki

import kotlin.test.assertFailsWith
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class FieldMergeTest {
    private val ours = mapOf("Word" to "spleen", "Translation" to "хандра")

    @Test fun keepsUnknownFieldsAndOrder() = assertArrayEquals(
        arrayOf("My note", "spleen", "хандра"),
        mergeFields(listOf("Extra", "Word", "Translation"), listOf("My note", "old", "старое"), ours),
    )

    @Test fun newNoteFillsBlanks() = assertArrayEquals(
        arrayOf("spleen", "", "хандра"),
        mergeFields(listOf("Word", "Extra", "Translation"), null, ours),
    )

    @Test fun aNoteTypeWithoutTranscriptionTakesTheRest() = assertArrayEquals(
        arrayOf("spleen", "хандра"),
        mergeFields(listOf("Word", "Translation"), null, ours + ("Transcription" to "spliːn")),
    )

    @Test fun missingFieldThrows() {
        assertEquals(listOf("Translation"), assertFailsWith<NoteTypeChanged> { mergeFields(listOf("Word"), null, ours) }.missing)
    }
}

package org.lattenwald.moonvocab.domain.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lattenwald.moonvocab.data.anki.NoteSnapshot

class ReconcileTest {
    private val f = mapOf("Word" to "spleen", "Translation" to "хандра")

    @Test fun missingNote() = assertEquals(listOf(ReconcileAction.Missing(1)), reconcile(listOf(SentCard(1, 10, f)), emptyMap()))

    @Test fun editedInAnki() {
        val g = f + ("Translation" to "сплин")
        assertEquals(
            listOf(ReconcileAction.Refresh(1, g, 5)),
            reconcile(listOf(SentCard(1, 10, f)), mapOf(10L to NoteSnapshot(g, emptySet(), 5))),
        )
    }

    @Test fun unchanged() =
        assertTrue(reconcile(listOf(SentCard(1, 10, f)), mapOf(10L to NoteSnapshot(f + ("Extra" to "x"), emptySet(), 9))).isEmpty())

    @Test fun refreshCarriesOnlyOurFields() {
        val g = f + ("Translation" to "сплин")
        assertEquals(
            listOf(ReconcileAction.Refresh(1, g, 5)),
            reconcile(listOf(SentCard(1, 10, f)), mapOf(10L to NoteSnapshot(g + ("Extra" to "x"), emptySet(), 5))),
        )
    }

    @Test fun fieldOnlyOnOneSideCounts() {
        val withMeaning = f + ("Meaning" to "gloom")
        assertEquals(
            listOf(ReconcileAction.Refresh(1, f, 5)),
            reconcile(listOf(SentCard(1, 10, withMeaning)), mapOf(10L to NoteSnapshot(f, emptySet(), 5))),
        )
    }

    @Test fun eachCardJudgedByItsOwnNote() = assertEquals(
        listOf(ReconcileAction.Missing(2)),
        reconcile(listOf(SentCard(1, 10, f), SentCard(2, 20, f)), mapOf(10L to NoteSnapshot(f, emptySet(), 5))),
    )
}

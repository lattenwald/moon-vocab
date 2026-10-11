package org.lattenwald.moonvocab.domain.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lattenwald.moonvocab.data.anki.NoteSnapshot

class ReconcileTest {
    private val f = mapOf("Word" to "spleen", "Translation" to "хандра")

    private fun unchanged(ours: Map<String, String>, theirs: Map<String, String>) =
        assertTrue(reconcile(listOf(SentCard(1, 10, ours)), mapOf(10L to NoteSnapshot(theirs, emptySet(), 5))).isEmpty())

    private fun refreshed(ours: Map<String, String>, theirs: Map<String, String>) = assertEquals(
        listOf(ReconcileAction.Refresh(1, theirs, 5)),
        reconcile(listOf(SentCard(1, 10, ours)), mapOf(10L to NoteSnapshot(theirs, emptySet(), 5))),
    )

    @Test fun missingNote() = assertEquals(listOf(ReconcileAction.Missing(1)), reconcile(listOf(SentCard(1, 10, f)), emptyMap()))

    @Test fun editedInAnki() = refreshed(f, f + ("Translation" to "сплин"))

    @Test fun unchanged() = unchanged(f, f + ("Extra" to "x"))

    @Test fun refreshCarriesOnlyStoredFields() {
        val g = f + ("Translation" to "сплин")
        assertEquals(
            listOf(ReconcileAction.Refresh(1, g, 5)),
            reconcile(
                listOf(SentCard(1, 10, f)),
                mapOf(10L to NoteSnapshot(g + mapOf("Extra" to "x", "Book" to "Diaspora", "AppId" to "x"), emptySet(), 5)),
            ),
        )
    }

    @Test fun formattingOnlyChangeIsNotARefresh() = unchanged(f + ("Translation" to "пряжка"), f + ("Translation" to "<b>пряжка</b>"))

    @Test fun escapedTextComparesEqual() = unchanged(f + ("Translation" to "a & b"), f + ("Translation" to "a &amp; b"))

    @Test fun bookAndAppIdAreNotCompared() =
        unchanged(f + mapOf("Book" to "Diaspora, Greg Egan", "AppId" to "uuid"), f + mapOf("Book" to "Diaspora", "AppId" to "x"))

    @Test fun fieldRemovedInAnkiIsNotARefresh() = unchanged(f + ("Meaning" to "gloom"), f)

    @Test fun fieldWeLeftEmptyCounts() = refreshed(f + ("Meaning" to ""), f + ("Meaning" to "gloom"))

    @Test fun plainTextEditIsRefreshed() = refreshed(f + ("Translation" to "пряжка"), f + ("Translation" to "застёжка"))

    @Test fun excerptHtmlEditIsRefreshed() = refreshed(
        f + ("Excerpt" to "the <span class=\"mark\">spleen</span>"),
        f + ("Excerpt" to "the <b>spleen</b>"),
    )

    @Test fun eachCardJudgedByItsOwnNote() = assertEquals(
        listOf(ReconcileAction.Missing(2)),
        reconcile(listOf(SentCard(1, 10, f), SentCard(2, 20, f)), mapOf(10L to NoteSnapshot(f, emptySet(), 5))),
    )
}

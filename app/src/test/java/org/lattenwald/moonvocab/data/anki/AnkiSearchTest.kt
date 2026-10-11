package org.lattenwald.moonvocab.data.anki

import org.junit.Assert.assertEquals
import org.junit.Test

class AnkiSearchTest {
    @Test fun plainAndPhraseTerms() {
        assertEquals("\"w:Schnalle\"", wordSearch("Schnalle"))
        assertEquals("\"w:get the upper hand\"", wordSearch("get the upper hand"))
    }

    @Test fun specialCharactersAreEscaped() = assertEquals("\"w:a\\*b\\_c\\\\d\\\"e\\:f\\(g\\)\"", wordSearch("a*b_c\\d\"e:f(g)"))

    @Test fun leadingDashIsEscaped() = assertEquals("\"w:\\-ish\"", wordSearch("-ish"))

    @Test fun elsewhereQueryExcludesOurNoteTypeAndDedupes() = assertEquals(
        "-note:\"Moon+ Vocab\" (\"w:Schnalle\" OR \"w:Schnallen\")",
        elsewhereQuery(listOf("Schnalle", "Schnallen", "schnalle")),
    )

    @Test fun appIdQueryByMid() = assertEquals("mid:42 (AppId:a-1 OR AppId:b-2)", appIdQuery(42, listOf("a-1", "b-2")))
}

package org.lattenwald.moonvocab.domain.settings

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguagesTest {
    @Test fun suggestedPutsCurrentFirstThenDeviceThenEnglishWithoutRepeats() {
        assertEquals(listOf("ru", "bg", "en"), suggestedLanguages("ru", listOf("bg", "ru", "xx")))
    }

    @Test fun searchMatchesRussianNativeEnglishNamesAndCode() {
        for (query in listOf("нем", "Deutsch", "germ", "de")) {
            assertTrue(query, "de" in searchLanguages(LANGUAGE_CODES, query, Locale.ENGLISH))
        }
    }

    @Test fun searchMatchesTheCodeAlone() {
        assertTrue("zh" in searchLanguages(LANGUAGE_CODES, "zh", Locale.ENGLISH))
    }

    @Test fun searchWithNoMatchIsEmpty() {
        assertEquals(emptyList<String>(), searchLanguages(LANGUAGE_CODES, "qqq", Locale.ENGLISH))
    }

    @Test fun sortFollowsTheUiLocale() {
        // Испанский < Немецкий; by code, English name or own name the order is reversed.
        val sorted = sortedLanguages(listOf("de", "es"), Locale.forLanguageTag("ru"))
        assertEquals(listOf("es", "de"), sorted)
    }
}

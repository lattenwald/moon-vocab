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

    @Test fun searchWithNoMatchIsEmpty() {
        assertEquals(emptyList<String>(), searchLanguages(LANGUAGE_CODES, "qqq", Locale.ENGLISH))
    }

    @Test fun sortFollowsTheUiLocale() {
        val sorted = sortedLanguages(listOf("de", "en"), Locale.forLanguageTag("ru"))
        assertEquals(listOf("en", "de"), sorted)
    }
}

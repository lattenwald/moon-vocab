package org.lattenwald.moonvocab.domain.card

import org.junit.Assert.assertEquals
import org.junit.Test

class DeckAndTagsTest {
    @Test fun deckUsesEnglishLanguageName() = assertEquals("Moon+::German", deckName("Moon+::{language}", "de", null))

    @Test fun overrideWins() = assertEquals("Moon+::Test", deckName("Moon+::{language}", "de", "Moon+::Test"))

    @Test fun blankOverrideIsNoOverride() = assertEquals("Moon+::German", deckName("Moon+::{language}", "de", " "))

    @Test fun unknownLanguage() = assertEquals("Moon+::Other", deckName("Moon+::{language}", null, null))

    @Test fun underscoreTagParses() = assertEquals("Moon+::Chinese", deckName("Moon+::{language}", "zh_CN", null))

    @Test fun unparsableCodeIsOther() = assertEquals(
        listOf("Moon+::Other", "Moon+::Other", "Moon+::Other"),
        listOf("x", "und", " ").map { deckName("Moon+::{language}", it, null) },
    )

    @Test fun tagSanitized() =
        assertEquals(setOf("book::A_Hacker_s_Mind__2023_", "moon-vocab"), bookTags("book::{title}", "A Hacker${Char(0x2019)}s Mind (2023)"))

    @Test fun tagKeepsCyrillicLetters() =
        assertEquals(setOf("book::Карибский_кризис", "moon-vocab"), bookTags("book::{title}", "Карибский кризис"))
}

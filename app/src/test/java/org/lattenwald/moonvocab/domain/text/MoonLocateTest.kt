package org.lattenwald.moonvocab.domain.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoonLocateTest {
    private val part = "A grim one. " + "Filler. ".repeat(40) + "A grim two."
    private val second = part.lastIndexOf("grim")

    @Test fun noteAtItsPositionIsThere() = assertEquals(second, moonNoteStart(part, second, "grim"))

    @Test fun movedNoteIsReanchoredToTheNearestOccurrenceWithinMoonsWindow() {
        assertEquals(second, moonNoteStart(part, second - 30, "grim"))
        assertEquals(2, moonNoteStart(part, 40, "grim"))
    }

    @Test fun noteFurtherThanTheWindowIsNotShown() = assertNull(moonNoteStart(part, second + 250, "grim"))
}

package org.lattenwald.moonvocab.domain.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SameSenseTest {
    private val nbsp = Char(0xA0)
    private val iDiaeresis = Char(0xEF)
    private val combiningDiaeresis = Char(0x308)
    private val rightQuote = Char(0x2019)

    @Test fun senseKeyIgnoresCaseSpacingUnicodeFormAndQuotes() {
        assertEquals(senseKey("schnalle"), senseKey(" Schnalle "))
        assertEquals(senseKey("get the upper hand"), senseKey("get  the${nbsp}upper hand"))
        assertEquals(senseKey("na${iDiaeresis}ve"), senseKey("nai${combiningDiaeresis}ve"))
        assertEquals(senseKey("don't"), senseKey("don${rightQuote}t"))
        assertNotEquals(senseKey("Schnalle"), senseKey("Schnallen"))
    }
}

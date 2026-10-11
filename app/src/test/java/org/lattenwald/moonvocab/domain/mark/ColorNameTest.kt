package org.lattenwald.moonvocab.domain.mark

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorNameTest {
    @Test fun moonReaderColorsHaveNames() {
        assertEquals(ColorName.ORANGE, colorName(0xFFFF9200.toInt())); assertEquals(ColorName.YELLOW, colorName(0xFFFFEB3B.toInt()))
        assertEquals(ColorName.BLUE, colorName(0xFF3B7BE0.toInt())); assertEquals(ColorName.GRAY, colorName(0xFF808080.toInt()))
        assertEquals(ColorName.RED, colorName(0xFFE53935.toInt())); assertEquals(ColorName.GREEN, colorName(0xFF43A047.toInt()))
    }
}

package org.lattenwald.moonvocab.domain.mark

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorNameTest {
    @Test fun moonReaderColorsHaveNames() {
        assertEquals(ColorName.ORANGE, colorName(0xFFFF9200.toInt())); assertEquals(ColorName.YELLOW, colorName(0xFFFFEB3B.toInt()))
        assertEquals(ColorName.BLUE, colorName(0xFF3B7BE0.toInt())); assertEquals(ColorName.GRAY, colorName(0xFF808080.toInt()))
        assertEquals(ColorName.RED, colorName(0xFFE53935.toInt())); assertEquals(ColorName.GREEN, colorName(0xFF43A047.toInt()))
    }

    @Test fun shadeOnlyWhenTwoCombosShareAName() = assertEquals(
        listOf(
            ComboName(ColorName.GRAY, Shade.DARK), ComboName(ColorName.GRAY, Shade.LIGHT),
            ComboName(ColorName.GRAY), ComboName(ColorName.ORANGE),
        ),
        comboNames(
            listOf(
                StyleCombo(MarkStyle.UNDERLINE, 0xFF555555.toInt()), StyleCombo(MarkStyle.UNDERLINE, 0x88888888.toInt()),
                StyleCombo(MarkStyle.HIGHLIGHT, 0xFF555555.toInt()), StyleCombo(MarkStyle.WAVY, 0xFFFF9200.toInt()),
            ),
        ),
    )

    @Test fun hexWhenTheShadeStillClashes() = assertEquals(
        listOf(ComboName(ColorName.GRAY, Shade.LIGHT, "#88888888"), ComboName(ColorName.GRAY, Shade.LIGHT, "#909090")),
        comboNames(listOf(StyleCombo(MarkStyle.UNDERLINE, 0x88888888.toInt()), StyleCombo(MarkStyle.UNDERLINE, 0xFF909090.toInt()))),
    )

    @Test fun blackAndWhiteGetNoShade() = assertEquals(
        listOf(ComboName(ColorName.BLACK, hex = "#000000"), ComboName(ColorName.BLACK, hex = "#101010")),
        comboNames(listOf(StyleCombo(MarkStyle.UNDERLINE, 0xFF000000.toInt()), StyleCombo(MarkStyle.UNDERLINE, 0xFF101010.toInt()))),
    )
}

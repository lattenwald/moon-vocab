package org.lattenwald.moonvocab.domain.mark

import org.junit.Assert.assertEquals
import org.junit.Test

class StyleChoiceTest {
    private val WAVY_ORANGE = StyleCombo(MarkStyle.WAVY, 0xFFFF9200.toInt())
    private val YELLOW_HL = StyleCombo(MarkStyle.HIGHLIGHT, 0xFFFFEB3B.toInt())

    private fun mark(key: String, combo: StyleCombo) = RawMark(key, "/books/a.epub", "A", 0, 0, "word", combo, 0L)

    // Wavy first, so yellow-first rows prove the sort.
    private val marks = List(3) { mark("w$it", WAVY_ORANGE) } + List(5) { mark("y$it", YELLOW_HL) }
    private val wavyKey0 = "w0"
    private val allKnownMarks = marks
    private val allKeys = marks.mapTo(HashSet()) { it.key }

    @Test fun countsPerCombinationAndImportTotal() {
        // wavy-orange: 3 marks, 1 known; highlight-yellow: 5 marks, 0 known; selected = {wavy-orange}; waiting = 0
        val c = sheetCounts(marks, setOf(wavyKey0), setOf(WAVY_ORANGE), waiting = 0)
        assertEquals(listOf(ComboRow(YELLOW_HL, 5, 5), ComboRow(WAVY_ORANGE, 3, 2)), c.rows)
        assertEquals(2, c.toImport)
    }

    @Test fun waitingMarksKeepImportEnabledWhenNothingIsNew() =
        assertEquals(4, sheetCounts(allKnownMarks, allKeys, setOf(WAVY_ORANGE), waiting = 4).toImport)

    @Test fun firstUsePicksWavy() = assertEquals(setOf(WAVY_ORANGE), defaultStyles(emptySet(), setOf(WAVY_ORANGE, YELLOW_HL)))

    @Test fun storedChoiceSurvives() = assertEquals(setOf(YELLOW_HL), defaultStyles(setOf(YELLOW_HL), setOf(WAVY_ORANGE, YELLOW_HL)))

    @Test fun emptyBackupKeepsStored() = assertEquals(setOf(YELLOW_HL), defaultStyles(setOf(YELLOW_HL), emptySet()))
}

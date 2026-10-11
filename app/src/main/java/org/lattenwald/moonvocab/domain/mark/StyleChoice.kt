package org.lattenwald.moonvocab.domain.mark

data class ComboRow(val combo: StyleCombo, val total: Int, val new: Int)

data class SheetCounts(val rows: List<ComboRow>, val toImport: Int)

/** [waiting] is the stored marks with no card; any import resumes them. */
fun sheetCounts(marks: List<RawMark>, knownKeys: Set<String>, selected: Set<StyleCombo>, waiting: Int): SheetCounts {
    val rows = marks.groupBy { it.combo }
        .map { (combo, ms) -> ComboRow(combo, ms.size, ms.count { it.key !in knownKeys }) }
        .sortedWith(compareByDescending<ComboRow> { it.total }.thenBy { it.combo.style.ordinal })
    return SheetCounts(rows, rows.filter { it.combo in selected }.sumOf { it.new } + waiting)
}

fun defaultStyles(stored: Set<StyleCombo>, present: Set<StyleCombo>): Set<StyleCombo> = when {
    present.isEmpty() || stored.any { it in present } -> stored
    else -> present.filterTo(HashSet()) { it.style == MarkStyle.WAVY }
}

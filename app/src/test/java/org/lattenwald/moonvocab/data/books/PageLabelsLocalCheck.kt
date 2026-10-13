package org.lattenwald.moonvocab.data.books

import java.io.File
import java.util.zip.ZipFile
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.lattenwald.moonvocab.domain.text.unlabelledIndex

class PageLabelsLocalCheck {
    @Test fun everyLabelledPartAlignsWithItsPlainPart() {
        val tsv = File(System.getenv("MOON_CHECK_DIR") ?: "/nonexistent", "parts-py.tsv")
        assumeTrue(tsv.exists())
        val report = StringBuilder()
        var labelledParts = 0
        var bad = 0
        for (path in tsv.readLines().map { it.substringBefore('\t') }.distinct()) {
            val moon = ZipFile(path).use { zip -> MoonEpubText(zip, File(path).length()).chapters() }
            for ((c, splits) in moon.parts.withIndex()) {
                for (s in splits.indices) {
                    val labelled = checkNotNull(moon.part(c, s, Long.MAX_VALUE))
                    val plain = splits[s]
                    if (labelled == plain) continue
                    labelledParts++
                    if (unlabelledIndex(labelled, plain, labelled.length) != null) continue
                    bad++
                    var lo = 0
                    var hi = labelled.length
                    while (lo < hi) {
                        val mid = (lo + hi) / 2
                        if (unlabelledIndex(labelled, plain, mid) == null) hi = mid else lo = mid + 1
                    }
                    val j = unlabelledIndex(labelled, plain, lo - 1) ?: 0
                    report.append("${File(path).name} $c/$s at $lo: «${labelled.around(lo)}» vs «${plain.around(j)}»\n")
                }
            }
        }
        File(tsv.parentFile, "page-labels-check.txt").writeText("bad=$bad of $labelledParts labelled parts\n$report")
    }

    private fun String.around(i: Int) = substring((i - 80).coerceAtLeast(0), (i + 40).coerceAtMost(length))
}

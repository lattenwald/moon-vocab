package org.lattenwald.moonvocab.data.books

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import org.junit.Assume.assumeTrue
import org.junit.Test

class MoonEpubLocalCheck {
    @Test fun matchesPythonPort() {
        val tsv = File(System.getenv("MOON_CHECK_DIR") ?: "/nonexistent", "parts-py.tsv")
        assumeTrue(tsv.exists())
        val expected = tsv.readLines().map { it.split('\t') }.groupBy { it[0] }
        val report = StringBuilder()
        var bad = 0
        for ((path, rows) in expected) {
            val book = ZipFile(path).use { zip ->
                val m = MoonEpub(zip, File(path).length())
                (0 until m.size).flatMap { c -> m.parts(c).mapIndexed { p, h -> listOf(path, "$c", "$p", "${h.length}", md5(h)) } }
            }
            if (book != rows) {
                bad++
                val first = book.zip(rows).indexOfFirst { (a, b) -> a != b }
                report.append("${File(path).name}: ours ${book.size} parts, python ${rows.size}; first diff at $first: " +
                    "${book.getOrNull(first)?.drop(1)} vs ${rows.getOrNull(first)?.drop(1)}\n")
            }
        }
        File(tsv.parentFile, "parts-kt-diff.txt").writeText("bad=$bad of ${expected.size}\n$report")
    }

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_16LE)).joinToString("") { "%02x".format(it) }
}

package org.lattenwald.moonvocab.data.books

import java.io.File
import java.security.MessageDigest
import org.junit.Assume.assumeTrue
import org.junit.Test

// Compares MoonFb2/MoonMobi parts with the Python ports' dumps in $MOON_CHECK_DIR; skipped where the dumps are missing.
class MoonFb2MobiLocalCheck {
    private val dir = File(System.getenv("MOON_CHECK_DIR") ?: "/nonexistent")

    @Test fun fb2MatchesPythonPort() = compare("fb2-parts-py.tsv", "fb2-parts-kt-diff.txt") { f -> MoonFb2.read(f)?.let { it.size to it::parts } }

    @Test fun allFb2MatchPythonPort() = compare("fb2-parts-py-all.tsv", "fb2-parts-kt-diff-all.txt") { f -> MoonFb2.read(f)?.let { it.size to it::parts } }

    @Test fun mobiMatchesPythonPort() = compare("mobi-parts-py.tsv", "mobi-parts-kt-diff.txt") { f -> MoonMobi.read(f)?.let { it.size to it::parts } }

    @Test fun allMobiMatchPythonPort() = compare("mobi-parts-py-all.tsv", "mobi-parts-kt-diff-all.txt") { f -> MoonMobi.read(f)?.let { it.size to it::parts } }

    @Test fun epubStillMatchesPythonPort() = MoonEpubLocalCheck().matchesPythonPort()

    @Test fun dumpRequested() {
        val req = File(dir, "kt-dump-request.txt")
        assumeTrue(req.exists())
        val out = File(dir, "kt-dump").apply { mkdirs() }
        for (line in req.readLines().filter { it.isNotBlank() }) {
            val (path, chapter) = line.split('\t')
            val f = File(path)
            val parts = if (path.lowercase().contains(".fb2")) MoonFb2.read(f)!!.parts(chapter.toInt()) else MoonMobi.read(f)!!.parts(chapter.toInt())
            parts.forEachIndexed { p, h -> File(out, "${f.name}.$chapter.$p.html").writeText(h) }
        }
    }

    private fun compare(tsvName: String, reportName: String, open: (File) -> Pair<Int, (Int) -> List<String>>?) {
        val tsv = File(dir, tsvName)
        assumeTrue(tsv.exists())
        val expected = tsv.readLines().map { it.split('\t') }.groupBy { it[0] }
        val report = StringBuilder()
        var bad = 0
        var parts = 0
        for ((path, rows) in expected) {
            parts += rows.size
            val book = open(File(path))?.let { (size, partsOf) ->
                (0 until size).flatMap { c -> partsOf(c).mapIndexed { p, h -> listOf(path, "$c", "$p", "${h.length}", md5(h)) } }
            }.orEmpty()
            if (book != rows) {
                bad++
                val first = (0 until maxOf(book.size, rows.size)).first { book.getOrNull(it) != rows.getOrNull(it) }
                report.append("${File(path).name}: ours ${book.size} parts, python ${rows.size}; first diff at $first: " +
                    "${book.getOrNull(first)?.drop(1)} vs ${rows.getOrNull(first)?.drop(1)}\n")
            }
        }
        File(dir, reportName).writeText("bad=$bad of ${expected.size} books, $parts python parts\n$report")
    }

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_16LE)).joinToString("") { "%02x".format(it) }
}

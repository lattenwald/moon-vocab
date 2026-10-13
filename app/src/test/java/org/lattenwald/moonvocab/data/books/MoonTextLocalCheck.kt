package org.lattenwald.moonvocab.data.books

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.lattenwald.moonvocab.domain.text.BookSentences
import org.lattenwald.moonvocab.domain.text.MarkPlace
import org.lattenwald.moonvocab.domain.text.moonNoteStart
import org.robolectric.RobolectricTestRunner

// Scores the user's real notes ($MOON_CHECK_DIR/notes.jsonl: path, chapter, split, position, text, time) against Moon+ text.
@RunWith(RobolectricTestRunner::class)
class MoonTextLocalCheck {
    private val dir = File(System.getenv("MOON_CHECK_DIR") ?: "/nonexistent")

    @Test fun notesAreAtTheirPositions() {
        val notes = File(dir, "notes.jsonl")
        assumeTrue(notes.exists())
        val rows = notes.readLines().map { Json.parseToJsonElement(it).jsonObject }
        val report = StringBuilder()
        val totals = sortedMapOf<String, IntArray>()
        for ((path, ns) in rows.groupBy { it.getValue("path").jsonPrimitive.content }) {
            val format = path.substringAfterLast('.').lowercase().let { if (path.lowercase().endsWith(".fb2.zip")) "fb2" else it }
            val t = totals.getOrPut(format) { IntArray(4) } // notes, exact, shown, no moon text
            val book = readBookText(File(path), File(path).name)
            val sentences = book?.let { BookSentences(it, emptyList()) }
            var bookExact = 0
            val lines = StringBuilder()
            for (n in ns) {
                t[0]++
                val c = n.getValue("chapter").jsonPrimitive.int
                val s = n.getValue("split").jsonPrimitive.int
                val p = n.getValue("position").jsonPrimitive.int
                val w = n.getValue("text").jsonPrimitive.content
                val moon = book?.moon
                if (moon == null) {
                    t[3]++
                    continue
                }
                val part = sentences?.part(MarkPlace(c, s, p, n.getValue("time").jsonPrimitive.long, w))
                if (part != null && part.startsWith(w, p)) {
                    t[1]++
                    t[2]++
                    bookExact++
                    continue
                }
                val shown = part?.let { moonNoteStart(it, p, w) }
                if (shown != null) t[2]++
                lines.append("  MISS ch$c s$s p$p shown=${shown?.minus(p)} parts=${moon.parts.getOrNull(c)?.size} ${w.take(30)}\n")
            }
            report.append("${File(path).name}: $bookExact/${ns.size}${if (book?.moon == null) " NO MOON TEXT" else ""}\n").append(lines)
        }
        val summary = totals.entries.joinToString("\n") { (f, t) -> "$f: notes=${t[0]} exact=${t[1]} shown=${t[2]} noMoon=${t[3]}" }
        File(dir, "kt-text-score.txt").writeText("$summary\n\n$report")
    }
}

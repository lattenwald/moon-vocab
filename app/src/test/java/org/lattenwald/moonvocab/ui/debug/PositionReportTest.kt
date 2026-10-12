package org.lattenwald.moonvocab.ui.debug

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.lattenwald.moonvocab.data.books.BookCopy
import org.lattenwald.moonvocab.data.books.BookText
import org.lattenwald.moonvocab.domain.mark.MarkStyle
import org.lattenwald.moonvocab.domain.mark.RawMark
import org.lattenwald.moonvocab.domain.mark.StyleCombo

class PositionReportTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun mark(book: String, chapter: Int, position: Int, text: String) =
        RawMark("$book-$position", "/sdcard/Books/$book", book, chapter, position, text, StyleCombo(MarkStyle.WAVY, -28160), 0L)

    @Test fun booksWithAMissGoIntoTheZipWithTheBackup() {
        val backup = tmp.newFile("b.mrpro").apply { writeText("backup") }
        val texts = mapOf("good.epub" to listOf("A grim day."), "bad.epub" to listOf("A grim day."), "lost.epub" to null)
        val marks = listOf(
            mark("good.epub", 0, 2, "grim"),
            mark("bad.epub", 0, 2, "grim"),
            mark("bad.epub", 0, 0, "day"),
            mark("lost.epub", 0, 0, "x"),
        )
        val out = ByteArrayOutputStream()
        val summary = writePositionReport(
            out, "0.1.0 (68)", backup, marks, mapOf("bad.epub-0" to "<b>sentence</b>"),
            copy = { path ->
                val name = path.substringAfterLast('/')
                if (name == "lost.epub") null else BookCopy("Books/$name", tmp.newFile(name).apply { writeText(name) })
            },
            read = { copy -> texts.getValue(copy.file.name)?.let { BookText(null, null, null, it) } },
        )

        assertEquals(PositionReportSummary(notes = 4, exact = 2), summary)
        val entries = unzip(out.toByteArray())
        assertEquals(setOf("backup/b.mrpro", "books/Books/bad.epub", "report.json"), entries.keys)
        val books = Json.parseToJsonElement(entries.getValue("report.json")).jsonObject.getValue("books").jsonArray
        val bad = books.single { it.jsonObject.getValue("bookPath").jsonPrimitive.content.endsWith("bad.epub") }.jsonObject
        val miss = bad.getValue("misses").jsonArray.single().jsonObject
        assertEquals("day", miss.getValue("text").jsonPrimitive.content)
        assertEquals(listOf(0, 0, 7), miss.getValue("occurrences").jsonArray.single().jsonArray.map { it.jsonPrimitive.int })
        assertEquals("<b>sentence</b>", miss.getValue("cardSentence").jsonPrimitive.content)
    }

    private fun unzip(bytes: ByteArray): Map<String, String> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                put(entry.name, zip.readBytes().decodeToString())
            }
        }
    }
}

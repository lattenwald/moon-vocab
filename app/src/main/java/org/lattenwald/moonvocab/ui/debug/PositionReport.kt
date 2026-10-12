package org.lattenwald.moonvocab.ui.debug

import java.io.File
import java.io.OutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.lattenwald.moonvocab.data.books.BookCopy
import org.lattenwald.moonvocab.data.books.BookText
import org.lattenwald.moonvocab.domain.mark.RawMark
import org.lattenwald.moonvocab.domain.mark.fileKey
import org.lattenwald.moonvocab.domain.text.NoteRef
import org.lattenwald.moonvocab.domain.text.PositionCheck
import org.lattenwald.moonvocab.domain.text.checkPosition

private val reportJson = Json { prettyPrint = true }

data class PositionReportSummary(val notes: Int, val exact: Int)

fun positionReportName(today: LocalDate): String = "moon-vocab-positions-$today.zip"

fun writePositionReport(
    out: OutputStream,
    appVersion: String,
    backup: File,
    marks: List<RawMark>,
    cardSentences: Map<String, String>,
    copy: (String) -> BookCopy?,
    read: (BookCopy) -> BookText?,
): PositionReportSummary {
    var notes = 0
    var exact = 0
    val books = mutableListOf<JsonObject>()
    ZipOutputStream(out).use { zip ->
        zip.putNextEntry(ZipEntry("backup/${backup.name}"))
        backup.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
        for (bookMarks in marks.groupBy { it.fileKey }.values) {
            val bookPath = bookMarks.first().bookPath
            val bookCopy = copy(bookPath)
            try {
                val text = bookCopy?.let(read)
                val checks = text?.let { t -> bookMarks.map { it to checkPosition(t.chapters, NoteRef(it.chapter, it.position, it.text)) } }
                notes += bookMarks.size
                exact += checks.orEmpty().count { it.second.exact }
                val missed = checks == null || checks.any { !it.second.exact }
                if (missed && bookCopy != null) {
                    zip.putNextEntry(ZipEntry("books/${bookCopy.relativePath}"))
                    bookCopy.file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                books += bookJson(bookPath, bookCopy, text, bookMarks, checks, cardSentences)
            } finally {
                bookCopy?.file?.delete()
            }
        }
        val report = buildJsonObject {
            put("appVersion", appVersion)
            put("backup", backup.name)
            put("notes", notes)
            put("exact", exact)
            putJsonArray("books") { books.forEach { add(it) } }
        }
        zip.putNextEntry(ZipEntry("report.json"))
        zip.write(reportJson.encodeToString(JsonElement.serializer(), report).toByteArray())
        zip.closeEntry()
    }
    return PositionReportSummary(notes, exact)
}

private fun bookJson(
    bookPath: String,
    bookCopy: BookCopy?,
    text: BookText?,
    marks: List<RawMark>,
    checks: List<Pair<RawMark, PositionCheck>>?,
    cardSentences: Map<String, String>,
) = buildJsonObject {
    put("bookPath", bookPath)
    put("title", marks.first().bookTitle)
    put("found", bookCopy?.relativePath)
    put("readable", text != null)
    put("notes", marks.size)
    if (text == null || checks == null) return@buildJsonObject
    put("exact", checks.count { it.second.exact })
    put("chaptersMatchMoonReader", text.chaptersMatchMoonReader)
    putJsonArray("chapters") {
        for (chapter in text.chapters) addJsonObject {
            put("length", chapter.length)
            put("start", chapter.take(CHAPTER_HEAD))
        }
    }
    putJsonArray("misses") {
        for ((mark, check) in checks) if (!check.exact) addJsonObject {
            put("chapter", mark.chapter)
            put("split", mark.split)
            put("position", mark.position)
            put("text", mark.text)
            put("style", mark.combo.style.name)
            put("atPosition", check.atPosition)
            put("around", check.around)
            putJsonArray("occurrences") {
                for (o in check.occurrences) addJsonArray {
                    add(o.chapter)
                    add(o.position)
                }
            }
            put("cardSentence", cardSentences[mark.key])
        }
    }
}

private const val CHAPTER_HEAD = 60

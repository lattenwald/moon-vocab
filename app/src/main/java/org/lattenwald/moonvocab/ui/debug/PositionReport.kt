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
import org.lattenwald.moonvocab.data.books.parts
import org.lattenwald.moonvocab.domain.mark.RawMark
import org.lattenwald.moonvocab.domain.mark.fileKey
import org.lattenwald.moonvocab.domain.mark.place
import org.lattenwald.moonvocab.domain.text.BookSentences
import org.lattenwald.moonvocab.domain.text.Lookup
import org.lattenwald.moonvocab.domain.text.PositionCheck
import org.lattenwald.moonvocab.domain.text.SentenceResult
import org.lattenwald.moonvocab.domain.text.checkPosition

private val reportJson = Json { prettyPrint = true }

data class PositionReportSummary(val notes: Int, val exact: Int)

data class PositionReportProgress(val done: Int, val total: Int, val bookTitle: String?)

fun positionReportName(today: LocalDate): String = "moon-vocab-positions-$today.zip"

fun writePositionReport(
    out: OutputStream,
    appVersion: String,
    backup: File,
    marks: List<RawMark>,
    copy: (String) -> BookCopy?,
    read: (BookCopy) -> BookText?,
    onProgress: (PositionReportProgress) -> Unit = {},
): PositionReportSummary {
    var notes = 0
    var exact = 0
    val books = mutableListOf<JsonObject>()
    val byBook = marks.groupBy { it.fileKey }.values
    ZipOutputStream(out).use { zip ->
        onProgress(PositionReportProgress(0, byBook.size, null))
        zip.putNextEntry(ZipEntry("backup/${backup.name}"))
        backup.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
        for ((i, bookMarks) in byBook.withIndex()) {
            onProgress(PositionReportProgress(i, byBook.size, bookMarks.first().bookTitle))
            val bookPath = bookMarks.first().bookPath
            val bookCopy = copy(bookPath)
            try {
                val text = bookCopy?.let(read)
                val checks = text?.let { checks(it, bookMarks) }
                notes += bookMarks.size
                exact += checks.orEmpty().count { it.position.exact }
                val missed = checks == null || checks.any { !it.position.exact }
                if (missed && bookCopy != null) {
                    zip.putNextEntry(ZipEntry("books/${bookCopy.relativePath}"))
                    bookCopy.file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                books += bookJson(bookPath, bookCopy, text, bookMarks, checks)
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

private data class Check(val mark: RawMark, val position: PositionCheck, val lookup: Lookup? = null, val lookupError: String? = null)

private fun checks(text: BookText, marks: List<RawMark>): List<Check> {
    val sentences = BookSentences(text, marks.map { it.place })
    val parts = text.parts
    return marks.map { mark ->
        val position = checkPosition(parts, sentences.part(mark.place), mark.position, mark.text)
        when {
            position.exact -> Check(mark, position)
            else -> try {
                Check(mark, position, sentences.find(mark.place, text.language))
            } catch (e: Exception) {
                Check(mark, position, lookupError = e.message ?: e.javaClass.name)
            }
        }
    }
}

private fun bookJson(
    bookPath: String,
    bookCopy: BookCopy?,
    text: BookText?,
    marks: List<RawMark>,
    checks: List<Check>?,
) = buildJsonObject {
    put("bookPath", bookPath)
    put("title", marks.first().bookTitle)
    put("found", bookCopy?.relativePath)
    put("readable", text != null)
    put("notes", marks.size)
    if (text == null || checks == null) return@buildJsonObject
    put("exact", checks.count { it.position.exact })
    put("reanchored", checks.count { !it.position.exact && it.position.shownAt != null })
    put("moonText", text.moon != null)
    putJsonArray("chapters") {
        for (splits in text.parts) addJsonArray {
            for (part in splits) addJsonObject {
                put("length", part.length)
                put("start", part.take(CHAPTER_HEAD))
            }
        }
    }
    putJsonArray("misses") {
        for ((mark, check, lookup, lookupError) in checks) if (!check.exact) addJsonObject {
            put("chapter", mark.chapter)
            put("split", mark.split)
            put("position", mark.position)
            put("text", mark.text)
            put("style", mark.combo.style.name)
            put("markedAt", mark.markedAt)
            put("shownAt", check.shownAt)
            put("atPosition", check.atPosition)
            put("around", check.around)
            putJsonArray("occurrences") {
                for (o in check.occurrences) addJsonArray {
                    add(o.chapter)
                    add(o.split)
                    add(o.position)
                }
            }
            put("via", lookup?.via?.name)
            put("sentence", (lookup?.sentence as? SentenceResult.Found)?.sentence)
            put("lookupError", lookupError)
        }
    }
}

private const val CHAPTER_HEAD = 60

package org.lattenwald.moonvocab.data.backup

import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream
import org.lattenwald.moonvocab.domain.mark.KeyInput
import org.lattenwald.moonvocab.domain.mark.RawMark
import org.lattenwald.moonvocab.domain.mark.StyleCombo
import org.lattenwald.moonvocab.domain.mark.markKeys
import org.lattenwald.moonvocab.domain.mark.styleOf

sealed interface BackupContents {
    data class Complete(val marks: List<RawMark>, val bookmarksSkipped: Int, val writtenAt: Long? = null) : BackupContents
    data class Incomplete(val reason: String) : BackupContents
}

class BackupReader(private val cacheDir: File) {
    fun read(file: File): BackupContents = try {
        ZipInputStream(file.inputStream().buffered()).use(::scan)
    } catch (e: IOException) {
        BackupContents.Incomplete(e.message ?: e.toString())
    } catch (e: SQLException) {
        BackupContents.Incomplete(e.message ?: e.toString())
    } catch (e: IllegalArgumentException) { // Android's ZipInputStream throws it for a malformed entry name
        BackupContents.Incomplete(e.message ?: e.toString())
    }

    // Entries are read in order: a truncated backup has no central directory (spec §2).
    private fun scan(zip: ZipInputStream): BackupContents {
        while (true) {
            val entry = zip.nextEntry ?: break
            val header = zip.readUpTo(SQLITE_HEADER.size)
            if (!header.contentEquals(SQLITE_HEADER)) continue
            val copy = File(cacheDir, "mrbooks-${UUID.randomUUID()}.db")
            try {
                copy.outputStream().use { out ->
                    out.write(header)
                    zip.copyTo(out)
                }
                readNotes(copy)?.let { return it.copy(writtenAt = entry.time.takeIf { t -> t > 0 }) }
            } finally {
                SQLiteDatabase.deleteDatabase(copy)
            }
        }
        return BackupContents.Incomplete("no database in backup")
    }

    private fun readNotes(dbFile: File): BackupContents.Complete? =
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val hasNotes = db.rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'notes'", null)
                .use { it.moveToFirst() }
            if (!hasNotes) return null
            var bookmarks = 0
            val rows = mutableListOf<Row>()
            db.rawQuery(
                "SELECT filename, book, lastChapter, lastPosition, original, highlightColor, time, " +
                    "bak, underline, strikethrough, bookmark, lastSplitIndex FROM notes",
                null,
            ).use { c ->
                while (c.moveToNext()) {
                    if (!c.getString(10).isNullOrEmpty()) {
                        bookmarks++
                        continue
                    }
                    val text = c.getString(4)
                    if (text.isNullOrBlank()) continue
                    rows += Row(
                        bookPath = c.getString(0).orEmpty(),
                        bookTitle = c.getString(1).orEmpty(),
                        chapter = c.getInt(2),
                        position = c.getInt(3),
                        text = text,
                        combo = StyleCombo(styleOf(c.getString(7), c.getInt(8), c.getInt(9)), c.getInt(5)),
                        markedAt = c.getLong(6),
                        split = c.getInt(11),
                    )
                }
            }
            val keys = markKeys(rows.map { KeyInput(it.bookPath, it.markedAt, it.text) })
            val marks = rows.mapIndexed { i, r ->
                RawMark(keys[i], r.bookPath, r.bookTitle, r.chapter, r.position, r.text, r.combo, r.markedAt, r.split)
            }
            BackupContents.Complete(marks, bookmarks)
        }

    private class Row(
        val bookPath: String,
        val bookTitle: String,
        val chapter: Int,
        val position: Int,
        val text: String,
        val combo: StyleCombo,
        val markedAt: Long,
        val split: Int,
    )

    private companion object {
        val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    }
}

private fun InputStream.readUpTo(n: Int): ByteArray {
    val buf = ByteArray(n)
    var len = 0
    while (len < n) {
        val r = read(buf, len, n - len)
        if (r < 0) break
        len += r
    }
    return if (len == n) buf else buf.copyOf(len)
}

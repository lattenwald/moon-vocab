package org.lattenwald.moonvocab.data.backup

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class NoteRow(
    val book: String,
    val filename: String,
    val chapter: Int,
    val position: Int,
    val color: Int,
    val time: Long,
    val original: String,
    val underline: Int = 0,
    val strikethrough: Int = 0,
    val bak: String = "0",
    val bookmark: String = "",
    val split: Int = 0,
)

enum class Cut { NONE, BEFORE_DB, INSIDE_DB }

object MrproFixture {
    private const val DIR = "com.flyersoft.moonreaderp/"

    fun build(dir: File, notes: List<NoteRow>, cut: Cut = Cut.NONE, writtenAt: Long? = null): File {
        val db = database(dir, notes)
        val bytes = ByteArrayOutputStream()
        var endOfFirstEntry = 0
        var middleOfDatabase = 0
        ZipOutputStream(bytes).use { zip ->
            zip.setMethod(ZipOutputStream.DEFLATED)
            zip.entry("1.tag", "<?xml version='1.0' encoding='utf-8'?><map />".toByteArray())
            endOfFirstEntry = bytes.size()
            val dbEntry = ZipEntry(DIR + "2.tag")
            writtenAt?.let { dbEntry.time = it }
            zip.putNextEntry(dbEntry)
            val dbDataStart = bytes.size()
            zip.write(db)
            zip.closeEntry()
            middleOfDatabase = dbDataStart + (dbEntry.compressedSize / 2).toInt()
            zip.entry("3.tag", ByteArray(0))
            zip.entry("_names.list", "/shared_prefs/settings.xml\n/databases/mrbooks.db\n/databases/mrbooks.db-journal\n".toByteArray())
        }
        val all = bytes.toByteArray()
        val keep = when (cut) {
            Cut.NONE -> all.size
            Cut.BEFORE_DB -> endOfFirstEntry
            Cut.INSIDE_DB -> middleOfDatabase
        }
        return File.createTempFile("backup-", ".mrpro", dir).apply { writeBytes(all.copyOf(keep)) }
    }

    private fun ZipOutputStream.entry(name: String, data: ByteArray) {
        putNextEntry(ZipEntry(DIR + name))
        write(data)
        closeEntry()
    }

    private fun database(dir: File, notes: List<NoteRow>): ByteArray {
        val file = File.createTempFile("fixture-", ".db", dir)
        file.delete()
        try {
            SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
                db.execSQL(
                    "CREATE TABLE notes (_id integer primary key autoincrement, book text, filename text, " +
                        "lowerFilename text, lastChapter NUMERIC, lastSplitIndex NUMERIC, lastPosition NUMERIC, " +
                        "highlightLength NUMERIC, highlightColor NUMERIC, time NUMERIC, bookmark text, note text, " +
                        "original text, underline NUMERIC, strikethrough NUMERIC, bak text)",
                )
                for (n in notes) {
                    db.insertOrThrow("notes", null, ContentValues().apply {
                        put("book", n.book)
                        put("filename", n.filename)
                        put("lowerFilename", n.filename.lowercase())
                        put("lastChapter", n.chapter)
                        put("lastSplitIndex", n.split)
                        put("lastPosition", n.position)
                        put("highlightLength", n.original.length)
                        put("highlightColor", n.color)
                        put("time", n.time)
                        put("bookmark", n.bookmark)
                        put("note", "")
                        put("original", n.original)
                        put("underline", n.underline)
                        put("strikethrough", n.strikethrough)
                        put("bak", n.bak)
                    })
                }
            }
            return file.readBytes()
        } finally {
            SQLiteDatabase.deleteDatabase(file)
        }
    }
}

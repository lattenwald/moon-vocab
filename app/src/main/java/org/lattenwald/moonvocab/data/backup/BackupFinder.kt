package org.lattenwald.moonvocab.data.backup

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract.Document
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.lattenwald.moonvocab.domain.mark.RawMark

data class FoundBackup(val file: File, val name: String, val lastModified: Long, val marks: List<RawMark>)

data class BackupSearch(val chosen: FoundBackup?, val skipped: List<String>)

class BackupFinder(
    private val resolver: ContentResolver,
    cacheDir: File,
    private val reader: BackupReader,
    private val io: CoroutineDispatcher,
) {
    private val dir = File(cacheDir, DIR)

    /** Throws [SecurityException] when the folder can't be read. */
    suspend fun newest(tree: Uri): BackupSearch = withContext(io) {
        val folder = SafBackupFolder(resolver, tree)
        dir.mkdirs()
        val pick = pickNewestComplete(folder.list()) { doc -> reader.read(folder.copy(doc, File(dir, doc.name))) }
        keepOnly(pick.chosen?.name)
        BackupSearch(
            chosen = pick.chosen?.let { FoundBackup(File(dir, it.name), it.name, it.lastModified, pick.marks) },
            skipped = pick.skipped.map { it.name },
        )
    }

    suspend fun open(document: Uri): FoundBackup? = withContext(io) {
        val (name, lastModified) = describe(document)
        dir.mkdirs()
        // A partial copy must not overwrite a chosen backup of the same name.
        val part = File(dir, "$name$PART")
        try {
            val input = resolver.openInputStream(document) ?: throw FileNotFoundException("cannot open $document")
            input.use { source -> part.outputStream().use { source.copyTo(it) } }
            when (val contents = reader.read(part)) {
                is BackupContents.Incomplete -> null
                is BackupContents.Complete -> {
                    val file = File(dir, name)
                    check(part.renameTo(file)) { "cannot rename $part" }
                    keepOnly(name)
                    FoundBackup(file, name, lastModified, contents.marks)
                }
            }
        } finally {
            part.delete()
        }
    }

    private fun describe(document: Uri): Pair<String, Long> {
        val cursor = resolver.query(document, null, null, null, null) ?: return FALLBACK_NAME to 0L
        return cursor.use { c ->
            if (!c.moveToFirst()) return@use FALLBACK_NAME to 0L
            val nameColumn = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val modifiedColumn = c.getColumnIndex(Document.COLUMN_LAST_MODIFIED)
            val name = if (nameColumn >= 0) c.getString(nameColumn)?.substringAfterLast('/') else null
            val modified = if (modifiedColumn >= 0 && !c.isNull(modifiedColumn)) c.getLong(modifiedColumn) else 0L
            (name?.takeIf { it.isNotBlank() } ?: FALLBACK_NAME) to modified
        }
    }

    private fun keepOnly(name: String?) {
        dir.listFiles()?.filter { it.name != name }?.forEach { it.delete() }
    }

    private companion object {
        const val DIR = "backups"
        const val PART = ".part"
        const val FALLBACK_NAME = "backup.mrpro"
    }
}

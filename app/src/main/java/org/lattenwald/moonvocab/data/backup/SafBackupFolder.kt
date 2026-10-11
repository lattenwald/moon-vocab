package org.lattenwald.moonvocab.data.backup

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

class SafBackupFolder(private val resolver: ContentResolver, private val treeUri: Uri) {
    fun list(): List<BackupDocument> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_MIME_TYPE)
        val cursor = resolver.query(children, projection, null, null, null) ?: throw IOException("cannot list $children")
        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    if (c.getString(3) == Document.MIME_TYPE_DIR) continue
                    add(BackupDocument(c.getString(0), c.getString(1).orEmpty(), if (c.isNull(2)) 0L else c.getLong(2)))
                }
            }
        }
    }

    fun copy(document: BackupDocument, target: File): File {
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, document.documentId)
        try {
            val input = resolver.openInputStream(uri) ?: throw FileNotFoundException("cannot open $uri")
            input.use { source -> target.outputStream().use { source.copyTo(it) } }
        } catch (e: Throwable) {
            target.delete()
            throw e
        }
        return target
    }
}

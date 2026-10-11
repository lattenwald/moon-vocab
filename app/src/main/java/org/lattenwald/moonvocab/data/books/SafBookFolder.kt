package org.lattenwald.moonvocab.data.books

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.Log
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

interface BookFolder {
    fun listRelativePaths(): List<String>
    fun copyToCache(relativePath: String, cacheDir: File): File
}

class SafBookFolder(private val context: Context, private val treeUri: Uri) : BookFolder {
    private val documentIds = HashMap<String, String>()

    override fun listRelativePaths(): List<String> {
        documentIds.clear()
        documentIds += walkTree(DocumentsContract.getTreeDocumentId(treeUri), ::children)
        return documentIds.keys.toList()
    }

    private fun children(parentId: String): List<TreeChild> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
        val cursor = context.contentResolver.query(uri, projection, null, null, null) ?: throw IOException("cannot list $uri")
        return cursor.use { c ->
            buildList { while (c.moveToNext()) add(TreeChild(c.getString(0), c.getString(1), c.getString(2) == Document.MIME_TYPE_DIR)) }
        }
    }

    override fun copyToCache(relativePath: String, cacheDir: File): File {
        val id = documentIds[relativePath] ?: throw FileNotFoundException("not listed: $relativePath")
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
        val target = File.createTempFile("book", extensionOf(relativePath), cacheDir)
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException("cannot open $uri")
            input.use { source -> target.outputStream().use { source.copyTo(it) } }
        } catch (e: Throwable) {
            target.delete()
            throw e
        }
        return target
    }
}

internal class TreeChild(val documentId: String, val name: String, val isDirectory: Boolean)

internal fun walkTree(rootId: String, children: (String) -> List<TreeChild>): Map<String, String> {
    val ids = LinkedHashMap<String, String>()
    val pending = ArrayDeque(listOf(rootId to ""))
    while (pending.isNotEmpty()) {
        val (parentId, prefix) = pending.removeFirst()
        val listed = if (prefix.isEmpty()) {
            children(parentId)
        } else {
            try {
                children(parentId)
            } catch (e: Exception) {
                Log.w(TAG, "cannot list $prefix", e)
                continue
            }
        }
        for (child in listed) {
            val path = prefix + child.name
            if (child.isDirectory) pending.add(child.documentId to "$path/") else ids[path] = child.documentId
        }
    }
    return ids
}

private const val TAG = "SafBookFolder"

private fun extensionOf(path: String): String {
    val name = path.substringAfterLast('/')
    if (name.lowercase().endsWith(".fb2.zip")) return name.takeLast(".fb2.zip".length)
    val dot = name.lastIndexOf('.')
    return if (dot > 0) name.substring(dot) else ""
}

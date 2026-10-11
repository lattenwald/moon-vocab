package org.lattenwald.moonvocab.data.books

import android.util.Log
import java.io.File

interface BookSource {
    fun text(bookPath: String): BookText?
}

class FolderBookSource(private val folder: BookFolder?, private val cacheDir: File) : BookSource {
    private val locator: BookLocator by lazy { BookLocator(folder?.listRelativePaths().orEmpty()) }
    private val texts = HashMap<String, BookText?>()

    override fun text(bookPath: String): BookText? {
        if (folder == null) return null
        if (bookPath !in texts) texts[bookPath] = read(folder, bookPath)
        return texts[bookPath]
    }

    private fun read(folder: BookFolder, bookPath: String): BookText? {
        val relativePath = locator.locate(bookPath) ?: return null
        return try {
            val copy = folder.copyToCache(relativePath, cacheDir)
            try {
                readBookText(copy, relativePath.substringAfterLast('/'))
            } finally {
                copy.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "cannot read $relativePath", e)
            null
        }
    }

    private companion object {
        const val TAG = "FolderBookSource"
    }
}

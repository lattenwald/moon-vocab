package org.lattenwald.moonvocab.data.books

import android.util.Log
import java.io.File

interface BookSource {
    fun text(bookPath: String): BookText?
}

class FolderBookSource(private val folder: BookFolder?, private val cacheDir: File) : BookSource {
    private val locator: BookLocator by lazy { BookLocator(folder?.listRelativePaths().orEmpty()) }

    override fun text(bookPath: String): BookText? = folder?.let { read(it, bookPath) }

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
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "out of memory reading $relativePath", e)
            null
        }
    }

    private companion object {
        const val TAG = "FolderBookSource"
    }
}

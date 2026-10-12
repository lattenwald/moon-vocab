package org.lattenwald.moonvocab.data.books

import android.util.Log
import java.io.File

interface BookSource {
    fun text(bookPath: String): BookText?
}

data class BookCopy(val relativePath: String, val file: File)

class FolderBookSource(private val folder: BookFolder?, private val cacheDir: File) : BookSource {
    private val locator: BookLocator by lazy { BookLocator(folder?.listRelativePaths().orEmpty()) }

    override fun text(bookPath: String): BookText? {
        val copy = copy(bookPath) ?: return null
        try {
            return read(copy)
        } finally {
            copy.file.delete()
        }
    }

    /** The caller deletes the copy. */
    fun copy(bookPath: String): BookCopy? {
        val folder = folder ?: return null
        val relativePath = locator.locate(bookPath) ?: return null
        return try {
            BookCopy(relativePath, folder.copyToCache(relativePath, cacheDir))
        } catch (e: Exception) {
            Log.w(TAG, "cannot copy $relativePath", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "out of memory copying $relativePath", e)
            null
        }
    }

    fun read(copy: BookCopy): BookText? {
        val relativePath = copy.relativePath
        return try {
            readBookText(copy.file, relativePath.substringAfterLast('/'))
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

package org.lattenwald.moonvocab.data.settings

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// Android caps how many persisted grants an app may hold.
class FolderGrants(private val resolver: ContentResolver, private val settings: Settings, private val io: CoroutineDispatcher) {
    private val lock = Mutex()

    suspend fun choose(uri: Uri, save: suspend () -> Unit) = lock.withLock {
        withContext(io) {
            try {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                Log.e(TAG, "no persistable permission for $uri", e)
            }
        }
        save()
        withContext(io) {
            val chosen = settings.current().let { setOfNotNull(it.backupTree, it.booksTree) }
            resolver.persistedUriPermissions.map { it.uri }.filter { it !in chosen }
                .forEach { resolver.releasePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
    }

    private companion object {
        const val TAG = "FolderGrants"
    }
}

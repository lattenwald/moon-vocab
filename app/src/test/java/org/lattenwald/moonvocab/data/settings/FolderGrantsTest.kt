package org.lattenwald.moonvocab.data.settings

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FolderGrantsTest {
    @get:Rule val folder = TemporaryFolder()

    private val resolver: ContentResolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
    private val a = Uri.parse("content://tree/a")
    private val b = Uri.parse("content://tree/b")
    private val c = Uri.parse("content://tree/c")

    private fun TestScope.grants(): Pair<FolderGrants, Settings> {
        val settings = Settings(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "settings.preferences_pb") })
        return FolderGrants(resolver, settings, StandardTestDispatcher(testScheduler)) to settings
    }

    private suspend fun FolderGrants.backup(settings: Settings, uri: Uri) = choose(uri) { settings.update { it.copy(backupTree = uri) } }

    private suspend fun FolderGrants.books(settings: Settings, uri: Uri) = choose(uri) { settings.update { it.copy(booksTree = uri) } }

    private fun granted() = resolver.persistedUriPermissions.map { it.uri }.toSet()

    @Test fun choosingAnotherBackupFolderReleasesTheOldOneAndKeepsTheBooksFolder() = runTest {
        val (grants, settings) = grants()
        grants.backup(settings, a)
        grants.books(settings, b)
        grants.backup(settings, c)
        assertEquals(setOf(b, c), granted())
    }

    @Test fun aFolderChosenForBothKeepsItsGrantWhenOneMovesAway() = runTest {
        val (grants, settings) = grants()
        grants.backup(settings, a)
        grants.books(settings, a)
        grants.backup(settings, c)
        assertEquals(setOf(a, c), granted())
    }
}

package org.lattenwald.moonvocab.data.books

import java.io.File
import kotlin.test.assertFailsWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FolderBookSourceTest {
    @get:Rule val folder = TemporaryFolder()

    private class FakeFolder(
        private val files: Map<String, File>,
        private val listingFails: Boolean = false,
        private val outOfMemory: String? = null,
    ) : BookFolder {
        var listings = 0
        var copies = 0

        override fun listRelativePaths(): List<String> {
            listings++
            if (listingFails) throw SecurityException("permission revoked")
            return files.keys.toList()
        }

        override fun copyToCache(relativePath: String, cacheDir: File): File {
            copies++
            if (relativePath == outOfMemory) throw OutOfMemoryError("Java heap space")
            return files.getValue(relativePath).copyTo(File(cacheDir, "copy-$copies"))
        }
    }

    @Test fun locatedBookIsCopiedAndCleanedUp() {
        val books = folder.newFolder("books")
        val cache = folder.newFolder("cache")
        val fake = FakeFolder(mapOf("Greg Egan/Diaspora.epub" to BookFixtures.epub(books, chapters = listOf("<p>Myopia.</p>"))))
        val source = FolderBookSource(fake, cache)

        val first = source.text("/sdcard/Books/Greg Egan/Diaspora.epub")
        assertTrue("Myopia." in first!!.chapters.single())
        assertNull(source.text("/sdcard/Books/Missing.epub"))
        assertEquals(1, fake.listings)
        assertTrue(cache.listFiles()!!.isEmpty())
    }

    @Test fun unlistableFolderFailsInsteadOfGivingNoText() {
        val source = FolderBookSource(FakeFolder(emptyMap(), listingFails = true), folder.newFolder("cache"))
        assertFailsWith<SecurityException> { source.text("/sdcard/Books/Diaspora.epub") }
    }

    @Test fun unlistableSubfolderIsSkippedButUnlistableRootFails() {
        val tree = mapOf(
            "root" to listOf(TreeChild("a", "Locked", isDirectory = true), TreeChild("x", "x.epub", false), TreeChild("b", "Egan", true)),
            "b" to listOf(TreeChild("y", "Diaspora.epub", isDirectory = false)),
        )
        fun children(id: String) = tree[id] ?: throw SecurityException("cannot list $id")

        assertEquals(mapOf("x.epub" to "x", "Egan/Diaspora.epub" to "y"), walkTree("root", ::children))
        assertFailsWith<SecurityException> { walkTree("missing", ::children) }
    }

    @Test fun brokenBookIsNullAndDoesNotStopOthers() {
        val books = folder.newFolder("books")
        val cache = folder.newFolder("cache")
        val broken = File(books, "broken.epub").apply { writeText("not a zip") }
        val fake = FakeFolder(mapOf("broken.epub" to broken, "good.epub" to BookFixtures.epub(books, name = "good.epub")))
        val source = FolderBookSource(fake, cache)

        assertNull(source.text("/sdcard/broken.epub"))
        assertNull(source.text("/sdcard/broken.epub"))
        assertNotNull(source.text("/sdcard/good.epub"))
        assertEquals(3, fake.copies)
        assertTrue(cache.listFiles()!!.isEmpty())
    }

    @Test fun outOfMemoryOnOneBookGivesItNoText() {
        val books = folder.newFolder("books")
        val fake = FakeFolder(mapOf("huge.epub" to File(books, "huge.epub"), "good.epub" to BookFixtures.epub(books, name = "good.epub")),
            outOfMemory = "huge.epub")
        val source = FolderBookSource(fake, folder.newFolder("cache"))

        assertNull(source.text("/sdcard/huge.epub"))
        assertNotNull(source.text("/sdcard/good.epub"))
    }
}

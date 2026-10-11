package org.lattenwald.moonvocab.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.lattenwald.moonvocab.domain.mark.MarkStyle
import org.lattenwald.moonvocab.domain.mark.RawMark
import org.lattenwald.moonvocab.domain.mark.StyleCombo

class NewestBackupTest {
    private val mark = RawMark("k", "/b/Diaspora.epub", "Diaspora", 0, 0, "myopia", StyleCombo(MarkStyle.WAVY, -28160), 1L)

    @Test fun skipsNewerIncompleteBackupsAndStopsAtTheFirstComplete() {
        val truncated = BackupDocument("1", "2026-10-11.mrpro", lastModified = 300)
        val good = BackupDocument("2", "2026-10-10.MRPRO", lastModified = 200)
        val older = BackupDocument("3", "2026-10-09.mrpro", lastModified = 100)
        val notBackup = BackupDocument("4", "notes.txt", lastModified = 900)
        val read = mutableListOf<BackupDocument>()

        val pick = pickNewestComplete(listOf(older, notBackup, good, truncated)) { doc ->
            read += doc
            if (doc == truncated) BackupContents.Incomplete("truncated") else BackupContents.Complete(listOf(mark), 0)
        }

        assertEquals(good, pick.chosen)
        assertEquals(listOf(mark), pick.marks)
        assertEquals(listOf(truncated), pick.skipped)
        assertEquals(listOf(truncated, good), read)
    }

    @Test fun backupThatThrowsIsSkipped() {
        val unreadable = BackupDocument("1", "new.mrpro", lastModified = 2)
        val good = BackupDocument("2", "old.mrpro", lastModified = 1)

        val pick = pickNewestComplete(listOf(good, unreadable)) { doc ->
            if (doc == unreadable) throw SecurityException("permission revoked") else BackupContents.Complete(listOf(mark), 0)
        }

        assertEquals(good, pick.chosen)
        assertEquals(listOf(unreadable), pick.skipped)
    }

    @Test fun withoutAnyCompleteBackupNothingIsChosenAndAllAreSkippedNewestFirst() {
        val a = BackupDocument("1", "a.mrpro", lastModified = 1)
        val b = BackupDocument("2", "b.mrpro", lastModified = 2)

        val pick = pickNewestComplete(listOf(a, b)) { BackupContents.Incomplete("truncated") }

        assertNull(pick.chosen)
        assertEquals(emptyList<RawMark>(), pick.marks)
        assertEquals(listOf(b, a), pick.skipped)
    }
}

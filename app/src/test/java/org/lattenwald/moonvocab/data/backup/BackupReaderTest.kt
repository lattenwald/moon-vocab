package org.lattenwald.moonvocab.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.lattenwald.moonvocab.domain.mark.MarkStyle
import org.lattenwald.moonvocab.domain.mark.StyleCombo
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupReaderTest {
    @get:Rule val folder = TemporaryFolder()
    private val tmp get() = folder.root

    private val rows = listOf(
        NoteRow("Diaspora", "/sdcard/Books/Greg Egan/Diaspora.epub", 3, 1011, -28160, 1_700_000_000_000, "myopia", bak = "1"),
    )

    @Test fun readsWavyMarkAndSkipsBookmarksAndBlankText() {
        val f = MrproFixture.build(tmp, listOf(
            NoteRow("Diaspora", "/sdcard/Books/Greg Egan/Diaspora.epub", 3, 1011, -28160, 1_700_000_000_000, "myopia", bak = "1", split = 2),
            NoteRow("Diaspora", "/sdcard/Books/Greg Egan/Diaspora.epub", 3, 1020, 1996532479, 1_700_000_000_500, "x", bookmark = "(0.4%) chapter"),
            NoteRow("Diaspora", "/sdcard/Books/Greg Egan/Diaspora.epub", 3, 1030, -28160, 1_700_000_000_900, "  ", bak = "1")))
        val c = BackupReader(tmp).read(f) as BackupContents.Complete
        assertEquals(1, c.marks.size); assertEquals(1, c.bookmarksSkipped)
        assertEquals(StyleCombo(MarkStyle.WAVY, -28160), c.marks[0].combo)
        assertEquals("myopia", c.marks[0].text); assertEquals(3, c.marks[0].chapter); assertEquals(1011, c.marks[0].position)
        assertEquals(2, c.marks[0].split)
        assertTrue(tmp.listFiles()!!.none { it.name.startsWith("mrbooks-") })
    }

    @Test fun truncatedBeforeDatabaseIsIncomplete() = assertEquals(
        BackupContents.Incomplete("no database in backup"),
        BackupReader(tmp).read(MrproFixture.build(tmp, rows, Cut.BEFORE_DB)),
    )

    @Test fun truncatedInsideDatabaseIsIncomplete() {
        assertTrue(BackupReader(tmp).read(MrproFixture.build(tmp, rows, Cut.INSIDE_DB)) is BackupContents.Incomplete)
        assertTrue(tmp.listFiles()!!.none { it.name.startsWith("mrbooks-") })
    }
}

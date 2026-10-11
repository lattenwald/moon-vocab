package org.lattenwald.moonvocab.data.books

import java.text.Normalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BookLocatorTest {
    private val rightSingleQuote = Char(0x2019)
    private val hackersMind = "Bruce Schneier/A Hacker${rightSingleQuote}s Mind.epub"
    private val loc = BookLocator(listOf("Greg Egan/Diaspora.epub", "Other/Diaspora.epub",
        hackersMind, "Карибский кризис/Michael Dobbs - One minute to midnight.epub"))

    @Test fun longestSuffixWins() = assertEquals("Greg Egan/Diaspora.epub", loc.locate("/sdcard/Books/Greg Egan/Diaspora.epub"))

    @Test fun curlyApostropheMatchesStraight() =
        assertEquals(hackersMind, loc.locate("/sdcard/Books/Bruce Schneier/A Hacker's Mind.epub"))

    @Test fun cyrillicDecomposedFolder() = assertNotNull(loc.locate(Normalizer.normalize(
        "/sdcard/Books/Карибский кризис/Michael Dobbs - One minute to midnight.epub", Normalizer.Form.NFD)))

    @Test fun decomposedFolderOutranksShorterPathWithSameFileName() {
        val dobbs = "Карибский кризис/Michael Dobbs - One minute to midnight.epub"
        val loc = BookLocator(listOf("X/Michael Dobbs - One minute to midnight.epub", dobbs))
        assertEquals(dobbs, loc.locate(Normalizer.normalize("/sdcard/Books/$dobbs", Normalizer.Form.NFD)))
    }

    @Test fun missingFileIsNull() = assertNull(loc.locate("/sdcard/Books/X/Nope.epub"))

    @Test fun tieGoesToShorterRelativePath() = assertEquals("Other/Diaspora.epub", loc.locate("/sdcard/Books/X/Diaspora.epub"))
}

package org.lattenwald.moonvocab.data.books

import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MoonEpubTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun ncx(vararg hrefs: String) = """<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1"><navMap>
${hrefs.withIndex().joinToString("\n") { (i, h) -> """<navPoint id="n$i"><navLabel><text>Part $i</text></navLabel><content src="$h"/></navPoint>""" }}
</navMap></ncx>"""

    @Test fun spineFilesMissingFromTheTocJoinTheChapterBeforeThem() {
        val hrefs = listOf("cover.xhtml", "c1.xhtml", "c2.xhtml", "c3.xhtml")
        val bodies = listOf("<p>Cover.</p>", "<p>One.</p>", "<p>Two.</p>", "<p>Three.</p>")
        val file = BookFixtures.zip(tmp.root, "b.epub", buildMap {
            put("META-INF/container.xml", BookFixtures.container("OEBPS/content.opf").toByteArray())
            put("OEBPS/content.opf", BookFixtures.opf("en", "T", "A", hrefs).toByteArray())
            put("OEBPS/toc.ncx", ncx("c1.xhtml", "c3.xhtml").toByteArray())
            hrefs.forEachIndexed { i, h -> put("OEBPS/$h", BookFixtures.xhtml(bodies[i]).toByteArray()) }
        })
        ZipFile(file).use { zip ->
            val book = MoonEpub(zip, file.length())
            assertEquals(3, book.size)
            assertTrue("Cover." in book.parts(0).single())
            val merged = book.parts(1).single()
            assertTrue("One." in merged && "<a name=c2.xhtml><hr2>" in merged && "Two." in merged)
            assertTrue("Three." in book.parts(2).single() && "Two." !in book.parts(2).single())
        }
    }

    @Test fun longChapterIsCutAtTheFirstParagraphAfterEachHundredThousandChars() {
        val paragraph = "<p>" + "x".repeat(993) + "</p>"
        val html = paragraph.repeat(150)
        val cut = html.indexOf("<p", 100_000)
        val parts = splitHtml(html)
        assertEquals(2, parts.size)
        assertEquals(html.substring(0, cut) + "<br/><span align=\"right\"><font color=#6060EE>[1/2]</font></span>", parts[0])
        assertEquals(html.substring(cut), parts[1])
    }
}

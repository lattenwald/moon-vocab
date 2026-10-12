package org.lattenwald.moonvocab.data.books

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BookTextTest {
    @get:Rule val folder = TemporaryFolder()
    private val tmp get() = folder.root

    @Test fun epubChaptersFollowSpineAndLanguageIsNormalized() {
        val b = readBookText(BookFixtures.epub(tmp, "eng", listOf("<p>Alpha.</p>", "<p>Beta &amp; gamma.</p>")), "x.epub")!!
        assertEquals("en", b.language); assertEquals(2, b.chapters.size); assertTrue("Beta & gamma." in b.chapters[1])
        assertTrue("Alpha." in b.chapters[0])
    }

    @Test fun epubSpineHrefsResolveAgainstOpfDirectoryAndMissingEntriesStayEmptyChapters() {
        val f = BookFixtures.zip(tmp, "b.epub", mapOf(
            "META-INF/container.xml" to BookFixtures.container("OPS/pkg/book.opf").toByteArray(),
            "OPS/pkg/book.opf" to BookFixtures.opf("de-DE", "Der Titel", "Die Autorin",
                listOf("Text/Kapitel%201.xhtml", "Text/gone.xhtml", "../c++.xhtml#start")).toByteArray(),
            "OPS/pkg/Text/Kapitel 1.xhtml" to BookFixtures.xhtml("<p>Erstes.</p>").toByteArray(),
            "OPS/c++.xhtml" to BookFixtures.xhtml("<p>Drittes.</p>").toByteArray(),
        ))
        val b = readBookText(f, "b.epub")!!
        assertEquals(3, b.chapters.size)
        assertTrue("Erstes." in b.chapters[0]); assertEquals("", b.chapters[1]); assertTrue("Drittes." in b.chapters[2])
        assertEquals("Der Titel", b.title); assertEquals("Die Autorin", b.author); assertEquals("de", b.language)
    }

    private fun oneChapterEpub(xhtml: String, opfNs: String = "") = readBookText(BookFixtures.zip(tmp, "t.epub", mapOf(
        "META-INF/container.xml" to BookFixtures.container("content.opf").toByteArray(),
        "content.opf" to BookFixtures.opf("en", "T", "A", listOf("c.xhtml"), opfNs).toByteArray(),
        "c.xhtml" to xhtml.toByteArray(),
    )), "t.epub")!!

    @Test fun epubSelfClosingTitleDoesNotSwallowTheBody() {
        val b = oneChapterEpub(BookFixtures.xhtml("<p>Text.</p>").replace("<title>t</title>", "<title/>"))
        assertTrue("Text." in b.chapters.single())
    }

    @Test fun epubAdjacentParagraphsAreSeparated() {
        val b = oneChapterEpub(BookFixtures.xhtml("<div><p>Eins.</p><p>Zwei.<br/>Drei.</p></div><h2>Vier</h2>"))
        assertEquals(listOf("Eins.", "Zwei.", "Drei.", "Vier"), b.chapters.single().trim().split(Regex("\\s+")))
    }

    @Test fun epubSourceLineBreaksInsideParagraphsAreSpaces() {
        val b = oneChapterEpub(BookFixtures.xhtml(
            "<p>one two\nthree four.</p><p>Next.</p><table><tr><td>Cell one.</td><td>Cell two.</td></tr></table><pre>a\n  b</pre>",
        ))
        val paragraphs = b.chapters.single().split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        assertEquals(listOf("one two three four.", "Next.", "Cell one.", "Cell two.", "a", "b"), paragraphs)
        assertTrue("a\n  b" in b.chapters.single())
    }

    @Test fun epubOpfWithPrefixedElements() {
        val b = oneChapterEpub(BookFixtures.xhtml("<p>Text.</p>"), opfNs = "opf:")
        assertTrue("Text." in b.chapters.single()); assertEquals("T", b.title); assertEquals("en", b.language)
    }

    @Test fun fb2SectionsAreChapters() {
        val b = readBookText(BookFixtures.fb2(tmp, "de", listOf(listOf("Erster Satz."), listOf("Zweiter Satz."))), "y.fb2")!!
        assertEquals("de", b.language); assertEquals(listOf("Erster Satz.", "Zweiter Satz."), b.chapters)
    }

    // Moon+ Reader's lastChapter for The Great Book of Amber counts nested sections this way.
    @Test fun fb2NestedSectionsAreChaptersInReadingOrderWithOwnText() {
        val part = "<section><title><p>Part One</p></title>" +
            "<section><title><p>CHAPTER 1</p></title><p>Eins.</p></section>" +
            "<section><title><p>CHAPTER 2</p></title><subtitle>* * *</subtitle><p>Zwei.</p></section></section>"
        val f = BookFixtures.fb2(tmp, sections = listOf(listOf("<title><p>Omnibus</p></title>", part)))
        assertEquals(listOf("Omnibus", "Part One", "CHAPTER 1\nEins.", "CHAPTER 2\n* * *\nZwei."), readBookText(f, "y.fb2")!!.chapters)
    }

    @Test fun fb2ParagraphsJoinWithNewlinesAndNotesBodyIsNotAChapter() {
        val f = BookFixtures.fb2(tmp, sections = listOf(listOf("Eins.", "Zwei.")), notes = listOf("Fußnote."))
        val b = readBookText(f, "y.fb2")!!
        assertEquals(listOf("Eins.\nZwei."), b.chapters)
        assertEquals("Titel", b.title); assertEquals("Vorname Nachname", b.author)
    }

    @Test fun fb2PoemLinesAreTextInDocumentOrder() {
        val poem = "<poem><title><p>Lied</p></title><stanza><v>Zeile eins,</v><v>Zeile zwei.</v></stanza></poem>"
        val f = BookFixtures.fb2(tmp, sections = listOf(listOf("Davor.", poem, "Danach.")))
        assertEquals(listOf("Davor.\nLied\nZeile eins,\nZeile zwei.\nDanach."), readBookText(f, "y.fb2")!!.chapters)
    }

    @Test fun fb2FirstBodyIsMainTextEvenWhenNamed() {
        val f = BookFixtures.fb2(tmp, sections = listOf(listOf("Haupttext.")), notes = listOf("Fußnote."), mainBodyName = "Inhalt")
        assertEquals(listOf("Haupttext."), readBookText(f, "y.fb2")!!.chapters)
    }

    @Test fun fb2StopsAtTheFirstBinary() {
        val tail = """<binary id="c.jpg" content-type="image/jpeg">/9j/4AAQSkZJRgABAQ==</binary><body><section><p>after</p></section></body>"""
        val f = BookFixtures.fb2(tmp, sections = listOf(listOf("Haupttext.")), afterBodies = tail)
        assertEquals(listOf("Haupttext."), readBookText(f, "y.fb2")!!.chapters)
    }

    @Test fun fb2BinaryBeforeTheBodiesIsNotTheEnd() {
        val cover = """<binary id="c.jpg" content-type="image/jpeg">/9j/4AAQSkZJRgABAQ==</binary>"""
        val f = BookFixtures.fb2(tmp, sections = listOf(listOf("Eins."), listOf("Zwei.")), beforeBodies = cover)
        assertEquals(listOf("Eins.", "Zwei."), readBookText(f, "y.fb2")!!.chapters)
    }

    @Test fun zippedFb2NamedFb2IsRead() {
        val inner = BookFixtures.fb2(tmp, sections = listOf(listOf("Im Zip.")))
        val f = BookFixtures.zip(tmp, "y.fb2", mapOf("0a1b.fb2" to inner.readBytes()))
        assertEquals(listOf("Im Zip."), readBookText(f, "y.fb2")!!.chapters)
    }

    @Test fun fb2DeclaredEncodingIsHonoured() {
        val f = BookFixtures.fb2(tmp, "ru", listOf(listOf("Привет, мир.")), title = "Книга", encoding = "windows-1251")
        val b = readBookText(f, "y.FB2")!!
        assertEquals(listOf("Привет, мир."), b.chapters); assertEquals("Книга", b.title)
    }

    @Test fun fb2ZipReadsTheFb2Inside() {
        val inner = BookFixtures.fb2(tmp, sections = listOf(listOf("Im Zip.")))
        val f = BookFixtures.zip(tmp, "y.fb2.zip", mapOf("readme.txt" to "x".toByteArray(), "Buch.fb2" to inner.readBytes()))
        assertEquals(listOf("Im Zip."), readBookText(f, "y.fb2.zip")!!.chapters)
    }

    @Test fun fb2EpubIsEpub() = assertNotNull(readBookText(BookFixtures.epub(tmp), "z.fb2.epub"))

    @Test fun mobiIsUnsupported() = assertNull(readBookText(File(tmp, "n.mobi").apply { writeText("x") }, "n.mobi"))

    @Test fun languageCodes() { assertEquals("de", isoLanguage("de-DE")); assertEquals("en", isoLanguage("eng")); assertEquals("de", isoLanguage("ger")); assertNull(isoLanguage("")); assertNull(isoLanguage("UND")) }

    @Test fun languageCodesMustBeIso6391() {
        assertNull(isoLanguage("russian")); assertNull(isoLanguage("xx")); assertNull(isoLanguage("haw"))
        assertEquals("ru", isoLanguage("RU")); assertEquals("en", isoLanguage("en_US")); assertEquals("de", isoLanguage("ger"))
    }
}

package org.lattenwald.moonvocab.data.books

import java.io.InputStream
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

internal object Fb2Text {
    // Parsed from bytes: FB2 files are often windows-1251, declared only in the XML prolog.
    fun read(input: InputStream): BookText {
        val doc = Jsoup.parse(input, null, "", Parser.xmlParser())
        val info = doc.selectFirst("description > title-info")
        val author = info?.selectFirst("author")?.let { a ->
            listOfNotNull(a.childText("first-name"), a.childText("last-name")).joinToString(" ").ifEmpty { null }
        }
        // FB2 spec: the first body is the main text and may be named; later named bodies hold notes and comments.
        val bodies = doc.select("FictionBook > body")
        val chapters = bodies.filterIndexed { i, body -> i == 0 || !body.hasAttr("name") }
            .flatMap { body -> body.children().filter { it.nameIs("section") } }
            .map { section -> section.select("p, v").joinToString("\n") { it.text() } }
        return BookText(
            title = info?.childText("book-title"),
            author = author,
            language = isoLanguage(info?.childText("lang")),
            chapters = chapters,
        )
    }

    private fun Element.childText(tag: String): String? = selectFirst("> $tag")?.text()?.trim()?.ifEmpty { null }
}

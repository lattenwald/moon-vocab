package org.lattenwald.moonvocab.data.books

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

internal object Fb2Text {
    private val BODY = "<body".toByteArray(Charsets.US_ASCII)
    private val BINARY = "<binary".toByteArray(Charsets.US_ASCII)

    // Parsed from bytes: FB2 files are often windows-1251, declared only in the XML prolog.
    // FB2 places binaries (base64 images) after the bodies, though some files put one before them.
    fun read(input: InputStream): BookText {
        val bytes = input.readBytes()
        val bodyStart = bytes.indexOf(BODY, 0).takeIf { it >= 0 } ?: 0
        val end = bytes.indexOf(BINARY, bodyStart).takeIf { it >= 0 } ?: bytes.size
        val doc = Jsoup.parse(ByteArrayInputStream(bytes, 0, end), null, "", Parser.xmlParser())
        val info = doc.selectFirst("description > title-info")
        val author = info?.selectFirst("author")?.let { a ->
            listOfNotNull(a.childText("first-name"), a.childText("last-name")).joinToString(" ").ifEmpty { null }
        }
        // FB2 spec: the first body is the main text and may be named; later named bodies hold notes and comments.
        val bodies = doc.select("FictionBook > body")
        val chapters = bodies.filterIndexed { i, body -> i == 0 || !body.hasAttr("name") }
            .flatMap { body -> body.select("section") }
            .map { section -> section.select("p, v, subtitle").filter { it.closest("section") === section }.joinToString("\n") { it.text() } }
        return BookText(
            title = info?.childText("book-title"),
            author = author,
            language = isoLanguage(info?.childText("lang")),
            chapters = chapters,
        )
    }

    private fun ByteArray.indexOf(part: ByteArray, from: Int): Int {
        outer@ for (i in from..size - part.size) {
            for (j in part.indices) if (this[i + j] != part[j]) continue@outer
            return i
        }
        return -1
    }

    private fun Element.childText(tag: String): String? = selectFirst("> $tag")?.text()?.trim()?.ifEmpty { null }
}

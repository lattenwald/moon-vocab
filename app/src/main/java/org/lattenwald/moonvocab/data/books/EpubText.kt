package org.lattenwald.moonvocab.data.books

import android.util.Log
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser

internal object EpubText {
    fun read(file: File): BookText = ZipFile(file).use { zip ->
        fun xml(entry: ZipEntry): Document = zip.getInputStream(entry).use { Jsoup.parse(it, null, "", Parser.xmlParser()) }
        fun xml(path: String): Document = xml(zip.getEntry(path) ?: throw IOException("no $path in ${file.name}"))
        // Elements are matched in any namespace: some OPFs write <opf:spine>, <opf:item>.
        val opfPath = xml("META-INF/container.xml").selectFirst("*|rootfile[full-path]")?.attr("full-path")
            ?: throw IOException("no rootfile in ${file.name}")
        val opf = xml(opfPath)
        val opfDir = opfPath.substringBeforeLast('/', "")
        val hrefs = opf.select("*|manifest > *|item").associate { it.attr("id") to it.attr("href") }
        // A spine entry that can't be read stays an empty chapter, so later ones keep Moon+ Reader's numbers.
        val chapters = opf.select("*|spine > *|itemref")
            .map { itemref -> hrefs[itemref.attr("idref")]?.let { zip.getEntry(resolve(opfDir, it)) } }
            // XML, not HTML, parsing: the HTML parser reads a self-closing <title/> as an open title that swallows the body.
            .map { entry -> entry?.let { xml(it).selectFirst("*|body")?.let(::text) }.orEmpty() }
        fun meta(tag: String) = opf.selectFirst("*|metadata *|$tag")?.text()?.trim()?.ifEmpty { null }
        val moon = try {
            MoonEpubText(zip, file.length()).chapters()
        } catch (e: Exception) {
            Log.w(TAG, "no Moon+ text for ${file.name}", e)
            null
        }
        BookText(title = meta("title"), author = meta("creator"), language = isoLanguage(meta("language")), chapters = chapters, moon = moon)
    }

    private const val TAG = "EpubText"

    private val BLOCKS = listOf(
        "p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "li", "blockquote", "pre", "tr", "dt", "dd", "br",
        "section", "article", "aside", "header", "footer", "figcaption", "caption", "td", "th", "hr", "ul", "ol", "dl", "table", "address",
    ).joinToString { "*|$it" }
    private val WHITESPACE = Regex("\\s+")

    // Source XHTML hard-wraps lines, and SentenceFinder reads a newline as a paragraph break.
    // Converted books often write <p>a</p><p>b</p> with no whitespace between, which wholeText() would glue together.
    private fun text(body: Element): String {
        for (element in body.select("*")) {
            if (element.closest("*|pre") != null) continue
            element.textNodes().forEach { it.text(it.wholeText.replace(WHITESPACE, " ")) }
        }
        body.select(BLOCKS).forEach { it.after(TextNode("\n")) }
        return body.wholeText()
    }

    private fun resolve(dir: String, href: String): String {
        val path = decode(href.substringBefore('#'))
        val segments = ArrayDeque<String>()
        val joined = if (path.startsWith('/')) path else "$dir/$path"
        for (segment in joined.split('/')) when (segment) {
            "", "." -> Unit
            ".." -> segments.removeLastOrNull()
            else -> segments.addLast(segment)
        }
        return segments.joinToString("/")
    }

    // URLDecoder is for form encoding: '+' must survive, and a stray '%' must not fail the book.
    private fun decode(href: String): String =
        runCatching { URLDecoder.decode(href.replace("+", "%2B"), "UTF-8") }.getOrDefault(href)
}

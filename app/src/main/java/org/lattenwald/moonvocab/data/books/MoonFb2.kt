package org.lattenwald.moonvocab.data.books

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.util.Base64
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

// Port of Moon+ Reader Pro 10.7's Fb2.java: the chapters a note's lastChapter counts, and their HTML. No XML parsing, as there.
class MoonFb2 internal constructor(private val document: String) {
    private class Chapter(var name: String, var text: String, val title: String?) {
        var indent = 0
        var hasSub = false
        var raw = false
    }

    private class FootNote(val tag: String, val title: String, val html: String)

    private val s = fb2Body(document)
    private val chapters = mutableListOf<Chapter>()
    private val footNotes = mutableListOf<FootNote>()
    private var firstSecPos = -1
    private val bookName = between("<book-title>", "</book-title>")?.let(::fromHtmlText).orEmpty()
    private val annotation = between("<annotation>", "</annotation>")?.takeUnless { fromHtmlText(it).isJavaBlank() }
    private var epigraph: String? = null
    private val html = HashMap<Int, String>()

    init {
        val b0 = s.indexOf("<body")
        val b1 = s.indexOf("</body>", b0)
        var notes = s.indexOf("<body name=\"notes\"", b1)
        if (notes == -1) notes = s.indexOf("<body name=", b1)
        val end = s.lastIndexOf("</body>", if (notes != -1) notes else s.length)
        chaptersFromBody(b0, end)
        val gt = s.indexOf('>', b0)
        if (firstSecPos > 0 && firstSecPos - b0 > 10 && gt > 0 && firstSecPos - gt > 5) epigraph = epigraph(gt)
        footNotes(notes)
        // Fb2.addFootNotesAsChapter: its text is never run through reverseFb2Tag.
        if (footNotes.isNotEmpty()) {
            chapters += Chapter("", footNotes.joinToString("") { "<h5>${it.title}</h5>${it.html}" }, null).also { it.raw = true }
        }
    }

    val size: Int get() = chapters.size

    fun parts(chapter: Int): List<String> = splitHtml(chapterHtml(chapter))

    private val images: Map<String, IntRange> by lazy { binaries(document) }
    private val imageSizes = HashMap<String, Pair<Double, Double>?>()

    /** Pixel size of the FB2 binary an `<img src>` names. */
    fun imageSize(name: String): Pair<Double, Double>? = imageSizes.getOrPut(name) {
        images[name]?.let { r ->
            val b64 = document.substring(r.first, r.last + 1).replace("&#xd;", "\r").replace("&#xa;", "\n")
            runCatching { imageDimensions(Base64.getMimeDecoder().decode(b64)) }.getOrNull()
        }
    }

    // Fb2.getChapterText, assuming enoughMemory(): only FB2s of tens of megabytes take the dealSplitHtml path.
    private fun chapterHtml(i: Int): String = html.getOrPut(i) {
        val ch = chapters.getOrNull(i) ?: return ""
        if (ch.raw) return@getOrPut ch.text
        var h = "<h2>" + fixedTitle(ch) + "</h2>"
        if (ch.hasSub && h.indexOf("<sup") > 0) h = "<br>$h"
        val t = h + reverseFb2Tag(ch.text)
        if (i == 0) annotationHead + t else t
    }

    private fun between(open: String, close: String): String? {
        val i = s.indexOf(open)
        val j = s.indexOf(close, i)
        return if (i == -1 || j == -1) null else s.substring(i + open.length, j)
    }

    private fun chapterData(str: String): Chapter {
        val i = str.indexOf("<title>")
        val j = str.indexOf("</title>")
        if (i == -1 || j == -1) return Chapter(UNTITLED, str, null)
        val t = str.substring(i + 7, j)
        val name = t.replace("\n", " ").replace(TAG_CR_TAB, "").replace("  ", " ").replace("  ", " ").javaTrim()
        return Chapter(name, str.substring(j + 8), t)
    }

    // Fb2.getChaptersFromBody: a section without a literal "<title>" joins the chapter before it.
    private fun chaptersFromBody(start: Int, end: Int) {
        var cur = start
        var indent = 0
        var found = false
        while (true) {
            var a = s.indexOf("<section", cur)
            if (a == -1 || a > end) break
            val c = s.indexOf("</section>", a)
            if (c == -1 || c > end) break
            if (firstSecPos == -1) firstSecPos = a
            var b: Int
            while (true) {
                b = s.indexOf("<section", a + 8)
                if (b == -1 || b > c) break
                val head = s.substring(s.indexOf('>', a) + 1, b)
                val ch = chapterData(head)
                val titled = "<title>" in head
                if (chapters.isNotEmpty() && !titled) {
                    chapters.last().text += ch.text
                } else if (titled || ch.text.length > 10 || !ch.text.isJavaBlank()) {
                    ch.indent = indent
                    chapters += ch
                }
                indent++
                a = b
            }
            val p = c + 10
            val d = s.indexOf("</section", p)
            var extra = ""
            if (d > p && (b == -1 || d < b)) {
                val t = s.substring(p, d).javaTrim()
                if (t.length > 1 && (t.length > 10 || fromHtmlText(t).isNotEmpty())) extra = (if (t.length > 10) PAGE_BREAK else "") + t
            }
            val body = s.substring(s.indexOf('>', a) + 1, c) + extra
            val ch = chapterData(body)
            if (chapters.isNotEmpty() && "<title>" !in body) {
                chapters.last().text += ch.text
            } else {
                ch.indent = indent
                chapters += ch
            }
            found = true
            val next = s.indexOf("<section", c)
            if (next == -1 || next > end) break
            var e = c
            while (true) {
                e = s.indexOf("</section>", e + 8)
                if (e == -1 || e > next) break
                indent--
            }
            cur = next
        }
        if (!found && end > cur && cur != -1) {
            val ch = chapterData(s.substring(s.indexOf('>', cur) + 1, end))
            if (ch.text.isNotEmpty()) chapters += ch
        }
        chapters.zipWithNext { x, y -> x.hasSub = y.indent > x.indent }
        chapters.firstOrNull()?.let { if (it.name == UNTITLED) it.name = bookName }
    }

    private fun epigraph(gt: Int): String? {
        val sub = s.substring(gt + 1, firstSecPos)
        if (fromHtmlText(sub).isJavaBlank()) return null
        if (sub.indexOf("<title>") == -1 || sub.indexOf("</title>") == -1) return sub
        val r = sub.replace("<title>", "<h2>").replace("</title>", "</h2>")
        val k = r.lastIndexOf("</p>")
        if (k == -1) return r
        return r.substring(0, k).replace("<p>", "").replace("</p>", "<br>") + r.substring(k + 4)
    }

    // Fb2.getNotesFromBody2: only top-level `<section ` with id="…"; a note's own links see only the notes before it.
    private fun footNotes(from: Int) {
        if (from == -1) return
        var i = from
        try {
            while (true) {
                val a = s.indexOf("<section ", i)
                if (a == -1) return
                val c = s.indexOf("</section>", a)
                if (c == -1) return
                val gt = s.indexOf('>', a)
                val head = s.substring(a, gt)
                val k = head.indexOf("id=\"")
                val q = if (k == -1) -1 else head.indexOf('"', k + 4)
                if (q != -1) {
                    var body = s.substring(gt, c)
                    val te = body.indexOf("</title>")
                    val ts = body.indexOf("<title>")
                    val title = if (ts != -1) body.substring(ts + 7, te).replace(TAG_NL_CR_TAB, "").javaTrim() else ""
                    if (te != -1) body = body.substring(te + 8)
                    footNotes += FootNote(head.substring(k + 4, q), title, replaceFootnoteAndImage(body))
                }
                i = c
            }
        } catch (_: RuntimeException) {
            // Moon+ logs the error and keeps the notes it has.
        }
    }

    private val annotationHead: String by lazy {
        val a = annotation
        val e = epigraph
        when {
            a == null && e == null -> ""
            a == null -> reverseFb2Tag("<i>$e</i>$PAGE_BREAK")
            else -> reverseFb2Tag("<div class=\"annotation\">$a</div>$PAGE_BREAK" + (e?.let { it + PAGE_BREAK } ?: ""))
        }
    }

    private fun fixedTitle(ch: Chapter): String {
        val title = ch.title ?: return if (ch.name == UNTITLED) "" else ch.name
        var t = title.replace("\n", " ").replace(TAB_CR, "").javaTrim()
        val k = t.lastIndexOf("</p>")
        if (k != -1) t = t.substring(0, k).replace(P_OPEN, "").replace("</p>", "<br>") + t.substring(k + 4)
        return replaceFootnoteAndImage(reverseFb2Tag(t))
    }

    // Fb2.reverseFb2Tag. LAST_STANZA is fitted, not decompiled: Moon+ shows one line break less per <poem> than its code.
    private fun reverseFb2Tag(str: String): String = replaceFootnoteAndImage(LAST_STANZA.replace(str, "\$1</poem>"))
        .replace("<poem>", "<div class=\"poem\">").replace("</poem>", "</div>")
        .replace("<cite>", "<div class=\"cite\">").replace("</cite>", "</div>")
        .replace("<text-author>", "<div class=\"text-author\">").replace("</text-author>", "</div>")
        .replace(EMPHASIS, "<\$1i>")
        .replace("<stanza>", "").replace("</stanza>", "<br>")
        .replace("<a l:", "<a ")
        .replace(VERSE, "&nbsp;&nbsp;&nbsp;").replace("</v>", "<br>")
        .replace("<empty-line/>", "<br><br>").replace("<empty-line>", "<br><br>").replace("</empty-line>", "")
        .replace("<subtitle>", "<h5 class=\"subtitle\">").replace("</subtitle>", "</h5>")
        .replace("<epigraph>", "<div class=\"epigraph\">").replace("</epigraph>", "</div>")
        .replace(STRIKE, "<\$1strike>")
        .replace(TITLE, "<\$1h5>")
        .replace(EMPTY_B, "").replace(EMPTY_P, "")

    private fun replaceFootnoteAndImage(str: String): String {
        var idx = str.indexOf(HREF)
        if (idx == -1) return str
        val sb = StringBuilder(str.length)
        var pos = 0
        var inLink = false
        try {
            while (idx != -1) {
                val lt = str.lastIndexOf('<', idx)
                sb.append(str, pos, lt)
                val c = str[lt + 1]
                val q = str.indexOf('"', idx + 8)
                val ref = str.substring(idx + 8, q)
                var end = q
                var next = -1
                if (c == 'i') {
                    var e1 = str.indexOf("/>", q)
                    val e2 = str.indexOf("</image", q)
                    val paired = e1 == -1 || (e2 != -1 && (e1 > e2 || e2 - e1 < 5))
                    if (paired) e1 = str.indexOf('>', q)
                    val tag = str.substring(lt, e1)
                    val k = tag.indexOf("id=\"")
                    val id = if (k == -1) "" else " id=\"" + tag.substring(k + 4, tag.indexOf('"', k + 5)) + "\""
                    val inline = "inline" in str.substring(q, e1)
                    sb.append("<img").append(id).append(" src=\"").append(ref).append(if (inline) "\" alt=\"nobreak\"/>" else "\"/>")
                    end = if (paired && e2 != -1) e2 else e1
                }
                if (c == 'a') {
                    inLink = true
                    val g = str.indexOf('>', q)
                    end = str.indexOf("</a", g)
                    when {
                        str[g - 1] == '/' -> next = g + 1
                        end == -1 -> return sb.toString()
                        else -> sb.append(footnoteLink(ref, str.substring(g + 1, end), str.substring(str.lastIndexOf('<', g), g)))
                    }
                    inLink = false
                }
                if (next == -1) next = str.indexOf('>', end) + 1
                pos = next
                idx = str.indexOf(HREF, pos)
            }
        } catch (_: RuntimeException) {
            // Moon+ logs the error; it keeps the rest of the text unless the error came from a link.
            if (inLink) return sb.toString()
        }
        sb.append(str, pos, str.length)
        return sb.toString()
    }

    private fun footnoteLink(ref: String, text: String, tag: String): String =
        if (footNotes.any { it.tag == ref }) {
            "<sup><a href=\"?$ref" + (if ("\"note\"" in tag) "\" type=\"note\">" else "\">") + text + "</a></sup>"
        } else {
            "<sup><a href=\"??$ref\">$text</a></sup>"
        }

    companion object {
        /** Null when Moon+ Reader would not open the file as an FB2 either. */
        fun read(file: File, fileName: String = file.name): MoonFb2? {
            val doc = try {
                document(file, fileName.lowercase())
            } catch (_: IOException) {
                null
            } ?: return null
            return try {
                MoonFb2(doc).takeIf { it.size > 0 }
            } catch (_: RuntimeException) {
                null
            }
        }

        private const val PAGE_BREAK = "<hr2>"
        private const val UNTITLED = "..."
        private const val HREF = ":href=\"#"
        private val TAG_CR_TAB = Regex("<.*?>|\r|\t")
        private val TAG_NL_CR_TAB = Regex("<.*?>|\n|\r|\t")
        private val TAB_CR = Regex("\t|\r")
        private val P_OPEN = Regex("<p.*?>")
        private val LAST_STANZA = Regex("</stanza>([\\t\\n\\u000B\\f\\r\\u001C-\\u001F \\u0085\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000]*)</poem>")
        private val EMPHASIS = Regex("<(|.)(emphasis)>")
        private val VERSE = Regex("<(v .*?|v)>")
        private val STRIKE = Regex("<(|.)strikethrough>")
        private val TITLE = Regex("<(|.)(title|title .*?)>")
        private val EMPTY_B = Regex("<b>(| |　| )</b>")
        private val EMPTY_P = Regex("<p>(| |　| )</p>")

        // R.array.charsets, the names A.getValidedEncoding accepts.
        private val CHARSETS = listOf(
            "UTF-7", "UTF-8", "UTF-16", "UTF-16BE", "UTF-16LE", "UTF-32", "UTF-32BE", "UTF-32LE", "US-ASCII", "BIG5", "CP864", "CP866",
            "CP874", "EUC-CN", "EUC-JP", "EUC-KR", "GBK", "GB2312", "KOI8-R", "SHIFT-JIS", "x-JIS7", "x-JIS8", "Macintosh",
            "Adobe-Standard-Encoding", "ISO-8859-1", "ISO-8859-2", "ISO-8859-3", "ISO-8859-4", "ISO-8859-5", "ISO-8859-6", "ISO-8859-7",
            "ISO-8859-8", "ISO-8859-8-I", "ISO-8859-9", "ISO-8859-10", "ISO-8859-11", "ISO-8859-13", "ISO-8859-14", "ISO-8859-15",
            "ISO-8859-16", "ISO-2022-JP", "Windows-950", "Windows-1250", "Windows-1251", "Windows-1252", "Windows-1253", "Windows-1254",
            "Windows-1255", "Windows-1256", "Windows-1257", "Windows-1258",
        )

        private class Source(val bytes: ByteArray, val encoding: String)

        // Fb2.getChaptersFromText. A zip named .fb2 keeps the encoding found in the zip's own first line, so UTF-8 in practice.
        private fun document(file: File, name: String): String? {
            var src = open(file, name, null) ?: return null
            var s = decode(src) ?: return null
            if (!s.startsWith("PK") && s.length > 10 && "<?xml" !in s) {
                src = open(file, name, "UTF-8") ?: return null
                s = decode(src) ?: return null
            }
            if (s.startsWith("PK") && name.endsWith(".fb2")) {
                val entry = ZipFile(file).use { zip -> fb2Entry(zip.entries().asSequence().map { it.name }.toList()) { n -> zip.getEntry(n)?.let { e -> zip.getInputStream(e).use { it.readBytes() } } } }
                s = decode(Source(entry ?: return null, src.encoding)) ?: return null
            }
            return s
        }

        private fun open(file: File, name: String, encoding: String?): Source? {
            if (name.endsWith(".fb2.zip")) {
                val entry = try {
                    ZipFile(file).use { zip -> fb2Entry(zip.entries().asSequence().map { it.name }.toList()) { n -> zip.getEntry(n)?.let { e -> zip.getInputStream(e).use { it.readBytes() } } } }
                } catch (_: IOException) {
                    val bytes = file.readBytes()
                    return if (firstLine(bytes).startsWith("<?xml")) Source(bytes, encoding ?: xmlEncoding(firstLine(bytes))) else null
                }
                return entry?.let { Source(it, encoding ?: xmlEncoding(firstLine(it))) }
            }
            val bytes = file.readBytes()
            return Source(bytes, encoding ?: xmlEncoding(firstLine(bytes)))
        }

        // Fb2.getFb2ZipStream's entry choice: the first entry if it ends in .fb2, else the first *.fb2, else a nested .fb2.zip.
        private fun fb2Entry(names: List<String>, read: (String) -> ByteArray?): ByteArray? {
            if (names.isEmpty()) return null
            val pick = if (names[0].endsWith(".fb2")) names[0] else names.firstOrNull { it.lowercase().endsWith(".fb2") }
            if (pick != null) return read(pick)
            val nested = names.firstOrNull { it.lowercase().endsWith(".fb2.zip") }?.let(read) ?: return null
            val inner = LinkedHashMap<String, ByteArray>()
            ZipInputStream(ByteArrayInputStream(nested)).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    inner[e.name] = z.readBytes()
                }
            }
            return fb2Entry(inner.keys.toList()) { inner[it] }
        }

        // BufferedReader.readLine on the raw bytes, as UTF-8.
        private fun firstLine(bytes: ByteArray): String {
            val end = bytes.indexOfFirst { it == '\n'.code.toByte() || it == '\r'.code.toByte() }.let { if (it == -1) bytes.size else it }
            return String(bytes, 0, end, Charsets.UTF_8)
        }

        // Fb2.getXmlEncoding: the value must be double-quoted; an unknown name means UTF-8.
        internal fun xmlEncoding(line: String): String {
            val i = line.indexOf("encoding=")
            if (i == -1) return "UTF-8"
            val j = line.indexOf('"', i + 11)
            if (j == -1 || j - i >= 50) return "UTF-8"
            val name = line.substring(i + 10, j)
            return CHARSETS.firstOrNull { it.equals(name, ignoreCase = true) } ?: "UTF-8"
        }

        private fun decode(src: Source): String? {
            val cs = runCatching { Charset.forName(src.encoding) }.getOrNull() ?: return null
            return String(src.bytes, cs)
        }

        // Fb2.getFb2Body stops reading at the 8192-char chunk that holds a "<binary" once "</body" has been read.
        private fun fb2Body(s: String): String {
            var start = 0
            while (start < s.length) {
                val end = minOf(s.length, start + 8192)
                val b = s.indexOf("<binary", if (start == 0) 0 else start - 10)
                if (b != -1 && b + 7 <= end) {
                    val body = s.indexOf("</body")
                    if (body != -1 && body + 6 <= end) return s.substring(0, end)
                }
                start = end
            }
            return s
        }

        // Fb2.scanImageStr/getImageStr over the whole file: id → the base64 text's range.
        private fun binaries(s: String): Map<String, IntRange> {
            val out = HashMap<String, IntRange>()
            var pos = 0
            while (true) {
                val close = s.indexOf("</binary>", pos)
                if (close == -1) return out
                val open = s.indexOf("<binary", pos)
                if (open != -1) {
                    val gt = s.indexOf('>', open)
                    var quote = '"'
                    var k = s.indexOf("id=\"", open)
                    if (k == -1) {
                        k = s.indexOf("id='", pos)
                        quote = '\''
                    }
                    val id = if (k == -1) null else s.indexOf(quote, k + 4).takeIf { it != -1 }?.let { s.substring(k + 4, it) }
                    val end = s.indexOf("</binary>", gt)
                    if (id != null && gt != -1 && end > gt) out[id] = gt + 1 until end
                }
                pos = close + 9
            }
        }
    }
}

private val IMG_TAG = Regex("<img[^>]*>", RegexOption.IGNORE_CASE)
private val BR_TAG = Regex("<br[^>]*>", RegexOption.IGNORE_CASE)
private val ANY_TAG = Regex("<[^>]*>")

// Html.fromHtml(s).toString() as far as Moon+'s emptiness tests and book title need it.
internal fun fromHtmlText(s: String): String {
    val t = org.jsoup.parser.Parser.unescapeEntities(
        s.replace("\r\n", "\n").replace('\r', '\n').replace(IMG_TAG, "￼").replace(BR_TAG, "\u0001").replace(ANY_TAG, ""),
        false,
    )
    val out = StringBuilder()
    for (c in t) {
        when (c) {
            ' ', '\n' -> if (out.isNotEmpty() && out.last() != ' ' && out.last() != '\n') out.append(' ')
            '\u0001' -> out.append('\n')
            else -> out.append(c)
        }
    }
    return out.toString()
}

// String.trim and its emptiness test in Java strip only chars up to U+0020, unlike Kotlin's.
internal fun String.javaTrim(): String = dropWhile { it <= ' ' }.dropLastWhile { it <= ' ' }

internal fun String.isJavaBlank(): Boolean = all { it <= ' ' }

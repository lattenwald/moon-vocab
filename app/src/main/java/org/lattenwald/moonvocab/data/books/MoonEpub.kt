package org.lattenwald.moonvocab.data.books

import java.io.ByteArrayInputStream
import java.io.StringReader
import java.net.URLDecoder
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

// Port of Moon+ Reader Pro 10.7's Epub.java: the chapters a note's lastChapter counts, and their HTML.
class MoonEpub(private val zip: ZipFile, private val fileSize: Long) {
    private class Chapter(var name: String, val file: String, var text: String) {
        var idTag: String? = null
        var indent = 0
        var hasSub = false
        var additional = ""
        val used = mutableListOf<String>()
        var broken = false
    }

    private val files: List<String> = zip.entries().asSequence().filter { !it.isDirectory }.map { it.name }.toList()
        .sortedWith(compareBy<String>({ folder(it) }, { it.lowercase() }))
    private val pure: Map<String, String> = files.groupBy(::fileName).filterValues { it.size == 1 }.mapValues { it.value.single() }
    private val raw = HashMap<String, String>()
    private var hasIdStr = false
    private var tocBase = ""
    private var epub3 = false
    private var firstChapterPos = 0
    private var beforeFirstTag: String? = null
    private var beforeFirstTagFile = ""
    private var spine: List<Chapter> = emptyList()
    private var chapters = mutableListOf<Chapter>()

    init {
        val toc = tocFileName() ?: navFileName()
        if (toc != null) {
            tocBase = toc.substring(0, toc.lastIndexOf('/') + 1)
            chapters = parseToc(toc)
            val nav = navFileName()
            if (nav != null && nav != toc) {
                val n = chapters.size
                chapters = parseToc(nav)
                if (chapters.size < n) chapters = parseToc(toc)
            }
        }
        recheckChapters()
    }

    val size: Int get() = chapters.size

    fun parts(chapter: Int): List<String> = splitHtml(chapterText(chapter))

    private fun read(name: String): String = raw.getOrPut(name) {
        zip.getEntry(name)?.let { e -> zip.getInputStream(e).use { it.readBytes().decodeToString() } }.orEmpty()
    }

    private fun body(name: String) = htmlBody(read(name))

    private fun tocFileName(): String? {
        pure["toc.ncx"]?.let { return it }
        var t: String? = null
        for (n in files.asReversed()) {
            val s = n.lowercase()
            if (s == "toc.ncx") return n
            if (s.endsWith("toc.ncx")) {
                if (t == null || !t.endsWith("/toc.ncx") || (s.endsWith("/toc.ncx") && t.length > s.length)) t = n
            } else if (t == null && s.endsWith(".ncx")) {
                t = n
            }
        }
        return t
    }

    private fun opfFileName(): String? {
        pure["content.opf"]?.let { return it }
        var o: String? = null
        for (n in files.asReversed()) {
            val s = n.lowercase()
            if (s == "content.opf") return n
            if (s.endsWith("content.opf")) o = n else if (o == null && s.endsWith(".opf")) o = n
        }
        return o
    }

    private fun navFileName(): String? {
        val s = read(opfFileName() ?: return null)
        val i = s.indexOf("properties=\"nav\"")
        if (i == -1) return null
        val el = s.substring(s.lastIndexOf('<', i).coerceAtLeast(0), s.indexOf('>', i).let { if (it == -1) s.length else it })
        val j = el.indexOf("href=\"")
        if (j == -1) return null
        val href = el.substring(j + 6, el.indexOf('"', j + 7).let { if (it == -1) el.length else it })
        val found = pure[fileName(href)]
            ?: files.asReversed().firstOrNull { it.endsWith(href) }
            ?: href.substringAfterLast('/').let { b -> files.asReversed().firstOrNull { it.endsWith("/$b") || it == b } }
        if (found != null) epub3 = true
        return found
    }

    private fun resolve(href: String): String? {
        pure[fileName(href)]?.let { return it }
        normalize(tocBase + href).let { if (it in files) return it }
        files.firstOrNull { it.endsWith("/$href") || it == href }?.let { return it }
        if (href.startsWith("../")) files.firstOrNull { it.endsWith(href.substring(3)) }?.let { return it }
        return null
    }

    private fun chapter(href: String?, name: String?): Chapter? {
        if (href == null || name == null) return null
        val title = name.replace("\n", "").replace("\r", "").trim()
        val c = if ('#' !in href) {
            Chapter(title, resolve(href) ?: return null, UN_LOAD_TAG)
        } else {
            hasIdStr = true
            Chapter(title, resolve(href.substringBefore('#')) ?: return null, HAS_ID_TAG).also { it.idTag = href.substringAfter('#') }
        }
        c.used += c.file
        return c
    }

    private fun parseToc(name: String): MutableList<Chapter> {
        val out = mutableListOf<Chapter>()
        val root = parseXml(zip, name)?.documentElement ?: return out
        val pre = root.tagName.let { if (':' in it) it.substring(0, it.indexOf(':') + 1) else "" }
        var tree = false

        fun ncx(node: Node, indent: Int) {
            var src: String? = null
            var title: String? = ""
            var sub = false
            for (c in node.childNodes.list()) {
                val ln = c.nodeName.lowercase()
                if (src == null && ln == pre + "content") {
                    val a = c.attributes
                    if (a != null && a.length > 0) src = decode((a.getNamedItem("src") ?: a.item(a.length - 1)).nodeValue)
                }
                if (title.isNullOrEmpty() && ln == pre + "navlabel") {
                    title = c.childNodes.list().firstOrNull { it.nodeName == pre + "text" && it.firstChild != null }?.firstChild?.nodeValue
                }
                if (ln == pre + "navpoint") {
                    tree = true
                    sub = true
                }
            }
            chapter(src, title)?.let {
                it.indent = indent
                out += it
            }
            if (sub) for (c in node.childNodes.list()) if (c.nodeName.lowercase() == pre + "navpoint") ncx(c, indent + 1)
        }
        for (map in root.getElementsByTagName(pre + "navMap").list()) for (c in map.childNodes.list()) ncx(c, 0)

        fun navLi(node: Node, indent: Int) {
            var last: Chapter? = null
            for (c in node.childNodes.list()) {
                if (c.nodeName == "a") {
                    val title = if (c.childNodes.length == 0) "" else c.childNodes.item(0).nodeValue
                    last = chapter(decode((c as Element).getAttribute("href")), title)?.also {
                        it.indent = indent
                        out += it
                    }
                } else if (c.nodeName == "ol") {
                    last?.hasSub = true
                    tree = true
                    for (li in c.childNodes.list()) if (li.nodeName == "li") navLi(li, indent + 1)
                }
            }
        }
        if (epub3 && out.isEmpty()) {
            val navs = root.getElementsByTagName("nav").list()
            if (navs.isNotEmpty()) {
                val k = if (navs.size > 1) navs.indexOfFirst { "toc" in (it as Element).getAttribute("epub:type") }.coerceAtLeast(0) else 0
                for (ol in navs[k].childNodes.list()) if (ol.nodeName == "ol") {
                    for (li in ol.childNodes.list()) if (li.nodeName == "li") navLi(li, 0)
                }
            }
        }
        if (tree) {
            out.forEach { it.indent++ }
            out.zipWithNext { a, b -> a.hasSub = a.indent < b.indent }
        }
        return out
    }

    private fun usedAnywhere(file: String) = chapters.any { file in it.used }

    private fun additionalText(sp: List<Chapter>, ch: Chapter, a: Int, b: Int, before: Boolean, lazyReload: Boolean): Pair<String, List<Int>>? {
        if ((!before || b <= a) && (a == -1 || b == -1 || b - a <= 1)) return null
        val sb = StringBuilder()
        val taken = mutableListOf<Int>()
        for (k in a + 1 until b) {
            if (k !in sp.indices) continue
            val c = sp[k]
            if (!lazyReload && usedAnywhere(c.file)) continue
            val defer = !lazyReload && !before && b - a > 3
            if (!lazyReload && !before) ch.used += c.file
            if (!defer) {
                val h = body(c.file)
                val short = h.length <= 100 && htmlText(h).isBlank()
                sb.append("<a name=").append(fileName(c.file)).append('>').append(if (k <= 0 || short) "" else PAGE_BREAK).append(h)
            } else if (sb.isEmpty()) {
                sb.append("#MORE#_").append(a).append('|').append(b)
            }
            taken += k
        }
        val s = sb.toString()
        if (s.length > 1 && !before) ch.additional = if (lazyReload) s else ch.additional + s
        return s to taken
    }

    private fun additional(ch: Chapter): String {
        val s = ch.additional
        if (s.startsWith("#MORE#")) {
            val a = s.substring(s.indexOf('_') + 1, s.indexOf('|')).toInt()
            val b = s.substring(s.indexOf('|') + 1).toInt()
            additionalText(spine, ch, a, b, before = false, lazyReload = true)
        }
        return ch.additional
    }

    private fun spineChapters(): List<Chapter> {
        val root = parseXml(zip, opfFileName() ?: return emptyList())?.documentElement ?: return emptyList()
        val pre = root.tagName.let { if (':' in it) it.substring(0, it.indexOf(':') + 1) else "" }
        val items = root.getElementsByTagName(pre + "item").list().ifEmpty { root.getElementsByTagName("opf:item").list() }
        val hrefs = items.map { it as Element }.filter { it.hasAttribute("id") && it.hasAttribute("href") }
            .associate { it.getAttribute("id") to decode(it.getAttribute("href")) }
        val sp = mutableListOf<Chapter>()
        for (ir in root.getElementsByTagName(pre + "itemref").list()) {
            val idref = (ir as Element).takeIf { it.hasAttribute("idref") }?.getAttribute("idref") ?: continue
            chapter(hrefs[idref] ?: continue, idref)?.let {
                it.name = "${sp.size + 1}"
                sp += it
            }
        }
        return sp
    }

    private fun chaptersFromOpf() {
        val sp = spineChapters()
        spine = sp
        if (sp.isEmpty()) return
        if (chapters.isEmpty() || (chapters.size < 2 && sp.size > 2)) {
            chapters = sp.toMutableList()
            return
        }
        val idx = chapters.map { c -> sp.indexOfFirst { it.file == c.file } }
        var before: Pair<String, List<Int>>? = null
        val n = chapters.size
        for (a in 0 until n) {
            val last = a == n - 1
            val start = idx[a]
            var end = if (last) sp.size else idx[a + 1]
            if (!last) for (b in a + 2 until n) if (end > idx[b] && idx[b] > start && idx[b] > 1) end = idx[b]
            additionalText(sp, chapters[a], start, end, before = false, lazyReload = false)
            if (a == 0) additionalText(sp, chapters[a], -1, minOf(end, start), before = true, lazyReload = false)
                ?.takeIf { it.first.length > 1 }?.let { before = it }
        }
        before?.let { (html, taken) ->
            chapters.add(0, Chapter(BOOK, sp[taken.first()].file, html).also { c -> c.used += taken.map { sp[it].file } })
            firstChapterPos++
        }
    }

    private fun recheckChapters() {
        chaptersFromOpf()
        if (chapters.isEmpty()) {
            files.filter { it.substringAfterLast('.', "").lowercase() in setOf("htm", "html", "xhtml") }
                .forEachIndexed { k, n -> chapters += Chapter("${k + 1}", n, UN_LOAD_TAG) }
        }
        if (hasIdStr && chapters.size < 60 && fileSize < 1_000_000) {
            for (i in chapters.indices) if (chapters[i].idTag != null) chapters[i].text = chapterText(i)
            val broken = chapters.count { it.broken }
            if (broken in 3 until chapters.size) chapters.removeAll { it.broken }
        }
        if (hasIdStr && chapters.size > firstChapterPos) {
            chapterText(firstChapterPos)
            beforeFirstTag?.let { html ->
                if (firstChapterPos == 0) chapters.add(0, Chapter(BOOK, beforeFirstTagFile, html))
                else chapters[0].text = chapters[0].text + PAGE_BREAK + html
            }
        }
    }

    private fun textWithIdTag(i: Int, beforeOnly: Boolean = false): String? {
        val ch = chapters[i]
        val nextId = chapters.getOrNull(i + 1)?.idTag
        var ft = if (ch.file.isNotEmpty()) body(ch.file) else ch.text
        var p = 0
        ch.idTag?.let { id ->
            p = idPos(ft, id, 0)
            if (p == -1 && idPos(read(ch.file), id, 0) > 0) p = 0
            if (i == firstChapterPos && p > 100) {
                beforeFirstTag = textBeforeTag(ft, p, 40)
                beforeFirstTagFile = ch.file
            }
        }
        if (p > 0 && i > 0 && chapters[i - 1].file != ch.file) {
            if (p > 500 || (p > 100 && p > ft.length / 4.0)) {
                val tb = textBeforeTag(ft, p, 20)
                if (tb == null) p = 0 else if (beforeOnly) return tb
            } else {
                p = 0
            }
        }
        if (beforeOnly) return null
        if (p != -1) {
            var end = -1
            val next = chapters.getOrNull(i + 1)
            if (next != null && next.file == ch.file) {
                var q = if (nextId != null) idPos(ft, nextId, p) else -1
                if (q == -1) {
                    // Moon+'s getIdPTagInHtml always ends up here: its checks compare with 1, not -1.
                    var ptag = "id=\"${ch.idTag}\""
                    for (k in 1 until ptag.length) if (ptag[k].isDigit()) {
                        ptag = ptag.substring(0, k)
                        break
                    }
                    if (ptag.length > 1) q = ft.indexOf(" $ptag", p)
                    if (q == -1) q = ft.indexOf(" id=", p)
                    if (q != -1 && nextId == null) {
                        val quote = if ('"' in ptag) '"' else if ('\'' in ptag) '\'' else null
                        val qq = quote?.let { ft.indexOf(it, q) } ?: -1
                        if (qq != -1) {
                            next.text = HAS_ID_TAG
                            next.idTag = ft.substring(qq + 1, ft.indexOf(quote!!, qq + 2).let { if (it == -1) ft.length else it })
                        }
                    }
                }
                if (q != -1) {
                    end = ft.lastIndexOf('<', q)
                    if (end == -1) end = ft.lastIndexOf('\n', q) + 2
                }
            }
            if (end == -1) end = ft.length
            var j = ft.lastIndexOf('<', p)
            if (j != -1) {
                while (true) {
                    p = j
                    if (p <= 0 || ft[p - 1] != '>') break
                    j = ft.lastIndexOf('<', p - 1)
                    if (j == -1 || ft[j + 1] == '/') break
                }
            } else {
                val k = ft.lastIndexOf('\n', p) + 2
                if (k > 5) p = k
            }
            ft = ft.substring(p, maxOf(p, minOf(end, ft.length)))
        } else {
            if (i > 0 && !chapters[i - 1].hasSub && chapters[i - 1].file == ch.file) {
                val add = additional(ch)
                if (add.isEmpty()) ch.broken = true
                return "<h2>${ch.name}</h2>$add"
            }
            if (i == 0 && nextId != null) {
                val q = idPos(ft, nextId, 0)
                if (q > 0) ft.lastIndexOf('<', q).takeIf { it > 0 }?.let { ft = ft.substring(0, it) }
            }
        }
        return ft + additional(ch)
    }

    private fun nextHasId(i: Int) = chapters.getOrNull(i + 1)?.idTag != null

    private fun chapterText(i: Int): String {
        val ch = chapters.getOrNull(i) ?: return ""
        if (ch.text != HAS_ID_TAG && !(ch.text == UN_LOAD_TAG && nextHasId(i))) {
            if (ch.text == UN_LOAD_TAG) {
                ch.text = if (ch.hasSub && chapters.getOrNull(i + 1)?.file == ch.file) "" else body(ch.file) + additional(ch)
            }
            return ch.text
        }
        var t = textWithIdTag(i).orEmpty()
        val next = chapters.getOrNull(i + 1)
        if (next?.idTag != null && next.file != ch.file) {
            textWithIdTag(i + 1, beforeOnly = true)?.let { tb ->
                val k = t.indexOf(CHAPTER_END_HINT)
                t = (if (k != -1) t.substring(0, k) + "<br>" else t) + PAGE_BREAK + tb
                if (next.file !in ch.used) ch.used += next.file
            }
        }
        var s = deleteHtmlComments(t)
        if (s.length < 200 && "<im" !in s && ch.name !in s && htmlText(s).isBlank()) s = "<center><h2>${ch.name}</h2></center>"
        ch.text = s
        return s
    }

    private companion object {
        const val PAGE_BREAK = "<hr2>"
        const val HAS_ID_TAG = "HAS_ID_TAG"
        const val UN_LOAD_TAG = "UN_LOAD_TAG"
        const val CHAPTER_END_HINT = " <br/><span align=\"right\"><font color=#6060EE><u>("
        const val BOOK = "<book>"
    }
}

private fun folder(p: String) = p.substringBeforeLast('/', "")

private fun fileName(p: String): String = p.substringAfter('?').substringAfterLast('/')

private fun normalize(path: String): String {
    val segments = ArrayDeque<String>()
    for (s in path.split('/')) when (s) {
        "", "." -> Unit
        ".." -> segments.removeLastOrNull()
        else -> segments.addLast(s)
    }
    return segments.joinToString("/")
}

// Uri.decode keeps '+', and a stray '%' must not drop the entry.
private fun decode(s: String): String = runCatching { URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") }.getOrDefault(s)

private fun org.w3c.dom.NodeList.list(): List<Node> = List(length, ::item)

private fun parseXml(zip: ZipFile, name: String): Document? = runCatching {
    val bytes = zip.getInputStream(zip.getEntry(name) ?: return null).use { it.readBytes() }
    val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    builder.setEntityResolver { _, _ -> InputSource(StringReader("")) } // never fetch a DTD
    builder.parse(ByteArrayInputStream(bytes))
}.getOrNull()

// T.getHtmlBody. The lowercase copy is only searched, so it must keep the length.
internal fun htmlBody(s: String): String {
    val lo = s.lowercase().takeIf { it.length == s.length } ?: s
    for ((open, close) in listOf("<body" to "</body>", "<html" to "</html>")) {
        val i = lo.indexOf(open)
        if (i == -1) continue
        val j = lo.indexOf('>', i)
        if (j == -1) return ""
        val k = lo.indexOf(close, j)
        return if (k != -1) s.substring(j + 1, k) else s.substring(j + 1)
    }
    return s
}

private val TAG = Regex("<[^>]+>")

// Approximates T.html2Text; Moon+ uses it only to test for empty or short text.
internal fun htmlText(s: String): String = org.jsoup.parser.Parser.unescapeEntities(s.replace(TAG, ""), false)

private fun deleteHtmlComments(html: String): String {
    var s = html
    var i = 0
    while (true) {
        i = s.indexOf("<!--", i)
        if (i == -1) return s
        val j = s.indexOf("-->", i)
        if (j == -1) return s
        s = s.substring(0, i) + s.substring(j + 3)
    }
}

private fun idPos(s: String, id: String, start: Int): Int {
    for (pattern in listOf("id=\"$id\"", "id='$id'", "name=\"$id\"", "name='$id'")) s.indexOf(pattern, start).let { if (it != -1) return it }
    if (id.length >= 3) {
        s.indexOf("\"$id\"", start).let { if (it != -1) return it }
        return s.indexOf("'$id'", start)
    }
    return -1
}

private fun textBeforeTag(s: String, p: Int, minText: Int): String? {
    var b = s.indexOf("<body")
    if (b != -1) b = s.indexOf('>', b) + 1
    if (b == -1) b = 0
    var e = s.lastIndexOf('<', p)
    if (e == -1) e = s.lastIndexOf('\n', p) + 2
    if (e <= b) return null
    val sub = s.substring(b, minOf(e, s.length))
    return sub.takeIf { it.length >= 80 || htmlText(it).length >= minText }
}

private const val MAX_HTML_CHAPTER_SIZE = 100_000

// A.cleanHtml for HTML content, with the user's trimBlankSpace off.
private fun cleanHtml(html: String): String {
    val s = html.replace("­", "")
    if (s.length > 100_000) return s
    var t = s.trim()
    while (true) {
        t = when {
            t.endsWith("<br>") -> t.dropLast(4)
            t.endsWith("　") || t.endsWith(" ") -> t.dropLast(1)
            else -> return t
        }
    }
}

/** A.adjustChapterHtml + A.createSplitHtmls: the parts `lastSplitIndex` numbers. */
internal fun splitHtml(s: String, max: Int = MAX_HTML_CHAPTER_SIZE): List<String> {
    if (s.length <= max) return listOf(cleanHtml(s))
    val out = mutableListOf<String>()
    val n = s.length
    var i2 = max
    var i3 = 0
    var carry: String? = null
    while (true) {
        var j: Int
        if (i2 <= n - 1) {
            j = listOf("<p", "<P", "<div", "<DIV").firstNotNullOfOrNull { s.indexOf(it, i2).takeIf { k -> k != -1 } } ?: -1
            if (j == -1 || j > max / 5 + i2) j = s.indexOf('>', i2).let { if (it != -1) it + 1 else n }
        } else {
            j = n
        }
        var part = s.substring(i3, j)
        if (part.length > max * 5) part = part.substring(0, max * 2)
        part = cleanHtml(part)
        if (!(part.length < 200 && htmlText(part).isBlank())) {
            out += (carry ?: "") + part
            val lo = part.lowercase().takeIf { it.length == part.length } ?: part
            val k = lo.lastIndexOf("<div", i2) // sic: Moon+ uses the chapter offset as an index into the part
            if (k != -1) carry = if (lo.lastIndexOf("</div") < k) part.substring(k, part.indexOf('>', k) + 1) else null
        }
        if (j >= n) break
        i2 = max + j
        i3 = j
    }
    if (n < 2_000_000) for (i in 0 until out.size - 1) out[i] += "<br/><span align=\"right\"><font color=#6060EE>[${i + 1}/${out.size}]</font></span>"
    return out
}

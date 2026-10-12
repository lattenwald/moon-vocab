package org.lattenwald.moonvocab.data.books

import java.io.StringReader
import org.ccil.cowan.tagsoup.HTMLSchema
import org.ccil.cowan.tagsoup.Parser
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

// Port of Moon+ Reader Pro 10.7's HtmlToSpannedConverter text output, with the user's trimBlankSpace off and indentParagraph on.
class MoonPage(
    val css: MoonCss? = null,
    val bodyClass: String? = null,
    val imageSize: (String) -> Pair<Double, Double>? = { null },
    val bigImage: (Double, Double) -> Boolean = { w, _ -> minOf(w * DENSITY, PAGE_WIDTH) > PAGE_WIDTH / 3 },
    val pageLabels: Boolean = false,
) {
    internal companion object {
        // The phone's page width and density are unknown; these fit every image in the user's marked EPUBs.
        const val PAGE_WIDTH = 1000.0
        const val DENSITY = 2.75
    }
}

private const val OBJ = '￼'

fun moonText(html: String, page: MoonPage): String {
    val conv = Converter(page)
    val parser = Parser()
    parser.setProperty(Parser.schemaProperty, HTMLSchema())
    parser.contentHandler = conv
    parser.parse(InputSource(StringReader(moonCleanHtml(html))))
    conv.finish()
    return conv.b.trimEnd(' ', ' ', '　', '\n').toString()
}

internal class CssStyle(from: CssStyle? = null) {
    var cssText: String? = null
    val margin = arrayOfNulls<Double>(4)
    val padding = arrayOfNulls<Double>(4)
    var border = false
    var bg = false
    var bgImg = false
    var display: String? = null
    var noindent = false

    init {
        if (from != null) combine(from)
    }

    fun combine(o: CssStyle) {
        o.cssText?.let { cssText = if (cssText == null) it else cssText + "\n\n" + it }
        for (j in 0..3) {
            o.margin[j]?.let { margin[j] = it }
            o.padding[j]?.let { padding[j] = it }
        }
        border = border || o.border
        bg = bg || o.bg
        bgImg = bgImg || o.bgImg
        o.display?.let { display = it }
        if (o.noindent) noindent = true
    }

    // CSS.scanPropertyForStyle, the properties that change the text.
    fun scan(css: String) {
        var s = css
        while (true) {
            val i = s.indexOf("//")
            if (i == -1) break
            val j = s.indexOf('\n', i)
            s = if (j != -1) s.substring(0, i) + s.substring(j) else s.substring(0, i)
        }
        cssText = if (cssText == null) s else cssText + "\n\n" + s
        s = s.replace("\r", "")
        propIndex(s, "display").let { if (it != -1) display = propValue(s, it + 8, true) }
        propIndex(s, "visibility").let { if (it != -1 && propValue(s, it + 11, true) == "hidden") display = "none" }
        for ((k, arr) in listOf("margin" to margin, "padding" to padding)) {
            val i = propIndex(s, k)
            if (i != -1) {
                val p = propValue(s, i + k.length + 1, false).split(' ', '\t', '\n').filter { it.isNotEmpty() }
                val values = when (p.size) {
                    4 -> p
                    3 -> listOf(p[0], p[1], p[2], p[1])
                    2 -> listOf(p[0], p[1], p[0], p[1])
                    1 -> List(4) { p[0] }
                    else -> null
                }
                values?.forEachIndexed { j, v -> arr[j] = emSize(v) }
            }
            for ((j, side) in listOf("top", "right", "bottom", "left").withIndex()) {
                val i2 = propIndex(s, "$k-$side")
                if (i2 != -1 && i < i2) arr[j] = emSize(propValue(s, i2 + k.length + side.length + 2, true))
            }
        }
        for (m in BORDER.findAll(s)) {
            val v = m.groupValues[1]
            if (BORDER_STYLE.containsMatchIn(v) && !ZERO.containsMatchIn(v)) border = true
        }
        propIndex(s, "background-color").let {
            if (it != -1) {
                val v = propValue(s, it + 17, true)
                if (v.isNotEmpty() && v !in setOf("transparent", "inherit", "none", "initial")) bg = true
            }
        }
        propIndex(s, "background-image").let { if (it != -1 && "none" !in propValue(s, it + 17, true)) bgImg = true }
    }

    // CSS.hasParagraphProperty; text-indent doesn't count while indentParagraph is on.
    val paragraph: Boolean get() = bgImg || border || bg || margin.any { it != null } || padding.any { it != null } || noindent

    val whiteSpaceWrap: Boolean get() = cssText?.let { "white-space" in it && ("pre-wrap" in it || "pre-line" in it) } == true

    private companion object {
        val BORDER = Regex("""(?:^|[\s;{])border(?:-(?:top|bottom|left|right))?(?:-style)?\s*:\s*([^;}]*)""")
        val BORDER_STYLE = Regex("""\b(solid|dotted|dashed|double|groove|ridge|inset|outset)\b""")
        val ZERO = Regex("""(?:^|\s)0(?:px|em)?(?:\s|$)""")
    }
}

private fun isSplit(c: Char, space: Boolean = true) = (space && c == ' ') || c in ";:\n\r=\",\t{}"

private fun priorNotSplit(s: String, from: Int): Int {
    var i = from
    while (true) {
        i--
        if (i <= 0 || !isSplit(s[i])) return i
    }
}

private fun nextNotSplit(s: String, from: Int): Int {
    var i = from
    while (i < s.length - 1 && isSplit(s[i])) i++
    return i
}

private fun nextSplit(s: String, from: Int, space: Boolean): Int {
    var i = from
    while (true) {
        i++
        if (i >= s.length) return i
        if (s[i] == '(') {
            val j = s.indexOf(')', i)
            if (j != -1) i = j + 1
            if (i >= s.length) return i
        }
        if (isSplit(s[i], space)) return i
    }
}

private fun propIndex(s: String, name: String): Int {
    var i = s.indexOf(name)
    while (i != -1 && s.length >= name.length + i) {
        val before = i == 0 || isSplit(s[i - 1])
        val after = s.length == name.length + i || isSplit(s[i + name.length])
        if (before && after) return i
        i = s.indexOf(name, i + name.length)
    }
    return -1
}

private fun propValue(s: String, i: Int, space: Boolean): String {
    val a = nextNotSplit(s, i)
    val b = nextSplit(s, a, space)
    return if (b <= s.length) s.substring(a, b) else ""
}

private val NUMBER = Regex("""^[-+]?\d*\.?\d+""")

private fun emSize(value: String?): Double {
    val v = value?.trim().orEmpty()
    if (v.isEmpty()) return 0.0
    val f = NUMBER.find(v)?.value?.toDoubleOrNull() ?: 0.0
    return when {
        '%' in v -> f * MoonPage.PAGE_WIDTH / 105.0 / 16.0
        "em" in v -> f
        "px" in v -> f / 16
        "pt" in v -> f / 12
        "in" in v -> f * 6
        "cm" in v -> f * 2
        else -> f / 16
    }
}

/** A book's stylesheet as Moon+'s CSS.scanTextForStyles reads it: lowercased, innermost blocks, no pseudo-classes. */
class MoonCss {
    internal val styles = LinkedHashMap<String, CssStyle>()

    fun scan(text: String) {
        val s = deleteCssComments(text).replace(" !important", "").replace("!important", "")
        var i = 0
        while (true) {
            var a = s.indexOf('{', i)
            if (a == -1) return
            val b = s.indexOf('}', a)
            if (b == -1) return
            while (true) {
                val a2 = s.indexOf('{', a + 1)
                if (a2 == -1 || a2 > b) break
                a = a2
            }
            val names = classNames(s, a)
            if (names.isNotEmpty()) {
                val st = CssStyle().apply { scan(s.substring(a, b + 1)) }
                st.cssText = names.toString() + "\n" + st.cssText
                for (n in names) styles.getOrPut(n) { CssStyle() }.combine(st)
            }
            i = b
        }
    }

    private val cache = HashMap<List<Any?>, CssStyle?>()

    // An approximation of MyHtml.getClassStyle's selector matching.
    internal fun classStyle(tag: String, id: String?, cls: String?, tags: List<String>, classes: List<String?>, bodyClass: String?): CssStyle? {
        if (styles.isEmpty()) return null
        val key = listOf(tag, id, cls, tags.toList(), classes.toList(), bodyClass)
        return cache.getOrPut(key) { match(tag, id, cls, tags, classes, bodyClass) }?.let { CssStyle(it) }
    }

    private fun match(tag: String, id: String?, cls: String?, tags: List<String>, classes: List<String?>, bodyClass: String?): CssStyle? {
        val clist = cls?.split(' ', '\t', '\n')?.filter { it.isNotEmpty() }.orEmpty()
        val found = mutableListOf<String>()
        val hasAncestor = tags.size > 1 || (tags.size == 1 && tags[0] != tag)
        var ancestors = tags.zip(classes)
        if (ancestors.isNotEmpty() && ancestors.last().first == tag) ancestors = ancestors.dropLast(1)
        for (key in styles.keys) {
            val parts = key.split(' ').filter { it.isNotEmpty() && it != ">" && it != "+" && it != "~" }
            if (parts.isEmpty() || !compoundMatch(parts.last(), tag, clist)) continue
            val last = parts.last()
            if (parts.size == 1) {
                if ((last == tag || last.startsWith(".") || last.startsWith("$tag.")) && last != "*") found += key
                continue
            }
            var pre = parts.dropLast(1)
            while (pre.isNotEmpty() && (pre[0] == "html" || pre[0] == "body" || (bodyClass != null && pre[0] == ".$bodyClass"))) pre = pre.drop(1)
            if (pre.isEmpty()) {
                found += key
                continue
            }
            if (!hasAncestor) continue
            var j = 0
            for ((t2, c2) in ancestors) {
                if (j < pre.size && compoundMatch(pre[j], t2, c2?.split(' ')?.filter { it.isNotEmpty() }.orEmpty())) j++
            }
            if (j == pre.size) found += key
        }
        if (tag in styles && tag !in found) found.add(0, tag)
        val ordered = found.sortedWith(compareBy<String>({ it != tag }, { k -> k.count { it == ' ' } }, { k -> k.count { it == '.' } }))
        var res: CssStyle? = null
        for (k in ordered) res = res?.apply { combine(styles.getValue(k)) } ?: CssStyle(styles.getValue(k))
        if (res != null) styles["*"]?.let { star -> res = CssStyle(star).apply { combine(res!!) } }
        if (id != null) styles["#$id"]?.let { byId -> res = res?.apply { combine(byId) } ?: CssStyle(byId) }
        return res
    }

    private companion object {
        val COMPOUND = Regex("""^([a-z0-9_*-]*)((?:\.[^.#\s]+)*)(#[^.\s]+)?$""")

        fun compoundMatch(comp: String, tag: String, classes: List<String>): Boolean {
            val m = COMPOUND.matchEntire(comp) ?: return false
            val t = m.groupValues[1]
            if (t.isNotEmpty() && t != "*" && t != tag) return false
            return m.groupValues[2].split('.').filter { it.isNotEmpty() }.all { it in classes }
        }

        fun deleteCssComments(text: String): String {
            val s = text.replace("\r", "").lowercase()
            val out = StringBuilder()
            var i = 0
            while (true) {
                val j = s.indexOf("/*", i)
                if (j == -1) {
                    out.append(s, i, s.length)
                    break
                }
                out.append(s, i, j)
                val k = s.indexOf("*/", j)
                if (k == -1) break
                i = k + 2
                if (i >= s.length) break
            }
            return out.toString()
        }

        fun trimClassName(name: String): String {
            val a = nextNotSplit(name, 0)
            val b = priorNotSplit(name, name.length) + 1
            var s = if (a < b) name.substring(a, b) else ""
            val i = s.indexOf('[')
            if (i != -1) {
                val j = s.indexOf(']', i)
                if (j != -1) {
                    if ("rtl" in s.substring(i, j)) return ""
                    if (s.startsWith("html") || s.startsWith("body")) s = s.substring(0, i) + s.substring(j + 1)
                }
            }
            val g = s.indexOf('>')
            if (g > 0) {
                if (g < s.length - 1 && s[g + 1] != ' ') s = s.substring(0, g + 1) + " " + s.substring(g + 1)
                if (s[g - 1] != ' ') s = s.substring(0, g) + " " + s.substring(g)
            }
            return s.replace('\n', ' ').javaTrim()
        }

        fun classNames(s: String, i: Int): List<String> {
            val out = mutableListOf<String>()
            var p = priorNotSplit(s, i)
            var e = p
            while (p > 0) {
                val c = s[p - 1]
                if (c == ',') {
                    out += trimClassName(s.substring(p, minOf(e + 1, s.length)))
                    e = p - 2
                }
                if (c in "};/") {
                    p--
                    break
                }
                p--
            }
            if (p == 0 && s.isNotEmpty() && s[0] == Char(0xFEFF)) p++
            out += trimClassName(s.substring(p, maxOf(p, minOf(e + 1, s.length))))
            return out.filter { it.isNotEmpty() && !it.startsWith("@") && !(':' in it && !it.startsWith("a:")) }
        }
    }
}

// A.cleanHtml with trimBlankSpace off.
internal fun moonCleanHtml(html: String): String {
    val s = html.replace("­", "")
    if (s.length > 100_000) return s
    var t = s.javaTrim()
    while (true) {
        t = when {
            t.endsWith("<br>") -> t.dropLast(4)
            t.endsWith("　") || t.endsWith(" ") -> t.dropLast(1)
            else -> return t
        }
    }
}

private val CSS_USED = setOf("blockquote", "p", "div", "fieldset", "code", "h1", "h2", "h3", "h4", "h5", "h6", "span", "section")

private fun isHeader(t: String) = t.length == 2 && t[0] == 'h' && t[1] in '1'..'6'

private class Event(val tag: String, val attrs: Map<String, String>?, val type: Int, var text: String? = null, var rowText: String? = null)

private class Converter(private val page: MoonPage) : DefaultHandler() {
    val b = StringBuilder()
    private var ignoreStart = false
    private var displayNoneTag: String? = null
    private var preTagStart = false
    private var tdStart = 0
    private var li = false
    private val tags = mutableListOf<String>()
    private val classes = mutableListOf<String?>()
    private val styles = mutableListOf<CssStyle?>()
    private val hide = ArrayDeque<Pair<CssStyle, Int>>()
    private val margin = ArrayDeque<Pair<CssStyle, Int>>()
    private var rubyRtStart = false
    private var rubyRtText: StringBuilder? = null
    private var mediaTagStart = 0
    private var tableStart = -1
    private var tableLevel = 0
    private var tableList: MutableList<Event>? = null
    private val open = ArrayDeque<String>()

    private fun last() = if (b.isEmpty()) null else b[b.length - 1]

    private fun br() = b.append('\n')

    private fun brSingle() {
        if (last() != '\n') br()
    }

    private fun brDouble() {
        if (!(b.length > 1 && b[b.length - 1] == '\n' && b[b.length - 2] == '\n')) br()
    }

    private fun handleP(st: CssStyle?, z: Boolean) {
        val para = st?.paragraph == true || z
        if (last() == '\n') {
            if ((b.length >= 2 && b[b.length - 2] == '\n') || para) return
            br()
            return
        }
        if (b.isNotEmpty()) {
            br()
            if (para) return
            br()
        }
    }

    private fun appendText(text: String) {
        var s = text
        if (s.startsWith("\n") && last() == '\n') {
            s = s.substring(1)
            if (s.isEmpty()) return
        }
        if (b.isNotEmpty()) {
            if (s == "\n") br() else b.append(s)
        } else if (s.length > 1) {
            b.append(s)
        }
    }

    private fun Map<String, String>.lowerTrim(name: String) = this[name]?.lowercase()?.javaTrim()?.ifEmpty { null }

    private fun styleFromAttr(tag: String, attrs: Map<String, String>): CssStyle? {
        var st = page.css?.classStyle(tag, attrs.lowerTrim("id"), attrs.lowerTrim("class"), tags, classes, page.bodyClass)
        attrs["style"]?.let { v -> st = (st?.let { CssStyle(it) } ?: CssStyle()).apply { scan(v) } }
        return st
    }

    private fun lastCssStyle(tag: String): CssStyle? {
        for (i in tags.indices.reversed()) if (tags[i] == tag) return styles[i]
        return null
    }

    private fun addTagCache(tag: String, attrs: Map<String, String>) {
        tags += tag
        classes += attrs.lowerTrim("class")
        styles += null
    }

    private fun removeTagCache() {
        if (tags.isNotEmpty()) {
            tags.removeAt(tags.size - 1)
            classes.removeAt(classes.size - 1)
            styles.removeAt(styles.size - 1)
        }
    }

    private fun startCss(tag: String, attrs: Map<String, String>): CssStyle? {
        if (page.pageLabels && (tag == "span" || tag == "div")) {
            val id = attrs["id"]
            val type = attrs["epub:type"]
            if (id != null && id.startsWith("page") && type != null && "pagebreak" in type.lowercase()) pageNumber(id)
        }
        tags += tag
        classes += attrs.lowerTrim("class")
        var st = styleFromAttr(tag, attrs)
        if (isHeader(tag)) st = (st?.let { CssStyle(it) } ?: CssStyle()).apply { noindent = true }
        styles += st
        if (st == null) return null
        if (st.whiteSpaceWrap) preTagStart = true
        if (st.display == "none") {
            hide.addLast(st to b.length)
            return st
        }
        if ((tag != "span" || st.display == "block") && tag != "code" && st.paragraph) margin.addLast(st to b.length)
        return st
    }

    private fun endCss(tag: String) {
        val st = lastCssStyle(tag)
        if (st?.whiteSpaceWrap == true) preTagStart = false
        if (st != null && hide.lastOrNull()?.first === st) b.setLength(hide.removeLast().second)
        if (st != null && margin.lastOrNull()?.first === st) {
            var i = margin.removeLast().second
            while (i < b.length && b[i] == '\n') i++
            if (i == b.length && ((st.margin[0] ?: 0.0) > 0 || (st.margin[2] ?: 0.0) > 0)) {
                brSingle()
                b.append(" \n")
            }
        }
        removeTagCache()
    }

    private fun pageNumber(id: String) {
        val n = Regex("-?\\d+").find(id)?.value?.toIntOrNull() ?: 0
        val label = (if (n <= 0) id else "Page $n") + " "
        if (b.isNotEmpty()) {
            brDouble()
            brDouble()
        }
        b.append(label)
        brSingle()
    }

    private fun startImg(tag: String, attrs: Map<String, String>) {
        val src = if (tag == "img") attrs["src"] else attrs["xlink:href"] ?: attrs["href"] ?: attrs["l:href"]?.removePrefix("#")
        if (src == null) return
        var st = page.css?.classStyle(tag, attrs.lowerTrim("id"), attrs.lowerTrim("class"), tags, classes, page.bodyClass)
        attrs["style"]?.let { v -> st = (st?.let { CssStyle(it) } ?: CssStyle()).apply { scan(v) } }
        if (st?.display == "none") return
        val big = attrs["alt"] != "nobreak" && page.imageSize(src)?.let { (w, h) -> page.bigImage(w, h) } == true
        if (big) brSingle()
        b.append(OBJ)
        if (big) br()
    }

    private fun hidden(tag: String, attrs: Map<String, String>): Boolean {
        val st = styleFromAttr(tag, attrs)
        return st?.display == "none" || attrs["hidden"]?.lowercase() == "hidden"
    }

    private fun start(tag: String, attrs: Map<String, String>) {
        if (ignoreStart || displayNoneTag != null) return
        val st = if (tag in CSS_USED) startCss(tag, attrs) else null
        when {
            tag == "p" -> if (!(li && last() == '\n') && b.length > tdStart) handleP(st, false)
            tag == "div" -> if (st?.display != "inline") handleP(st, true)
            tag == "span" -> if (st?.display == "block") brSingle()
            tag == "hr" -> appendText((if (last() != '\n') "\n" else "") + "───\n")
            tag == "hr2" -> handleP(null, false)
            tag == "aside" -> if (attrs["epub:type"] == "footnote") ignoreStart = true else startCss(tag, attrs)
            tag == "rt" -> if (rubyRtText == null) {
                rubyRtStart = true
                rubyRtText = StringBuilder()
            }
            tag == "rp" || tag == "style" -> ignoreStart = true
            tag == "pre" -> {
                preTagStart = true
                handleP(null, false)
                addTagCache(tag, attrs)
            }
            tag == "figure" || tag == "figcaption" -> {
                brSingle()
                startCss(tag, attrs)
            }
            tag == "dl" -> {
                handleP(null, false)
                startCss(tag, attrs)
            }
            tag == "dt" -> {
                brSingle()
                addTagCache(tag, attrs)
            }
            tag == "dd" -> brSingle()
            tag == "table" -> handleP(null, false)
            tag == "td" || tag == "th" -> tdStart = b.length
            tag == "ul" || tag == "ol" -> if (hidden(tag, attrs)) {
                displayNoneTag = tag
            } else {
                brSingle()
                addTagCache(tag, attrs)
            }
            tag == "li" -> if (hidden(tag, attrs)) {
                displayNoneTag = tag
            } else {
                li = true
                brSingle()
            }
            tag == "blockquote" -> brSingle()
            isHeader(tag) -> handleP(st, false)
            tag == "img" || tag == "image" -> startImg(tag, attrs)
            tag == "audio" || tag == "video" -> if (attrs["src"].isNullOrEmpty()) {
                mediaTagStart = -1
            } else {
                mediaTagStart = 1
                b.append(OBJ)
            }
            tag == "nav" -> if (hidden(tag, attrs)) {
                displayNoneTag = tag
            } else {
                br()
                addTagCache(tag, attrs)
            }
        }
    }

    private fun end(tag: String) {
        if (ignoreStart) {
            if (tag == "aside" || tag == "rp" || tag == "style") ignoreStart = false
            return
        }
        if (displayNoneTag != null) {
            if (displayNoneTag == tag) displayNoneTag = null
            return
        }
        when {
            tag == "p" -> if (tdStart == 0) handleP(lastCssStyle("p"), false)
            tag == "div" -> lastCssStyle("div").let { if (it?.display != "inline") handleP(it, true) }
            tag == "span" -> if (lastCssStyle("span")?.display == "block") brSingle()
            tag == "br" -> br()
            isHeader(tag) -> handleP(lastCssStyle(tag), false)
            tag == "aside" -> endCss(tag)
            tag == "ruby" -> rubyRtText = null
            tag == "rt" -> rubyRtStart = false
            tag == "pre" -> {
                preTagStart = false
                brSingle()
                handleP(null, false)
                removeTagCache()
            }
            tag == "audio" || tag == "video" -> mediaTagStart = 0
            tag == "figure" || tag == "figcaption" || tag == "dl" -> {
                endCss(tag)
                handleP(null, false)
            }
            tag == "dt" -> {
                brSingle()
                removeTagCache()
            }
            tag == "dd" || tag == "table" || tag == "tr" -> br()
            tag == "td" || tag == "th" -> {
                tdStart = 0
                if (b.isNotEmpty() && last() != '\n') appendText("　 ")
            }
            tag == "ul" -> {
                brSingle()
                removeTagCache()
            }
            tag == "ol" -> brSingle() // Moon+ never pops ol's tag-cache entry
            tag == "li" -> li = false
            tag == "blockquote" -> brSingle()
            tag == "nav" -> {
                br()
                removeTagCache()
            }
        }
        if (tag in CSS_USED) endCss(tag)
    }

    override fun startElement(uri: String?, localName: String, qName: String?, atts: Attributes) {
        val tag = localName.lowercase()
        open.addLast(tag)
        val attrs = buildMap { for (i in 0 until atts.length) putIfAbsent(atts.getQName(i).lowercase(), atts.getValue(i)) }
        start(tag, attrs)
        if (tag == "table") {
            if (tableLevel == 0) {
                tableStart = b.length
                tableList = mutableListOf()
            }
            tableLevel++
        }
        if (tableStart != -1) tableList!!.add(Event(tag, attrs, 0))
    }

    override fun endElement(uri: String?, localName: String, qName: String?) {
        val tag = localName.lowercase()
        open.removeLastOrNull()
        end(tag)
        if (tableStart != -1) {
            tableList!!.add(Event(tag, null, 1))
            if (tag == "table") {
                if (tableLevel == 1) {
                    dealTable()
                    tableStart = -1
                    tableList = null
                }
                tableLevel--
            }
        }
    }

    // dealTableContent: a table's first column is padded to a common width with NBSPs measured in the device font.
    private fun dealTable() {
        val events = tableList ?: return
        val rows = mutableListOf<Event>()
        var ncol = 1
        var inFirstCell = false
        var firstCellNext = false
        var rowStart = false
        for (e in events) {
            when {
                e.type == 2 -> if (inFirstCell) {
                    if (firstCellNext) {
                        e.rowText = e.text
                        rows += e
                        firstCellNext = false
                    } else {
                        e.rowText = rows.last().rowText + e.text
                        rows[rows.size - 1] = e
                    }
                }
                e.tag == "tr" -> {
                    if (e.type == 0) ncol = 0
                    inFirstCell = false
                    rowStart = true
                }
                e.tag == "td" -> {
                    if (e.type == 0) ncol++
                    firstCellNext = rowStart
                    rowStart = false
                    inFirstCell = firstCellNext
                }
            }
        }
        if (!(rows.size > 1 && ncol > 1)) return
        b.setLength(tableStart)
        val widths = rows.map { r -> if ('\n' in r.rowText!!) 0.0 else if (r.text!!.length > 50) PAGE_EM / 2 else textWidth(r.rowText!!) }
        var f = widths.max()
        f = minOf(f, if (ncol < 3) PAGE_EM / 2 else PAGE_EM * 2 / 5)
        if (widths.count { it > f / 4 } > 1) {
            val nbsp = textWidth(" ")
            rows.zip(widths).forEach { (r, w) ->
                if (w > 0 && w < f) {
                    val n = ((f - w) / nbsp + 0.5).toInt()
                    if (n > 0) r.text = r.text + " ".repeat(n)
                }
            }
        }
        tableStart = -1
        for (e in events) when (e.type) {
            0 -> start(e.tag, e.attrs!!)
            1 -> end(e.tag)
            else -> b.append(e.text)
        }
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        if (rubyRtStart) {
            rubyRtText?.append(ch, start, length)
            return
        }
        if (mediaTagStart != 0 || ignoreStart || displayNoneTag != null) return
        val sb = StringBuilder()
        for (k in start until start + length) {
            val c = ch[k]
            if (preTagStart && (c == '\n' || c == ' ')) {
                sb.append(c)
            } else if (c == ' ' || c == '\n' || c == '\t') {
                val prev = if (sb.isNotEmpty()) sb[sb.length - 1] else last() ?: '\n'
                if (prev != ' ' && prev != '\n') sb.append(' ')
            } else if (!(c == '　' && sb.isEmpty())) {
                sb.append(c)
            }
        }
        if (sb.isNotEmpty()) {
            b.append(sb)
            if (tableStart != -1) tableList!!.add(Event("", null, 2, sb.toString()))
        }
    }

    fun finish() {
        while (open.isNotEmpty()) end(open.removeLast())
    }

    private companion object {
        // Page width in em of the body font, fitted to the one padded text column in the user's books.
        const val PAGE_EM = 21.5

        // A rough proportional font; only the table padding depends on it.
        fun textWidth(s: String): Double = s.sumOf { c ->
            when {
                c in "  .,:;!|il'’" -> 0.25
                c.isDigit() -> 0.5
                c.isUpperCase() -> 0.7
                c == '　' || c.code > 0x2e80 -> 1.0
                else -> 0.5
            }
        }
    }
}

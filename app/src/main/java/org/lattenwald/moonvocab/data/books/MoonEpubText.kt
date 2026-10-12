package org.lattenwald.moonvocab.data.books

import java.net.URLDecoder
import java.nio.ByteBuffer
import java.util.zip.ZipFile

/** An EPUB's chapters as the texts Moon+ Reader counts note positions in: [texts] of a chapter are its split parts. */
class MoonEpubText(private val zip: ZipFile, fileSize: Long) {
    private val epub = MoonEpub(zip, fileSize)
    private val entries: Map<String, String> by lazy { zip.entries().asSequence().associate { it.name.lowercase() to it.name } }
    private val styles = HashMap<String, Pair<MoonCss, String?>>()
    private val images = HashMap<String, Pair<Double, Double>?>()

    val size: Int get() = epub.size

    fun chapters(): MoonChapters {
        val plain = (0 until size).map { texts(it, pageLabels = false) }
        val labelled = (0 until size).map { c -> if (epub.parts(c).any { "pagebreak" in it }) texts(c, pageLabels = true) else plain[c] }
        return MoonChapters(plain, labelled)
    }

    fun texts(chapter: Int, pageLabels: Boolean): List<String> {
        val file = epub.file(chapter)
        val (css, bodyClass) = styles.getOrPut(file) { stylesheet(file) }
        val page = MoonPage(css, bodyClass, imageSize = { src -> imageSize(file, src) }, pageLabels = pageLabels)
        return epub.parts(chapter).map { moonText(it, page) }
    }

    private fun bytes(name: String): ByteArray? {
        val entry = zip.getEntry(name) ?: entries[name.lowercase()]?.let(zip::getEntry) ?: return null
        return zip.getInputStream(entry).use { it.readBytes() }
    }

    private fun text(name: String): String? = bytes(name)?.decodeToString()?.removePrefix(BOM.toString())

    // Epub.getCSS: the file's rel="stylesheet" links with their @imports, then its first <style> block.
    private fun stylesheet(file: String): Pair<MoonCss, String?> {
        val css = MoonCss()
        val raw = text(file) ?: return css to null
        val low = raw.lowercase().takeIf { it.length == raw.length } ?: raw
        var i = 0
        while (true) {
            val j = low.indexOf("rel=\"stylesheet\"", i)
            if (j <= 0) break
            val tag = low.substring(low.lastIndexOf('<', j).coerceAtLeast(0), low.indexOf('>', j).let { if (it == -1) low.length else it })
            val k = tag.indexOf("href=\"")
            if (k > 0) {
                val path = join(file, tag.substring(k + 6, tag.indexOf('"', k + 6).let { if (it == -1) tag.length else it }))
                text(path)?.let { css.scan(withImports(it, path, mutableSetOf(path))) }
            }
            i = j + 1
        }
        val a = low.indexOf("<style")
        if (a > 0) {
            val b = low.indexOf("</style>")
            if (b > a) css.scan(low.substring(low.indexOf('>', a) + 1, b))
        }
        val bodyClass = low.indexOf("<body").takeIf { it != -1 }?.let { a2 ->
            val tag = low.substring(a2, low.indexOf('>', a2).let { if (it == -1) low.length else it })
            val k = tag.indexOf("class=\"")
            if (k > 0) tag.substring(k + 7, tag.indexOf('"', k + 7).let { if (it == -1) tag.length else it }) else null
        }
        return css to bodyClass
    }

    private fun withImports(s: String, path: String, seen: MutableSet<String>): String {
        val out = mutableListOf<String>()
        var i = 0
        while (true) {
            val j = s.indexOf("@import ", i)
            if (j == -1) break
            i = j + 8
            IMPORT.find(s.substring(i))?.let { m ->
                val p = join(path, m.groupValues[1].trim())
                if (seen.add(p)) text(p)?.let { out += withImports(it, p, seen) + "\n" }
            }
        }
        return out.reversed().joinToString("") + s
    }

    private fun imageSize(file: String, src: String): Pair<Double, Double>? =
        images.getOrPut("$file\u0000$src") { bytes(join(file, src.substringBefore('#')))?.let(::imageDimensions) }

    private companion object {
        val BOM = Char(0xFEFF)
        val IMPORT = Regex("""^(?:url\()?\s*["']?([^"');]+)""")

        fun join(from: String, href: String): String {
            val decoded = runCatching { URLDecoder.decode(href.replace("+", "%2B"), "UTF-8") }.getOrDefault(href)
            val segments = ArrayDeque<String>()
            for (s in (from.substringBeforeLast('/', "") + "/" + decoded).split('/')) when (s) {
                "", "." -> Unit
                ".." -> segments.removeLastOrNull()
                else -> segments.addLast(s)
            }
            return segments.joinToString("/")
        }
    }
}

private val SVG_SIZE = Regex("""<svg[^>]*\bwidth="([\d.]+)""")
private val SVG_HEIGHT = Regex("""<svg[^>]*\bheight="([\d.]+)""")
private val SVG_VIEW_BOX = Regex("""viewBox="[\d.\s-]+?\s([\d.]+)\s+([\d.]+)"""")

internal fun imageDimensions(data: ByteArray): Pair<Double, Double>? = runCatching {
    val buf = ByteBuffer.wrap(data)
    fun u16(i: Int) = buf.getShort(i).toInt() and 0xffff
    fun u16le(i: Int) = (data[i].toInt() and 0xff) or ((data[i + 1].toInt() and 0xff) shl 8)
    when {
        data.size >= 24 && data[0] == 0x89.toByte() && data[1] == 'P'.code.toByte() -> buf.getInt(16).toDouble() to buf.getInt(20).toDouble()
        data.size >= 10 && data[0] == 'G'.code.toByte() && data[1] == 'I'.code.toByte() && data[2] == 'F'.code.toByte() ->
            u16le(6).toDouble() to u16le(8).toDouble()
        data.size >= 4 && data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte() -> {
            var i = 2
            var size: Pair<Double, Double>? = null
            while (i < data.size && size == null) {
                while (data[i] != 0xFF.toByte()) i++
                while (data[i] == 0xFF.toByte()) i++
                val m = data[i].toInt() and 0xff
                i++
                if (m == 0xD8 || m == 0x01 || m in 0xD0..0xD7) continue
                val length = u16(i)
                if (m in 0xC0..0xCF && m != 0xC4 && m != 0xC8 && m != 0xCC) size = u16(i + 5).toDouble() to u16(i + 3).toDouble()
                i += length
            }
            size
        }
        else -> {
            val head = data.copyOf(minOf(data.size, 3000)).decodeToString()
            if ("<svg" !in head.take(2000)) {
                null
            } else {
                val w = SVG_SIZE.find(head)?.groupValues?.get(1)?.toDoubleOrNull()
                val h = SVG_HEIGHT.find(head)?.groupValues?.get(1)?.toDoubleOrNull()
                if (w != null && h != null) w to h
                else SVG_VIEW_BOX.find(head)?.let { it.groupValues[1].toDouble() to it.groupValues[2].toDouble() }
            }
        }
    }
}.getOrNull()

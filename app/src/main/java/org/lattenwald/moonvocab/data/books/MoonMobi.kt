package org.lattenwald.moonvocab.data.books

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer

// Moon+ Reader Pro 10.7 reads a MOBI as the EPUB libmobi (libmloader.so) makes of it, with Epub.java: so does this, with MoonEpub.
class MoonMobi private constructor(private val epub: MoonEpub) {
    val size: Int get() = epub.size

    fun parts(chapter: Int): List<String> = epub.parts(chapter)

    fun file(chapter: Int): String = epub.file(chapter)

    companion object {
        /** Null for what this port does not read: DRM, HUFF/CDIC compression, KF8 (AZW3) and hybrid KF7/KF8 files. */
        fun read(file: File): MoonMobi? {
            val files = try {
                virtualEpub(file.readBytes())
            } catch (_: RuntimeException) {
                null
            } ?: return null
            return MoonMobi(MoonEpub(files.keys.toList(), files::get, file.length(), mobi = true))
        }

        internal const val PART = "OEBPS/part00000.html"

        internal fun virtualEpub(d: ByteArray): Map<String, ByteArray>? {
            val book = Kf7.parse(d) ?: return null
            val html = book.html()
            val ncx = book.ncx
            val out = LinkedHashMap<String, ByteArray>()
            out["OEBPS/content.opf"] = ("<package><manifest><item id=\"part00000\" href=\"part00000.html\" media-type=\"application/xhtml+xml\"/>" +
                "</manifest><spine><itemref idref=\"part00000\"/></spine></package>").toByteArray()
            if (ncx.isNotEmpty()) {
                out["OEBPS/toc.ncx"] = buildString {
                    append("<ncx><navMap>")
                    for ((pos, title) in ncx) {
                        append("<navPoint><navLabel><text>").append(xmlEscape(title)).append("</text></navLabel><content src=\"")
                        append("part00000.html#").append(pad(pos, 10)).append("\"/></navPoint>")
                    }
                    append("</navMap></ncx>")
                }.toByteArray()
            }
            out[PART] = html.toByteArray()
            for ((name, bytes) in book.resources) out["OEBPS/$name"] = bytes
            return out
        }

        private fun xmlEscape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }
}

private fun pad(n: Number, width: Int) = n.toString().padStart(width, '0')

private class Kf7(private val records: List<ByteArray>, private val r0: ByteArray) {
    private val buf = ByteBuffer.wrap(r0)
    private val headerLength = buf.getInt(20)
    private val encoding = buf.getInt(28)
    private val text: ByteArray = run {
        val compression = buf.getShort(0).toInt() and 0xffff
        val textLength = buf.getInt(4)
        val count = buf.getShort(8).toInt() and 0xffff
        val flags = if (headerLength >= 0xE4) buf.getShort(0xF2).toInt() and 0xffff else 0
        val out = ByteArrayOutputStream()
        for (i in 1..count) {
            val rec = trailing(records[i], flags)
            out.write(if (compression == 2) palmDoc(rec) else rec)
        }
        out.toByteArray().let { if (it.size > textLength) it.copyOf(textLength) else it }
    }
    private val firstResource = if (headerLength >= 0x60) buf.getInt(0x6C) else -1

    val resources = LinkedHashMap<String, ByteArray>()

    private fun charset() = if (encoding == 1252) Charsets.windows1252 else Charsets.UTF_8

    // The NCX index in libmobi's mobi_write_ncx_level order: depth-first through each entry's child range.
    val ncx: List<Pair<Int, String>> by lazy {
        val index = if (headerLength >= 0xE4) buf.getInt(0xF4) else -1
        if (index == -1 || index >= records.size) return@lazy emptyList()
        val entries = Indx(records, index).entries
        val out = mutableListOf<Pair<Int, String>>()
        fun level(depth: Int, from: Int, to: Int) {
            for (i in from..minOf(to, entries.size - 1)) {
                val e = entries[i]
                if ((e[4]?.firstOrNull() ?: 0) != depth) continue
                val pos = e[1]?.firstOrNull() ?: continue
                out += pos to (e[3]?.firstOrNull()?.let { Indx.cncx(records, index, it, charset()) } ?: "")
                val first = e[22]?.firstOrNull()
                val last = e[23]?.firstOrNull()
                if (first != null && last != null) level(depth + 1, first, last)
            }
        }
        level(0, 0, entries.size - 1)
        out
    }

    // libmobi's mobi_reconstruct_links_kf7 (strings in libmloader.so); the recindex → src rewrite follows libmobi from memory.
    fun html(): String {
        val s = String(text, Charsets.ISO_8859_1)
        val targets = (FILEPOS.findAll(s).map { it.groupValues[1].toBigInteger() } + ncx.map { it.first.toBigInteger() })
            .filter { it <= s.length.toBigInteger() }.map { it.toInt() }.toSortedSet()
        val sb = StringBuilder(s.length + targets.size * 20)
        var last = 0
        for (t in targets) {
            sb.append(s, last, t).append("<a id=\"").append(pad(t, 10)).append("\"></a>")
            last = t
        }
        sb.append(s, last, s.length)
        var h = FILEPOS_ATTR.replace(sb) { "href=\"#" + pad(it.groupValues[1].toBigInteger(), 10) + "\"" }
        h = RECINDEX.replace(h) { m ->
            val uid = m.groupValues[1].toInt() - 1
            resource(uid)?.let { "src=\"$it\"" } ?: m.value
        }
        return String(h.toByteArray(Charsets.ISO_8859_1), charset())
    }

    private fun resource(uid: Int): String? {
        val k = firstResource + uid
        if (firstResource < 0 || uid < 0 || k >= records.size) return null
        val b = records[k]
        val ext = when {
            b.size > 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() -> "jpg"
            b.size > 4 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() -> "png"
            b.size > 4 && b[0] == 'G'.code.toByte() && b[1] == 'I'.code.toByte() && b[2] == 'F'.code.toByte() -> "gif"
            b.size > 2 && b[0] == 'B'.code.toByte() && b[1] == 'M'.code.toByte() -> "bmp"
            else -> return null
        }
        return "resource${pad(uid, 5)}.$ext".also { resources[it] = b }
    }

    companion object {
        private val FILEPOS = Regex("filepos=[\"']?(\\d+)")
        private val FILEPOS_ATTR = Regex("filepos=[\"']?0*(\\d+)[\"']?")
        private val RECINDEX = Regex("(?<=\\s)recindex=[\"']?(\\d+)[\"']?")
        private val Charsets.windows1252 get() = charset("windows-1252")

        fun parse(d: ByteArray): Kf7? {
            if (d.size < 78 || String(d, 60, 8, Charsets.ISO_8859_1) != "BOOKMOBI") return null
            val n = ByteBuffer.wrap(d).getShort(76).toInt() and 0xffff
            val offsets = List(n) { ByteBuffer.wrap(d).getInt(78 + 8 * it) } + d.size
            val records = List(n) { d.copyOfRange(offsets[it], offsets[it + 1]) }
            val r0 = records[0]
            val b = ByteBuffer.wrap(r0)
            val compression = b.getShort(0).toInt() and 0xffff
            if (compression != 1 && compression != 2) return null
            if (b.getShort(12).toInt() != 0) return null
            if (String(r0, 16, 4, Charsets.ISO_8859_1) != "MOBI") return null
            if (b.getInt(36) >= 8 || exth(r0).containsKey(121)) return null
            return Kf7(records, r0)
        }

        private fun exth(r0: ByteArray): Map<Int, ByteArray> {
            val b = ByteBuffer.wrap(r0)
            if (b.getInt(0x80) and 0x40 == 0) return emptyMap()
            val e = 16 + b.getInt(20)
            if (e + 12 > r0.size || String(r0, e, 4, Charsets.ISO_8859_1) != "EXTH") return emptyMap()
            val out = HashMap<Int, ByteArray>()
            var q = e + 12
            repeat(b.getInt(e + 8)) {
                val type = b.getInt(q)
                val len = b.getInt(q + 4)
                out[type] = r0.copyOfRange(q + 8, q + len)
                q += len
            }
            return out
        }

        fun trailing(rec: ByteArray, flags: Int): ByteArray {
            var end = rec.size
            for (bit in 1 until 16) {
                if (flags and (1 shl bit) == 0) continue
                var v = 0
                for (k in 0 until 4) {
                    val byte = rec[end - 1 - k].toInt() and 0xff
                    v = v or ((byte and 0x7f) shl (7 * k))
                    if (byte and 0x80 != 0) break
                }
                end -= v
            }
            if (flags and 1 != 0) end -= (rec[end - 1].toInt() and 3) + 1
            return rec.copyOf(end)
        }

        fun palmDoc(data: ByteArray): ByteArray {
            var o = ByteArray(8192)
            var n = 0
            fun put(x: Int) {
                if (n == o.size) o = o.copyOf(o.size * 2)
                o[n++] = x.toByte()
            }
            var i = 0
            while (i < data.size) {
                val c = data[i++].toInt() and 0xff
                when {
                    c == 0 || c in 9..0x7f -> put(c)
                    c in 1..8 -> {
                        repeat(c) { put(data[i + it].toInt()) }
                        i += c
                    }
                    c >= 0xc0 -> {
                        put(' '.code)
                        put(c xor 0x80)
                    }
                    else -> {
                        val x = (c shl 8) or (data[i++].toInt() and 0xff)
                        val dist = (x shr 3) and 0x7ff
                        repeat((x and 7) + 3) { put(o[n - dist].toInt()) }
                    }
                }
            }
            return o.copyOf(n)
        }
    }
}

// A MOBI INDX index: entry → tag → values.
private class Indx(records: List<ByteArray>, first: Int) {
    val entries = mutableListOf<Map<Int, List<Int>>>()

    init {
        val h = records[first]
        val hb = ByteBuffer.wrap(h)
        require(String(h, 0, 4, Charsets.ISO_8859_1) == "INDX")
        val hl = hb.getInt(4)
        val nrec = hb.getInt(24)
        require(String(h, hl, 4, Charsets.ISO_8859_1) == "TAGX")
        val tl = hb.getInt(hl + 4)
        val cb = hb.getInt(hl + 8)
        val tags = List((tl - 12) / 4) { k -> IntArray(4) { h[hl + 12 + 4 * k + it].toInt() and 0xff } }
        for (r in first + 1..first + nrec) {
            val rec = records[r]
            val rb = ByteBuffer.wrap(rec)
            val idxt = rb.getInt(20)
            val cnt = rb.getInt(24)
            val offs = List(cnt) { rb.getShort(idxt + 4 + 2 * it).toInt() and 0xffff } + idxt
            for (i in 0 until cnt) {
                val e = rec.copyOfRange(offs[i], offs[i + 1])
                var pos = 1 + (e[0].toInt() and 0xff)
                var ctrl = pos
                pos += cb
                val pending = mutableListOf<Triple<Int, Int, Pair<Int?, Int?>>>()
                for ((tag, nv, mask, end) in tags.map { listOf(it[0], it[1], it[2], it[3]) }) {
                    if (end != 0) {
                        ctrl++
                        continue
                    }
                    var v = (e[ctrl].toInt() and 0xff) and mask
                    if (v == 0) continue
                    if (v == mask && Integer.bitCount(mask) > 1) {
                        val (n, p) = varlen(e, pos)
                        pos = p
                        pending += Triple(tag, nv, null to n)
                    } else {
                        var m = mask
                        while (m and 1 == 0) {
                            m = m shr 1
                            v = v shr 1
                        }
                        pending += Triple(tag, nv, v to null)
                    }
                }
                val values = HashMap<Int, List<Int>>()
                for ((tag, nv, count) in pending) {
                    val out = mutableListOf<Int>()
                    val (n, bytes) = count
                    if (n != null) {
                        repeat(n * nv) {
                            val (x, p) = varlen(e, pos)
                            pos = p
                            out += x
                        }
                    } else {
                        val stop = pos + bytes!!
                        while (pos < stop) {
                            val (x, p) = varlen(e, pos)
                            pos = p
                            out += x
                        }
                    }
                    values[tag] = out
                }
                entries += values
            }
        }
    }

    companion object {
        fun varlen(b: ByteArray, from: Int): Pair<Int, Int> {
            var v = 0
            var pos = from
            while (true) {
                val c = b[pos++].toInt() and 0xff
                v = (v shl 7) or (c and 0x7f)
                if (c and 0x80 != 0) return v to pos
            }
        }

        // A CNCX offset names its record (offset / 0x10000) among the records after the index's own.
        fun cncx(records: List<ByteArray>, first: Int, offset: Int, charset: java.nio.charset.Charset): String {
            val h = ByteBuffer.wrap(records[first])
            val rec = records.getOrNull(first + h.getInt(24) + 1 + offset / 0x10000) ?: return ""
            val (len, p) = varlen(rec, offset % 0x10000)
            return String(rec, p, minOf(len, rec.size - p), charset)
        }
    }
}

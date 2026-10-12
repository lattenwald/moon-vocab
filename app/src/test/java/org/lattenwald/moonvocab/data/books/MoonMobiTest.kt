package org.lattenwald.moonvocab.data.books

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MoonMobiTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun textBeforeTheFirstNcxEntryIsChapterZeroAndEntriesEndAtTheNext() {
        val front = "<p>" + "Front matter. ".repeat(10) + "</p>"
        val html = "<html><head><guide></guide></head><body>$front<p>One.</p><mbp:pagebreak/><p>Two.</p></body></html>"
        val file = mobi(html, listOf(html.indexOf("<p>One.") to "1", html.indexOf("<p>Two.") to "2"))
        val book = MoonMobi.read(file)!!
        assertEquals(3, book.size)
        val zero = book.parts(0).single()
        assertTrue("Front matter." in zero && "One." !in zero)
        val one = book.parts(1).single()
        assertTrue("One." in one && "Two." !in one && "Front" !in one)
        assertFalse("mbp:pagebreak" in one)
        assertTrue("Two." in book.parts(2).single())
    }

    private fun mobi(html: String, ncx: List<Pair<Int, String>>): File {
        val text = html.toByteArray()
        val r0 = ByteBuffer.allocate(16 + 0xE8).apply {
            putShort(0, 1)
            putInt(4, text.size)
            putShort(8, 1)
            putShort(10, 4096)
            put(16, "MOBI".toByteArray())
            putInt(20, 0xE8)
            putInt(28, 65001)
            putInt(36, 6)
            putInt(0x6C, -1)
            putInt(0xF4, 2)
        }.array()
        val tagx = byteArrayOf(1, 1, 1, 0, 3, 1, 2, 0, 4, 1, 4, 0, 0, 0, 0, 1)
        val indxHeader = ByteBuffer.allocate(192 + 12 + tagx.size).apply {
            put(0, "INDX".toByteArray())
            putInt(4, 192)
            putInt(24, 1)
            putInt(52, 1)
            put(192, "TAGX".toByteArray())
            putInt(196, 12 + tagx.size)
            putInt(200, 1)
            put(204, tagx)
        }.array()
        val cncx = ByteArrayOutputStream()
        val entries = ncx.mapIndexed { i, (pos, title) ->
            val label = "%04d".format(i).toByteArray()
            val labelOffset = cncx.size()
            cncx.write(varlen(title.toByteArray().size))
            cncx.write(title.toByteArray())
            byteArrayOf(label.size.toByte()) + label + byteArrayOf(7) + varlen(pos) + varlen(labelOffset) + varlen(0)
        }
        val body = ByteArrayOutputStream().apply { write(ByteArray(192)) }
        val offsets = entries.map { e -> body.size().also { body.write(e) } }
        val idxt = body.size()
        body.write("IDXT".toByteArray())
        offsets.forEach { body.write(byteArrayOf((it shr 8).toByte(), it.toByte())) }
        val indxData = body.toByteArray().also { ByteBuffer.wrap(it).apply { put(0, "INDX".toByteArray()); putInt(20, idxt); putInt(24, entries.size) } }
        val records = listOf(r0, text, indxHeader, indxData, cncx.toByteArray())
        val pdb = ByteBuffer.allocate(78 + 8 * records.size + 2).apply {
            put(60, "BOOKMOBI".toByteArray())
            putShort(76, records.size.toShort())
            var offset = 78 + 8 * records.size + 2
            records.forEachIndexed { i, r ->
                putInt(78 + 8 * i, offset)
                offset += r.size
            }
        }.array()
        return File(tmp.root, "book.mobi").apply { writeBytes(records.fold(pdb) { a, r -> a + r }) }
    }

    private fun varlen(v: Int): ByteArray {
        val out = mutableListOf((v and 0x7f or 0x80).toByte())
        var x = v shr 7
        while (x > 0) {
            out.add(0, (x and 0x7f).toByte())
            x = x shr 7
        }
        return out.toByteArray()
    }
}

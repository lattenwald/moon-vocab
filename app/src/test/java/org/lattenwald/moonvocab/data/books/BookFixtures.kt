package org.lattenwald.moonvocab.data.books

import java.io.File
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object BookFixtures {
    fun zip(dir: File, name: String, entries: Map<String, ByteArray>): File {
        val file = File(dir, name)
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((path, bytes) in entries) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    fun container(opfPath: String) = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles><rootfile full-path="$opfPath" media-type="application/oebps-package+xml"/></rootfiles>
</container>"""

    // Manifest in reverse: only the spine gives the reading order.
    fun opf(lang: String, title: String, creator: String, hrefs: List<String>, ns: String = "") = """<?xml version="1.0" encoding="UTF-8"?>
<${ns}package xmlns${if (ns.isEmpty()) "" else ":" + ns.dropLast(1)}="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
  <${ns}metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="id">x</dc:identifier>
    <dc:title>$title</dc:title>
    <dc:creator>$creator</dc:creator>
    <dc:language>$lang</dc:language>
  </${ns}metadata>
  <${ns}manifest>
${hrefs.indices.reversed().joinToString("\n") { """    <${ns}item id="c$it" href="${hrefs[it]}" media-type="application/xhtml+xml"/>""" }}
  </${ns}manifest>
  <${ns}spine>
${hrefs.indices.joinToString("\n") { """    <${ns}itemref idref="c$it"/>""" }}
  </${ns}spine>
</${ns}package>"""

    fun xhtml(body: String) = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>t</title></head><body>
$body
</body></html>"""

    fun epub(
        dir: File,
        lang: String = "eng",
        chapters: List<String> = listOf("<p>One.</p>", "<p>Two.</p>"),
        name: String = "book.epub",
    ): File {
        val hrefs = chapters.indices.map { "c$it.xhtml" }
        return zip(dir, name, buildMap {
            put("mimetype", "application/epub+zip".toByteArray())
            put("META-INF/container.xml", container("OEBPS/content.opf").toByteArray())
            put("OEBPS/content.opf", opf(lang, "Title", "Author", hrefs).toByteArray())
            chapters.forEachIndexed { i, body -> put("OEBPS/${hrefs[i]}", xhtml(body).toByteArray()) }
        })
    }

    fun fb2(
        dir: File,
        lang: String = "de",
        sections: List<List<String>> = listOf(listOf("Satz eins.")),
        notes: List<String> = emptyList(),
        title: String = "Titel",
        encoding: String = "UTF-8",
        name: String = "book.fb2",
        mainBodyName: String? = null,
        afterBodies: String = "",
    ): File {
        fun section(paragraphs: List<String>) =
            "<section>${paragraphs.joinToString("") { if (it.startsWith("<")) it else "<p>$it</p>" }}</section>"
        val notesBody = if (notes.isEmpty()) "" else """<body name="notes">${notes.joinToString("") { section(listOf(it)) }}</body>"""
        val xml = """<?xml version="1.0" encoding="$encoding"?>
<FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0" xmlns:l="http://www.w3.org/1999/xlink">
  <description>
    <title-info>
      <author><first-name>Vorname</first-name><last-name>Nachname</last-name></author>
      <book-title>$title</book-title>
      <lang>$lang</lang>
    </title-info>
  </description>
  <body${mainBodyName?.let { " name=\"$it\"" }.orEmpty()}>${sections.joinToString("") { section(it) }}</body>
  $notesBody
  $afterBodies
</FictionBook>"""
        return File(dir, name).apply { writeBytes(xml.toByteArray(Charset.forName(encoding))) }
    }
}

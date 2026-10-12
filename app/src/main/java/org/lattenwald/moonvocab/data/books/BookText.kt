package org.lattenwald.moonvocab.data.books

import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.zip.ZipFile

data class BookText(
    val title: String?,
    val author: String?,
    val language: String?,
    val chapters: List<String>,
    val chaptersMatchMoonReader: Boolean = false,
    val moon: MoonChapters? = null,
)

class MoonChapters(private val plain: List<List<String>>, private val labelled: List<List<String>> = plain) {
    fun part(chapter: Int, split: Int, markedAt: Long): String? =
        (if (markedAt >= PAGE_LABELS_SINCE) labelled else plain).getOrNull(chapter)?.getOrNull(split)

    val parts: List<List<String>> get() = plain

    private companion object {
        // Marks up to 2023-04 have no page labels, one from 2025-05 has them; the version in between is unknown.
        const val PAGE_LABELS_SINCE = 1_700_000_000_000L
    }
}

fun readBookText(file: File, fileName: String): BookText? {
    val name = fileName.lowercase()
    return when {
        name.endsWith(".epub") -> EpubText.read(file)
        name.endsWith(".fb2") && !isZip(file) -> file.inputStream().use(Fb2Text::read).copy(moon = fb2Moon(file, fileName))
        name.endsWith(".fb2") || name.endsWith(".fb2.zip") -> ZipFile(file).use { zip ->
            val entry = zip.entries().asSequence().firstOrNull { !it.isDirectory && it.name.lowercase().endsWith(".fb2") }
                ?: throw IOException("no .fb2 entry in $fileName")
            zip.getInputStream(entry).use(Fb2Text::read)
        }.copy(moon = fb2Moon(file, fileName))
        MOBI.any { name.endsWith(it) } -> MoonMobi.read(file)?.let { mobi ->
            val parts = (0 until mobi.size).map { c -> mobi.parts(c).map { moonText(it, MoonPage()) } }
            BookText(title = null, author = null, language = null, chapters = parts.map { it.joinToString("\n") }, moon = MoonChapters(parts))
        }
        else -> null
    }
}

private val MOBI = listOf(".mobi", ".azw", ".prc")

// The paragraph-shaping rules of Moon+ Reader's built-in FB2 stylesheet; margins decide one or two line breaks.
private val FB2_CSS = MoonCss().apply {
    scan(
        ".epigraph {margin-left: 25%;} .text-author {margin-left: 4em;} .poem {margin-left: 4em;} .cite {margin-left: 4em;} " +
            "p {margin-top: 1em; margin-bottom: 1em;} .footnote {margin: 0.5em; padding: 0.5em; border: 1px dotted #888888;}",
    )
}

private fun fb2Moon(file: File, fileName: String): MoonChapters? = runCatching {
    MoonFb2.read(file, fileName)?.let { fb2 ->
        // Fitted to the user's FB2s: a 21×13 ornament stays in the line, 43×25 formulas stand alone.
        val page = MoonPage(FB2_CSS, imageSize = fb2::imageSize, bigImage = { w, h -> maxOf(w, h) >= 30 })
        MoonChapters((0 until fb2.size).map { c -> fb2.parts(c).map { moonText(it, page) } })
    }
}.getOrNull()

// Some libraries hand out zipped FB2s named plain .fb2.
private fun isZip(file: File): Boolean {
    val magic = ByteArray(4)
    val read = file.inputStream().use { it.read(magic) }
    return read == magic.size && magic.contentEquals(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
}

// ISO 639-2/B codes; getISOLanguages() knows only the T codes.
private val bibliographic = mapOf(
    "ger" to "de", "fre" to "fr", "cze" to "cs", "dut" to "nl", "chi" to "zh", "gre" to "el", "per" to "fa",
    "rum" to "ro", "slo" to "sk", "arm" to "hy", "geo" to "ka", "baq" to "eu", "alb" to "sq", "bur" to "my",
    "ice" to "is", "mac" to "mk", "mao" to "mi", "may" to "ms", "tib" to "bo", "wel" to "cy",
)

// getISO3Language can throw MissingResourceException on Android; a throwing lazy would rethrow on every book.
private val twoLetterByThree: Map<String, String> by lazy {
    bibliographic + Locale.getISOLanguages().mapNotNull { code ->
        runCatching { Locale.forLanguageTag(code).isO3Language }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { it to code }
    }
}

private val iso6391: Set<String> by lazy { Locale.getISOLanguages().toSet() }

fun isoLanguage(tag: String?): String? {
    // Lowercased first: forLanguageTag maps only lowercase "und" to no language.
    val language = Locale.forLanguageTag(tag?.trim()?.lowercase()?.replace('_', '-') ?: return null).language
    val code = if (language.length == 3) twoLetterByThree[language] else language
    // Android's list also carries some three-letter codes (haw, ast).
    return code?.takeIf { it.length == 2 && it in iso6391 }
}

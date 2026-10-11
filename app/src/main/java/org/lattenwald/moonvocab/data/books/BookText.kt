package org.lattenwald.moonvocab.data.books

import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.zip.ZipFile

data class BookText(val title: String?, val author: String?, val language: String?, val chapters: List<String>)

fun readBookText(file: File, fileName: String): BookText? {
    val name = fileName.lowercase()
    return when {
        name.endsWith(".epub") -> EpubText.read(file)
        name.endsWith(".fb2") && !isZip(file) -> file.inputStream().use(Fb2Text::read)
        name.endsWith(".fb2") || name.endsWith(".fb2.zip") -> ZipFile(file).use { zip ->
            val entry = zip.entries().asSequence().firstOrNull { !it.isDirectory && it.name.lowercase().endsWith(".fb2") }
                ?: throw IOException("no .fb2 entry in $fileName")
            zip.getInputStream(entry).use(Fb2Text::read)
        }
        else -> null
    }
}

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

fun isoLanguage(tag: String?): String? {
    // Lowercased first: forLanguageTag maps only lowercase "und" to no language.
    val language = Locale.forLanguageTag(tag?.trim()?.lowercase()?.replace('_', '-') ?: return null).language
    return when {
        language.isEmpty() -> null
        language.length == 3 -> twoLetterByThree[language] ?: language
        else -> language
    }
}

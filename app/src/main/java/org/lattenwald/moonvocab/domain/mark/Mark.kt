package org.lattenwald.moonvocab.domain.mark

import java.security.MessageDigest

enum class MarkStyle { HIGHLIGHT, UNDERLINE, STRIKETHROUGH, WAVY }

data class StyleCombo(val style: MarkStyle, val color: Int) { // color: signed ARGB as Moon+ stores it
    fun encode(): String = "${style.name}:$color"
}

fun parseStyleCombo(s: String): StyleCombo? {
    val style = MarkStyle.entries.firstOrNull { it.name == s.substringBefore(':') } ?: return null
    val color = s.substringAfter(':').toIntOrNull() ?: return null
    return StyleCombo(style, color)
}

data class RawMark(
    val key: String,
    val bookPath: String,
    val bookTitle: String,
    val chapter: Int,
    val position: Int,
    val text: String,
    val combo: StyleCombo,
    val markedAt: Long,
    val split: Int = 0, // Moon+ lastSplitIndex: which ~100 KB part of the chapter's HTML the position counts in
)

fun fileKeyOf(bookPath: String): String = bookPath.substringAfterLast('/').lowercase()

val RawMark.fileKey: String get() = fileKeyOf(bookPath)

data class KeyInput(val bookPath: String, val time: Long, val text: String)

fun styleOf(bak: String?, underline: Int, strikethrough: Int): MarkStyle = when {
    bak == "1" -> MarkStyle.WAVY
    underline == 1 -> MarkStyle.UNDERLINE
    strikethrough == 1 -> MarkStyle.STRIKETHROUGH
    else -> MarkStyle.HIGHLIGHT
}

fun markKeys(rows: List<KeyInput>): List<String> {
    val bases = rows.map { fileKeyOf(it.bookPath) + "\u0000" + it.time }
    val counts = bases.groupingBy { it }.eachCount()
    return rows.mapIndexed { i, row ->
        val base = bases[i]
        sha256Hex(if (counts.getValue(base) > 1) base + "\u0000" + row.text else base)
    }
}

private fun sha256Hex(s: String): String =
    MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

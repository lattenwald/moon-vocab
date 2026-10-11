package org.lattenwald.moonvocab.data.anki

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeVisitor
import org.lattenwald.moonvocab.data.db.CardEntity
import org.lattenwald.moonvocab.domain.card.escapeHtml

// AnkiDroid fields are HTML.
fun CardEntity.ankiFields(bookLine: String): Map<String, String> = mapOf(
    "Word" to escapeHtml(word),
    "Grammar" to escapeHtml(grammar),
    "Excerpt" to excerptHtml,
    "Translation" to escapeHtml(translation),
    "Meaning" to escapeHtml(meaning),
    "Sentence" to sentenceHtml,
    "Book" to escapeHtml(bookLine),
    "Source" to escapeHtml(sourceNote),
    "AppId" to uuid,
)

fun ankiToPlain(html: String): String {
    val out = StringBuilder()
    Jsoup.parseBodyFragment(html).body().traverse(object : NodeVisitor {
        override fun head(node: Node, depth: Int) {
            when {
                node is TextNode -> out.append(node.wholeText)
                node is Element && node.nameIs("div") -> out.append('\n')
                node is Element && node.nameIs("br") && !isBlankLineDiv(node) -> out.append('\n')
            }
        }
    })
    return out.toString().replace(Char(0xA0), ' ').trim()
}

// AnkiDroid's editor writes a blank line as <div><br></div>.
private fun isBlankLineDiv(br: Element): Boolean {
    val parent = br.parent() ?: return false
    return parent.nameIs("div") && parent.childNodeSize() == 1
}

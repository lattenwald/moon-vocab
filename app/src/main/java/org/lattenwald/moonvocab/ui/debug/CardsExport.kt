package org.lattenwald.moonvocab.ui.debug

import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.lattenwald.moonvocab.data.db.BookEntity
import org.lattenwald.moonvocab.data.db.CardEntity
import org.lattenwald.moonvocab.data.db.MarkEntity

private val exportJson = Json { prettyPrint = true }

fun cardsExportName(today: LocalDate): String = "moon-vocab-cards-$today.json"

fun cardsExportJson(cards: List<CardEntity>, marks: List<MarkEntity>, books: List<BookEntity>): String {
    val titles = books.associate { it.id to it.title }
    val marksByCard = marks.filter { it.cardId != null }.groupBy { it.cardId }
    val array = buildJsonArray {
        for (card in cards) addJsonObject {
            put("id", card.id)
            put("status", card.status.name)
            put("book", titles[card.bookId])
            put("sourceLanguage", card.sourceLanguage)
            put("word", card.word)
            put("transcription", card.transcription)
            put("lemma", card.lemma)
            put("grammar", card.grammar)
            put("translation", card.translation)
            put("meaning", card.meaning)
            put("excerptHtml", card.excerptHtml)
            put("sentenceHtml", card.sentenceHtml)
            put("elsewhereDeck", card.elsewhereDeck)
            put("error", card.error)
            putJsonArray("marks") {
                for (mark in marksByCard[card.id].orEmpty().sortedBy { it.markedAt }) addJsonObject {
                    put("text", mark.text)
                    put("chapter", mark.chapter)
                    put("position", mark.position)
                    put("markedAt", mark.markedAt)
                }
            }
        }
    }
    return exportJson.encodeToString(JsonElement.serializer(), array)
}

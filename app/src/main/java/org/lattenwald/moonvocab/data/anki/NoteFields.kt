package org.lattenwald.moonvocab.data.anki

import org.lattenwald.moonvocab.data.db.CardEntity
import org.lattenwald.moonvocab.domain.card.CardField
import org.lattenwald.moonvocab.domain.card.CardFields
import org.lattenwald.moonvocab.domain.card.cardFields
import org.lattenwald.moonvocab.domain.card.escapeHtml
import org.lattenwald.moonvocab.domain.card.with
import org.lattenwald.moonvocab.domain.card.withFields

// AnkiDroid fields are HTML.
enum class NoteField(val noteName: String, val html: Boolean = false, val optional: Boolean = false) {
    WORD("Word"),
    TRANSCRIPTION("Transcription", optional = true),
    GRAMMAR("Grammar"),
    EXCERPT("Excerpt", html = true),
    TRANSLATION("Translation"),
    MEANING("Meaning"),
    SENTENCE("Sentence", html = true),
    BOOK("Book"),
    SOURCE("Source"),
    APP_ID("AppId"),
    ;

    fun encode(value: String): String = if (html) value else escapeHtml(value)

    fun decode(value: String): String = if (html) value else ankiToPlain(value)
}

fun CardFields.toNote(only: Collection<CardField>): Map<String, String> =
    only.associate { it.noteField.noteName to it.noteField.encode(this[it]) }

fun noteFields(fields: CardFields, source: String, bookLine: String, appId: String): Map<String, String> =
    fields.toNote(CardField.entries) +
        listOf(NoteField.BOOK to bookLine, NoteField.SOURCE to source, NoteField.APP_ID to appId)
            .associate { (f, value) -> f.noteName to f.encode(value) }

fun CardEntity.toNote(bookLine: String): Map<String, String> = noteFields(cardFields(), sourceNote, bookLine, uuid)

fun CardFields.readFrom(note: Map<String, String>): CardFields = CardField.entries.fold(this) { acc, f ->
    note[f.noteField.noteName]?.let { acc.with(f, f.noteField.decode(it)) } ?: acc
}

fun CardEntity.readFrom(note: Map<String, String>): CardEntity =
    withFields(cardFields().readFrom(note)).copy(sourceNote = note[NoteField.SOURCE.noteName]?.let(NoteField.SOURCE::decode) ?: sourceNote)

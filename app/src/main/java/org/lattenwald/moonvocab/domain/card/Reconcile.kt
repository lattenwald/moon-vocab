package org.lattenwald.moonvocab.domain.card

import org.lattenwald.moonvocab.data.anki.NoteSnapshot
import org.lattenwald.moonvocab.data.anki.NoteTypeSpec
import org.lattenwald.moonvocab.data.anki.ankiToPlain
import org.lattenwald.moonvocab.data.db.CardEntity

sealed interface ReconcileAction {
    data class Refresh(val cardId: Long, val fields: Map<String, String>, val mod: Long) : ReconcileAction
    data class Missing(val cardId: Long) : ReconcileAction
}

data class SentCard(val cardId: Long, val noteId: Long, val fields: Map<String, String>)

fun reconcile(cards: List<SentCard>, snapshots: Map<Long, NoteSnapshot>): List<ReconcileAction> = cards.mapNotNull { card ->
    val snapshot = snapshots[card.noteId] ?: return@mapNotNull ReconcileAction.Missing(card.cardId)
    val theirs = snapshot.fields.filterKeys { it in NoteTypeSpec.STORED_FIELDS }
    val changed = theirs.any { (name, value) -> card.fields[name] != if (name in NoteTypeSpec.HTML_FIELDS) value else ankiToPlain(value) }
    if (changed) ReconcileAction.Refresh(card.cardId, theirs, snapshot.mod) else null
}

fun CardEntity.refreshed(fields: Map<String, String>, mod: Long): CardEntity = copy(
    word = fields["Word"]?.let(::ankiToPlain) ?: word,
    grammar = fields["Grammar"]?.let(::ankiToPlain) ?: grammar,
    excerptHtml = fields["Excerpt"] ?: excerptHtml,
    translation = fields["Translation"]?.let(::ankiToPlain) ?: translation,
    meaning = fields["Meaning"]?.let(::ankiToPlain) ?: meaning,
    sentenceHtml = fields["Sentence"] ?: sentenceHtml,
    sourceNote = fields["Source"]?.let(::ankiToPlain) ?: sourceNote,
    ankiMod = mod,
)

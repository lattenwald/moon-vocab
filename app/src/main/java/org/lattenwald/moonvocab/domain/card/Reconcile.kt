package org.lattenwald.moonvocab.domain.card

import org.lattenwald.moonvocab.data.anki.NoteSnapshot
import org.lattenwald.moonvocab.data.anki.NoteTypeSpec
import org.lattenwald.moonvocab.data.anki.ankiToPlain

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

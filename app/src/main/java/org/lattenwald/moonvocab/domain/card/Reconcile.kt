package org.lattenwald.moonvocab.domain.card

import org.lattenwald.moonvocab.data.anki.NoteSnapshot
import org.lattenwald.moonvocab.data.anki.NoteTypeSpec

sealed interface ReconcileAction {
    data class Refresh(val cardId: Long, val fields: Map<String, String>, val mod: Long) : ReconcileAction
    data class Missing(val cardId: Long) : ReconcileAction
}

data class SentCard(val cardId: Long, val noteId: Long, val fields: Map<String, String>)

fun reconcile(cards: List<SentCard>, snapshots: Map<Long, NoteSnapshot>): List<ReconcileAction> = cards.mapNotNull { card ->
    val snapshot = snapshots[card.noteId] ?: return@mapNotNull ReconcileAction.Missing(card.cardId)
    val ours = snapshot.fields.filterKeys { it in NoteTypeSpec.FIELDS }
    val changed = NoteTypeSpec.FIELDS.any { (it in ours || it in card.fields) && ours[it] != card.fields[it] }
    if (changed) ReconcileAction.Refresh(card.cardId, ours, snapshot.mod) else null
}

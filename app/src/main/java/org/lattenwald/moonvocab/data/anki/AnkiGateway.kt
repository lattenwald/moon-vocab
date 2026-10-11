package org.lattenwald.moonvocab.data.anki

enum class AnkiAvailability { NOT_INSTALLED, NO_PERMISSION, READY }

data class NoteSnapshot(val fields: Map<String, String>, val tags: Set<String>, val mod: Long) // mod: seconds

class NoteTypeChanged(val missing: List<String>) : Exception("Moon+ Vocab note type is missing fields: $missing")

interface AnkiGateway {
    fun availability(): AnkiAvailability

    fun ensureNoteType(knownId: Long?, knownVersion: Int): Pair<Long, Int>

    fun ensureDeck(name: String): Long

    fun add(mid: Long, deckId: Long, fields: Map<String, String>, tags: Set<String>): Long

    /** [mid] is ignored for merging; the note's current type in AnkiDroid is used. */
    fun update(noteId: Long, mid: Long, fields: Map<String, String>, tags: Set<String>)

    fun delete(noteId: Long): Boolean

    fun read(noteIds: Collection<Long>): Map<Long, NoteSnapshot>
}

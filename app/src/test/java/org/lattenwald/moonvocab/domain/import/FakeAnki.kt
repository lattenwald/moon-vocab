package org.lattenwald.moonvocab.domain.import

import org.lattenwald.moonvocab.data.anki.AnkiAvailability
import org.lattenwald.moonvocab.data.anki.AnkiGateway
import org.lattenwald.moonvocab.data.anki.NoteSnapshot
import org.lattenwald.moonvocab.data.anki.NoteTypeSpec

class FakeAnki : AnkiGateway {
    data class Note(val mid: Long, val deck: String, val fields: Map<String, String>, val tags: Set<String>, val mod: Long)

    var availability = AnkiAvailability.READY
    val notes = LinkedHashMap<Long, Note>()
    var adds = 0
    var reads = 0
    var failOnNthAdd: Int? = null
    var failReads = false
    val elsewhere = HashMap<String, String>()
    val searches = ArrayList<List<String>>()
    var failElsewhere = false
    var ensureCalls = 0

    private val decks = LinkedHashMap<Long, String>()
    private var nextId = 1_700_000_000_000L

    override fun availability() = availability

    override fun ensureNoteType(knownId: Long?, knownVersion: Int): Pair<Long, Int> {
        ensureCalls++
        return (knownId ?: MID) to NoteTypeSpec.VERSION
    }

    override fun ensureDeck(name: String): Long =
        decks.entries.firstOrNull { it.value == name }?.key ?: nextId++.also { decks[it] = name }

    override fun add(mid: Long, deckId: Long, fields: Map<String, String>, tags: Set<String>): Long {
        if (++adds == failOnNthAdd) throw IllegalStateException("AnkiDroid refused to add the note")
        val id = nextId++
        notes[id] = Note(mid, decks.getValue(deckId), fields, tags, mod = 1)
        return id
    }

    override fun update(noteId: Long, mid: Long, fields: Map<String, String>, tags: Set<String>) {
        val note = notes.getValue(noteId)
        notes[noteId] = note.copy(fields = note.fields + fields, tags = note.tags + tags, mod = note.mod + 1)
    }

    override fun delete(noteId: Long): Boolean = notes.remove(noteId) != null

    override fun read(noteIds: Collection<Long>): Map<Long, NoteSnapshot> {
        reads++
        if (failReads) throw NullPointerException()
        return noteIds.mapNotNull { id -> notes[id]?.let { id to NoteSnapshot(it.fields, it.tags, it.mod) } }.toMap()
    }

    override fun findElsewhere(terms: List<String>): String? {
        searches += terms
        if (failElsewhere) throw NullPointerException()
        return terms.firstNotNullOfOrNull { elsewhere[it.lowercase()] }
    }

    override fun findByAppIds(mid: Long, uuids: Collection<String>): Map<String, Long> =
        notes.entries.mapNotNull { (id, note) -> note.fields["AppId"]?.takeIf { note.mid == mid && it in uuids }?.let { it to id } }.toMap()

    companion object {
        const val MID = 42L
    }
}

package org.lattenwald.moonvocab.data.anki

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import com.ichi2.anki.FlashCardsContract
import com.ichi2.anki.api.AddContentApi
import kotlin.coroutines.cancellation.CancellationException

class AnkiDroidGateway(context: Context) : AnkiGateway {
    private val context = context.applicationContext
    private val api = AddContentApi(this.context)
    private val resolver = this.context.contentResolver

    override fun availability(): AnkiAvailability = when {
        AddContentApi.getAnkiDroidPackageName(context) == null -> AnkiAvailability.NOT_INSTALLED
        context.checkSelfPermission(AddContentApi.READ_WRITE_PERMISSION) != PackageManager.PERMISSION_GRANTED -> AnkiAvailability.NO_PERMISSION
        else -> AnkiAvailability.READY
    }

    override fun ensureNoteType(knownId: Long?, knownVersion: Int): Pair<Long, Int> {
        // AnkiDroid's provider throws on getFieldList for a deleted mid.
        val models = api.modelList ?: throw IllegalStateException("AnkiDroid note types unavailable")
        if (knownId != null && knownId in models) return knownId to upgradeTemplates(knownId, knownVersion)
        val byName = models.filterValues { it == NoteTypeSpec.NAME }.keys.minOrNull()
        if (byName != null) return byName to upgradeTemplates(byName, 0) // its template version is unknown
        val created = api.addNewCustomModel(
            NoteTypeSpec.NAME,
            NoteTypeSpec.FIELDS.toTypedArray(),
            arrayOf(NoteTypeSpec.CARD_NAME),
            arrayOf(NoteTypeSpec.FRONT),
            arrayOf(NoteTypeSpec.BACK),
            NoteTypeSpec.CSS,
            null,
            0,
        ) ?: throw IllegalStateException("AnkiDroid refused to create note type ${NoteTypeSpec.NAME}")
        return created to NoteTypeSpec.VERSION
    }

    override fun ensureDeck(name: String): Long {
        val decks = api.deckList ?: throw IllegalStateException("AnkiDroid refused to create deck $name: deck list unavailable")
        return decks.entries.firstOrNull { it.value == name }?.key
            ?: api.addNewDeck(name)
            ?: throw IllegalStateException("AnkiDroid refused to create deck $name")
    }

    override fun add(mid: Long, deckId: Long, fields: Map<String, String>, tags: Set<String>): Long =
        api.addNote(mid, deckId, mergeFields(fieldList(mid), null, fields), tags)
            ?: throw IllegalStateException("AnkiDroid refused to add the note")

    override fun update(noteId: Long, mid: Long, fields: Map<String, String>, tags: Set<String>) {
        val note = readRaw(listOf(noteId)).firstOrNull() ?: throw IllegalStateException("AnkiDroid has no note $noteId")
        val merged = mergeFields(fieldList(note.mid), note.values, fields) // the user can change a note's type in Anki
        if (!api.updateNoteFields(noteId, merged)) throw IllegalStateException("AnkiDroid did not update note $noteId")
        if (!api.updateNoteTags(noteId, note.tags + tags)) throw IllegalStateException("AnkiDroid did not update tags of note $noteId")
    }

    override fun delete(noteId: Long): Boolean = resolver.delete(noteUri(noteId), null, null) > 0

    override fun read(noteIds: Collection<Long>): Map<Long, NoteSnapshot> {
        val fieldNames = HashMap<Long, List<String>>()
        val out = HashMap<Long, NoteSnapshot>()
        for (note in readRaw(noteIds)) {
            val names = fieldNames.getOrPut(note.mid) { fieldList(note.mid) }
            out[note.id] = NoteSnapshot(
                fields = names.withIndex().associate { (i, name) -> name to note.values.getOrElse(i) { "" } },
                tags = note.tags,
                mod = note.mod,
            )
        }
        return out
    }

    override fun findElsewhere(terms: List<String>): String? {
        if (terms.isEmpty()) return null
        val noteId = resolver.query(FlashCardsContract.Note.CONTENT_URI, null, elsewhereQuery(terms), null, null)?.use { c ->
            if (c.moveToFirst()) c.getLong(c.getColumnIndexOrThrow(FlashCardsContract.Note._ID)) else null
        } ?: return null
        return try {
            deckName(noteId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ""
        }
    }

    private fun deckName(noteId: Long): String {
        val projection = arrayOf(FlashCardsContract.Card.CARD_ORD, FlashCardsContract.Card.DECK_ID)
        val deckId = resolver.query(Uri.withAppendedPath(noteUri(noteId), "cards"), projection, null, null, null)?.use { c ->
            val ord = c.getColumnIndexOrThrow(FlashCardsContract.Card.CARD_ORD)
            val deck = c.getColumnIndexOrThrow(FlashCardsContract.Card.DECK_ID)
            var first: Pair<Int, Long>? = null
            while (c.moveToNext()) {
                if (first == null || c.getInt(ord) < first.first) first = c.getInt(ord) to c.getLong(deck)
            }
            first?.second
        } ?: return ""
        return api.getDeckName(deckId) ?: ""
    }

    override fun findByAppIds(mid: Long, uuids: Collection<String>): Map<String, Long> {
        val wanted = uuids.toSet()
        val fieldNames = HashMap<Long, List<String>>()
        val out = HashMap<String, Long>()
        for (chunk in wanted.chunked(APP_ID_CHUNK)) {
            for (note in queryRaw(appIdQuery(mid, chunk))) {
                val appId = fieldNames.getOrPut(note.mid) { fieldList(note.mid) }.indexOf(APP_ID_FIELD)
                val value = note.values.getOrNull(appId) ?: continue
                if (value in wanted) out[value] = note.id
            }
        }
        return out
    }

    private class RawNote(val id: Long, val mid: Long, val values: List<String>, val tags: Set<String>, val mod: Long)

    private fun readRaw(noteIds: Collection<Long>): List<RawNote> =
        noteIds.distinct().chunked(READ_CHUNK).flatMap { queryRaw("nid:" + it.joinToString(",")) }

    private fun queryRaw(search: String): List<RawNote> {
        val out = ArrayList<RawNote>()
        // null when nothing matches, never empty
        val cursor = resolver.query(FlashCardsContract.Note.CONTENT_URI, null, search, null, null) ?: return out
        cursor.use { c ->
            val id = c.getColumnIndexOrThrow(FlashCardsContract.Note._ID)
            val mid = c.getColumnIndexOrThrow(FlashCardsContract.Note.MID)
            val flds = c.getColumnIndexOrThrow(FlashCardsContract.Note.FLDS)
            val tags = c.getColumnIndexOrThrow(FlashCardsContract.Note.TAGS)
            val mod = c.getColumnIndexOrThrow(FlashCardsContract.Note.MOD)
            while (c.moveToNext()) {
                out += RawNote(
                    id = c.getLong(id),
                    mid = c.getLong(mid),
                    values = c.getString(flds).split(FIELD_SEPARATOR),
                    tags = c.getString(tags).split(WHITESPACE).filter { it.isNotEmpty() }.toSet(),
                    mod = c.getLong(mod),
                )
            }
        }
        return out
    }

    private fun fieldList(mid: Long): List<String> =
        api.getFieldList(mid)?.toList() ?: throw IllegalStateException("AnkiDroid has no note type $mid")

    private fun upgradeTemplates(mid: Long, knownVersion: Int): Int {
        if (knownVersion >= NoteTypeSpec.VERSION) return knownVersion
        val modelUri = Uri.withAppendedPath(FlashCardsContract.Model.CONTENT_URI, mid.toString())
        val css = ContentValues().apply { put(FlashCardsContract.Model.CSS, NoteTypeSpec.CSS) }
        val template = ContentValues().apply {
            put(FlashCardsContract.CardTemplate.QUESTION_FORMAT, NoteTypeSpec.FRONT)
            put(FlashCardsContract.CardTemplate.ANSWER_FORMAT, NoteTypeSpec.BACK)
        }
        val updated = try {
            resolver.update(modelUri, css, null, null) > 0 &&
                resolver.update(Uri.withAppendedPath(modelUri, "templates/0"), template, null, null) > 0
        } catch (_: IllegalArgumentException) { // provider's error for a malformed note type
            false
        }
        return if (updated) NoteTypeSpec.VERSION else knownVersion
    }

    private fun noteUri(noteId: Long): Uri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, noteId.toString())

    private companion object {
        const val READ_CHUNK = 200
        const val APP_ID_CHUNK = 50
        const val APP_ID_FIELD = "AppId"
        const val FIELD_SEPARATOR = '\u001f'
        val WHITESPACE = Regex("\\s+")
    }
}

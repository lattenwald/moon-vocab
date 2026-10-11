package org.lattenwald.moonvocab.data.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.api.AddContentApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnkiDroidGatewayTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val gateway = AnkiDroidGateway(context)
    private val created = mutableListOf<Long>()

    @Before
    fun grantAnkiPermission() {
        // Granting the permission throws while AnkiDroid, which defines it, is absent.
        assumeTrue(gateway.availability() != AnkiAvailability.NOT_INSTALLED)
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, AddContentApi.READ_WRITE_PERMISSION)
        assertEquals(AnkiAvailability.READY, gateway.availability())
    }

    @After
    fun deleteCreatedNotes() {
        val leftover = created.filterNot { gateway.delete(it) }
        assertTrue("notes not deleted: $leftover", leftover.isEmpty())
    }

    @Test
    fun noteLifecycle() {
        val (mid, version) = gateway.ensureNoteType(null, 0)
        assertEquals(NoteTypeSpec.FIELDS, AddContentApi(context).getFieldList(mid)?.toList())
        assertEquals(NoteTypeSpec.VERSION, version)

        val deckId = gateway.ensureDeck(DECK)
        val word = "moonvocab-${System.currentTimeMillis()}"
        val id = gateway.add(mid, deckId, mapOf("Word" to word, "Translation" to "first"), setOf("moon-vocab"))
        created += id

        val added = gateway.read(listOf(id)).getValue(id)
        assertEquals(word, added.fields["Word"])
        assertTrue("mod ${added.mod}", added.mod > 0)

        gateway.update(id, mid, mapOf("Translation" to "second"), emptySet())
        assertEquals("second", gateway.read(listOf(id)).getValue(id).fields["Translation"])

        assertEquals(setOf(id), gateway.read(listOf(id, MISSING_ID)).keys)

        assertTrue(gateway.delete(id))
        created -= id
        assertEquals(emptyMap<Long, NoteSnapshot>(), gateway.read(listOf(id)))
    }

    @Test
    fun staleNoteTypeIdFallsBackToName() {
        val (mid, _) = gateway.ensureNoteType(null, 0)
        // The API can't delete a note type; an id that never existed stands in for a deleted one.
        val (staleMid, staleVersion) = gateway.ensureNoteType(knownId = MISSING_ID, knownVersion = 0)
        assertEquals(mid, staleMid)
        assertEquals(NoteTypeSpec.VERSION, staleVersion)
    }

    @Test
    fun otherSourceSearchAndAppIdLookup() {
        val api = AddContentApi(context)
        val basicMid = api.modelList?.entries?.firstOrNull { it.value == BASIC_MODEL }?.key
            ?: checkNotNull(api.addNewBasicModel(BASIC_MODEL))
        val deckId = gateway.ensureDeck(DECK)
        val basicTags = setOf("moon-vocab-instrumented")
        val basicFields = listOf("Zwiebelkuchenmoonvocab", "onion tart")
        val basicId = checkNotNull(api.addNote(basicMid, deckId, basicFields.toTypedArray(), basicTags))
        created += basicId

        assertEquals(DECK, gateway.findElsewhere(listOf("zwiebelkuchenmoonvocab")))
        assertNull(gateway.findElsewhere(listOf("zwiebelkuchen_oonvocab")))
        assertNull(gateway.findElsewhere(listOf("-zwiebelkuchenmoonvocab")))

        val (mid, _) = gateway.ensureNoteType(null, 0)
        val ownId = gateway.add(mid, deckId, mapOf("Word" to "Zwiebelkuchenmoonvocab2", "AppId" to "instrumented-uuid-1"), setOf("moon-vocab"))
        created += ownId
        assertNull(gateway.findElsewhere(listOf("zwiebelkuchenmoonvocab2")))
        assertEquals(mapOf("instrumented-uuid-1" to ownId), gateway.findByAppIds(mid, listOf("instrumented-uuid-1", "nope")))

        val basicAfter = checkNotNull(api.getNote(basicId))
        assertEquals(basicFields, basicAfter.fields.toList())
        assertEquals(basicTags, basicAfter.tags)
    }

    private companion object {
        const val DECK = "Moon+::InstrumentedTest"
        const val BASIC_MODEL = "Moon+ Vocab InstrumentedTest Basic"
        const val MISSING_ID = 999_999_999L
    }
}

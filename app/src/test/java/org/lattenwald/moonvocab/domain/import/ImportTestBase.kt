package org.lattenwald.moonvocab.domain.import

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.lattenwald.moonvocab.data.backup.BackupReader
import org.lattenwald.moonvocab.data.backup.MrproFixture
import org.lattenwald.moonvocab.data.backup.NoteRow
import org.lattenwald.moonvocab.data.books.BookSource
import org.lattenwald.moonvocab.data.books.BookText
import org.lattenwald.moonvocab.data.db.AppDatabase
import org.lattenwald.moonvocab.data.db.CardEntity
import org.lattenwald.moonvocab.data.settings.Settings
import org.lattenwald.moonvocab.domain.card.CardStore
import org.lattenwald.moonvocab.domain.card.DraftResult
import org.lattenwald.moonvocab.domain.card.NoteSync
import org.lattenwald.moonvocab.domain.card.SenseJudge
import org.lattenwald.moonvocab.domain.card.Translation
import org.lattenwald.moonvocab.domain.card.TranslationItem
import org.lattenwald.moonvocab.domain.card.Translator
import org.lattenwald.moonvocab.domain.card.buildDraft
import org.lattenwald.moonvocab.domain.mark.MarkStyle
import org.lattenwald.moonvocab.domain.mark.StyleCombo
import org.lattenwald.moonvocab.domain.text.SentenceResult
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
abstract class ImportTestBase {
    @get:Rule val folder = TemporaryFolder()
    protected val tmp: File get() = folder.root

    protected val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries().build()
    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    protected val settings by lazy {
        Settings(PreferenceDataStoreFactory.create(scope = storeScope) { File(tmp, "settings.preferences_pb") })
    }
    protected val fakeAnki = FakeAnki()
    protected val fakeBooks = FakeBooks(mapOf(PATH to bookText(30)))
    protected val store = CardStore(db)
    protected val sender by lazy { CardSender(db, fakeAnki, settings, store) { NOW } }
    protected val sync by lazy { NoteSync(db, fakeAnki, store) }
    protected val check by lazy { AnkiCheck(sync, settings) { NOW } }

    protected suspend fun stored(id: Long): CardEntity = db.cards().byIds(listOf(id)).single()

    @After fun closeAll() {
        db.close()
        storeScope.cancel()
    }

    protected fun TestScope.pipeline(
        translate: Translator = Translator { items, _, _, _ -> items.associate { it.id to tr(it) } },
        books: () -> BookSource = { fakeBooks },
        draft: (SentenceResult, Translation, Int) -> DraftResult = ::buildDraft,
        judge: SenseJudge = SenseJudge { emptyMap() },
    ) = ImportPipeline(
        db, store, BackupReader(tmp), books, translate, judge, fakeAnki, sender, check, settings, StandardTestDispatcher(testScheduler), draft,
    )

    protected fun rows(n: Int, path: String = PATH, title: String = TITLE, from: Int = 0) = (from until from + n).map { i ->
        NoteRow(title, path, chapter = 0, position = i * SENTENCE.length, color = ORANGE, time = T0 + i, original = word(i), bak = "1")
    }

    protected fun backup(n: Int) = MrproFixture.build(tmp, rows(n))

    protected fun req(backup: File?, send: Boolean = false, styles: Set<StyleCombo> = setOf(WAVY_ORANGE), only: String? = null) =
        ImportRequest(backup, styles, only, send)

    companion object {
        const val PATH = "/sdcard/Books/Greg Egan/Diaspora.epub"
        const val TITLE = "Diaspora"
        const val ORANGE = -28160
        const val T0 = 1_700_000_000_000L
        const val NOW = 1_800_000_000_000L
        const val SENTENCE = "On the page the reader found WORD and smiled at it. "
        val WAVY_ORANGE = StyleCombo(MarkStyle.WAVY, ORANGE)

        fun word(i: Int) = "w${i}x"

        fun bookText(n: Int) = BookText(TITLE, "Greg Egan", "eng", listOf((0 until n).joinToString("") { SENTENCE.replace("WORD", word(it)) }))

        fun tr(item: TranslationItem) = Translation(
            id = item.id, sourceLanguage = "de", focus = item.marked, headword = item.marked, transcription = "", lemma = item.marked,
            widened = false,
            grammar = "n.", translation = "t-${item.marked}", meaning = "", excerpt = null,
            inventedExample = "Ein [${item.marked}] hier.", sourceNote = null,
        )
    }
}

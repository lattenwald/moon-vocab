package org.lattenwald.moonvocab

import android.content.Context
import android.os.SystemClock
import androidx.room.Room
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import org.lattenwald.moonvocab.data.anki.AnkiDroidGateway
import org.lattenwald.moonvocab.data.anki.AnkiStatus
import org.lattenwald.moonvocab.data.backup.BackupFinder
import org.lattenwald.moonvocab.data.backup.BackupReader
import org.lattenwald.moonvocab.data.books.FolderBookSource
import org.lattenwald.moonvocab.data.books.SafBookFolder
import org.lattenwald.moonvocab.data.db.AppDatabase
import org.lattenwald.moonvocab.data.llm.ConnectionTest
import org.lattenwald.moonvocab.data.llm.LlmClient
import org.lattenwald.moonvocab.data.llm.LlmConfig
import org.lattenwald.moonvocab.data.llm.LlmSenseJudge
import org.lattenwald.moonvocab.data.llm.ModelCatalog
import org.lattenwald.moonvocab.data.llm.TranslationService
import org.lattenwald.moonvocab.data.settings.FolderGrants
import org.lattenwald.moonvocab.data.settings.Settings
import org.lattenwald.moonvocab.data.settings.settingsDataStore
import org.lattenwald.moonvocab.domain.card.CardStore
import org.lattenwald.moonvocab.domain.card.NoteSync
import org.lattenwald.moonvocab.domain.cardpage.CardRetranslator
import org.lattenwald.moonvocab.domain.import.AnkiCheck
import org.lattenwald.moonvocab.domain.import.CardSender
import org.lattenwald.moonvocab.domain.import.ImportPipeline
import org.lattenwald.moonvocab.work.ImportRuns
import org.lattenwald.moonvocab.work.WorkRunQueue
import org.lattenwald.moonvocab.work.importResultStore

class AppContainer(context: Context) {
    private val app = context.applicationContext

    val io: CoroutineDispatcher = Dispatchers.IO
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + io)
    val db: AppDatabase = Room.databaseBuilder(app, AppDatabase::class.java, AppDatabase.NAME).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3).build()
    val settings = Settings(app.settingsDataStore)
    val folders = FolderGrants(app.contentResolver, settings, io)
    val anki = AnkiStatus(AnkiDroidGateway(app), io)
    val backupReader = BackupReader(app.cacheDir)
    val backupFinder = BackupFinder(app.contentResolver, app.cacheDir, backupReader, io)
    val cards = CardStore(db)
    val sender = CardSender(db, anki, settings, cards, clock = System::currentTimeMillis)
    val noteSync = NoteSync(db, anki, cards)
    val ankiCheck = AnkiCheck(noteSync, settings, clock = System::currentTimeMillis)

    // LlmClient does its own retries; OkHttp's silent retry would add attempts.
    private val http = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
    private val llm = LlmClient(http, config = { settings.current().activeService.let { LlmConfig(it.baseUrl, it.apiKey, it.model) } })
    private val translator = TranslationService(llm)
    val retranslator = CardRetranslator(db, translator, settings)
    val modelCatalog = ModelCatalog(http)
    // Three attempts at 60 s each would hold "Checking" for minutes on a stalled server.
    val connectionTest = ConnectionTest(http.newBuilder().callTimeout(15, TimeUnit.SECONDS).build(), clock = SystemClock::elapsedRealtime)

    suspend fun bookSource() = FolderBookSource(settings.current().booksTree?.let { SafBookFolder(app, it) }, app.cacheDir)

    val pipeline = ImportPipeline(
        db = db,
        store = cards,
        reader = backupReader,
        books = ::bookSource,
        translator = translator,
        judge = LlmSenseJudge(llm),
        anki = anki,
        sender = sender,
        check = ankiCheck,
        settings = settings,
        io = io,
    )

    // Lazy: Robolectric tests build App without WorkManager's startup initializer.
    val importRuns: ImportRuns by lazy { ImportRuns(WorkRunQueue(WorkManager.getInstance(app)), app.importResultStore) }
}

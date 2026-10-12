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
import kotlinx.coroutines.sync.Mutex
import okhttp3.OkHttpClient
import org.lattenwald.moonvocab.data.anki.AnkiDroidGateway
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
import org.lattenwald.moonvocab.data.settings.Settings
import org.lattenwald.moonvocab.data.settings.settingsDataStore
import org.lattenwald.moonvocab.domain.cardpage.CardEditor
import org.lattenwald.moonvocab.domain.cardpage.CardRetranslator
import org.lattenwald.moonvocab.domain.import.AnkiCheck
import org.lattenwald.moonvocab.domain.import.CardSender
import org.lattenwald.moonvocab.domain.import.ImportPipeline
import org.lattenwald.moonvocab.domain.inbox.InboxActions

class AppContainer(context: Context) {
    private val app = context.applicationContext

    val io: CoroutineDispatcher = Dispatchers.IO
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + io)
    val db: AppDatabase = Room.databaseBuilder(app, AppDatabase::class.java, AppDatabase.NAME).addMigrations(AppDatabase.MIGRATION_1_2).build()
    val settings = Settings(app.settingsDataStore)
    val anki = AnkiDroidGateway(app)
    val backupReader = BackupReader(app.cacheDir)
    val backupFinder = BackupFinder(app.contentResolver, app.cacheDir, backupReader, io)
    private val ankiLock = Mutex()
    val sender = CardSender(db, anki, settings, ankiLock, clock = System::currentTimeMillis)
    val ankiCheck = AnkiCheck(db, anki, ankiLock, settings, clock = System::currentTimeMillis)
    val inboxActions = InboxActions(db, ankiLock)
    val cardEditor = CardEditor(db, anki, ankiLock)

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
    val workManager: WorkManager by lazy { WorkManager.getInstance(app) }
}

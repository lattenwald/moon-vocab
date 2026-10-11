package org.lattenwald.moonvocab

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import org.lattenwald.moonvocab.data.anki.AnkiDroidGateway
import org.lattenwald.moonvocab.data.backup.BackupReader
import org.lattenwald.moonvocab.data.books.FolderBookSource
import org.lattenwald.moonvocab.data.books.SafBookFolder
import org.lattenwald.moonvocab.data.db.AppDatabase
import org.lattenwald.moonvocab.data.llm.LlmClient
import org.lattenwald.moonvocab.data.llm.LlmConfig
import org.lattenwald.moonvocab.data.llm.LlmSenseJudge
import org.lattenwald.moonvocab.data.llm.TranslationService
import org.lattenwald.moonvocab.data.settings.Settings
import org.lattenwald.moonvocab.data.settings.settingsDataStore
import org.lattenwald.moonvocab.domain.import.AnkiCheck
import org.lattenwald.moonvocab.domain.import.CardSender
import org.lattenwald.moonvocab.domain.import.ImportPipeline

class AppContainer(context: Context) {
    private val app = context.applicationContext

    val io: CoroutineDispatcher = Dispatchers.IO
    val db: AppDatabase = Room.databaseBuilder(app, AppDatabase::class.java, AppDatabase.NAME).build()
    val settings = Settings(app.settingsDataStore)
    val anki = AnkiDroidGateway(app)
    val backupReader = BackupReader(app.cacheDir)
    val sender = CardSender(db, anki, settings, clock = System::currentTimeMillis)
    val ankiCheck = AnkiCheck(db, anki)

    // LlmClient does its own retries; OkHttp's silent retry would add attempts.
    private val http = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
    private val llm = LlmClient(http, config = { settings.current().activeService.let { LlmConfig(it.baseUrl, it.apiKey, it.model) } })

    val pipeline = ImportPipeline(
        db = db,
        reader = backupReader,
        books = { FolderBookSource(settings.current().booksTree?.let { SafBookFolder(app, it) }, app.cacheDir) },
        translator = TranslationService(llm),
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

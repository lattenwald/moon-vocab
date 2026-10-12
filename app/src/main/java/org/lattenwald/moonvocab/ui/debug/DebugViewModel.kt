package org.lattenwald.moonvocab.ui.debug

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.work.WorkManager
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lattenwald.moonvocab.App
import org.lattenwald.moonvocab.data.anki.AnkiAvailability
import org.lattenwald.moonvocab.data.anki.AnkiGateway
import org.lattenwald.moonvocab.data.backup.BackupFinder
import org.lattenwald.moonvocab.data.backup.FoundBackup
import org.lattenwald.moonvocab.data.books.FolderBookSource
import org.lattenwald.moonvocab.data.db.AppDatabase
import org.lattenwald.moonvocab.data.db.CardEntity
import org.lattenwald.moonvocab.data.settings.LlmService
import org.lattenwald.moonvocab.data.settings.Settings
import org.lattenwald.moonvocab.data.settings.SettingsSnapshot
import org.lattenwald.moonvocab.domain.card.CardStatus
import org.lattenwald.moonvocab.domain.import.AnkiCheck
import org.lattenwald.moonvocab.domain.import.CardSender
import org.lattenwald.moonvocab.domain.import.CheckResult
import org.lattenwald.moonvocab.domain.import.ImportRequest
import org.lattenwald.moonvocab.domain.import.SendResult
import org.lattenwald.moonvocab.domain.mark.StyleCombo
import org.lattenwald.moonvocab.domain.mark.defaultStyles
import org.lattenwald.moonvocab.domain.mark.fileKey
import org.lattenwald.moonvocab.work.ImportState
import org.lattenwald.moonvocab.work.ImportWork

sealed interface Notice {
    data class Checked(val result: CheckResult) : Notice
    data class Sent(val result: SendResult) : Notice
    data class Deleted(val notes: Int) : Notice
    data class Exported(val cards: Int) : Notice
    data class PositionsReported(val summary: PositionReportSummary) : Notice
    data class Forgotten(val cards: Int) : Notice
    data class Error(val message: String) : Notice
}

data class ComboCount(val combo: StyleCombo, val count: Int)

data class BookCount(val fileKey: String, val title: String, val count: Int)

data class BackupState(val searching: Boolean = false, val searched: Boolean = false, val name: String? = null, val skipped: List<String> = emptyList())

data class DebugState(
    val settings: SettingsSnapshot? = null,
    val availability: AnkiAvailability? = null,
    val backup: BackupState = BackupState(),
    val combos: List<ComboCount> = emptyList(),
    val books: List<BookCount> = emptyList(),
    val book: String? = null,
    val sendToAnki: Boolean = false,
    val importState: ImportState = ImportState(),
    val cards: List<CardEntity> = emptyList(),
    val busy: Boolean = false,
    val notice: Notice? = null,
)

class DebugViewModel(
    private val resolver: ContentResolver,
    private val db: AppDatabase,
    private val settings: Settings,
    private val finder: BackupFinder,
    private val sender: CardSender,
    private val ankiCheck: AnkiCheck,
    private val anki: AnkiGateway,
    private val workManager: WorkManager,
    private val io: CoroutineDispatcher,
    private val bookSource: suspend () -> FolderBookSource,
    private val appVersion: String,
) : ViewModel() {
    private data class Local(
        val availability: AnkiAvailability? = null,
        val searching: Boolean = false,
        val searched: Boolean = false,
        val picked: FoundBackup? = null,
        val skipped: List<String> = emptyList(),
        val book: String? = null,
        val sendToAnki: Boolean = false,
        val busy: Boolean = false,
        val notice: Notice? = null,
    )

    private val local = MutableStateFlow(Local())

    private val allCards = db.cards().observeAll().catch { e ->
        showError(e)
        emit(emptyList())
    }

    val state: StateFlow<DebugState> = combine(
        local, settings.snapshot, allCards, workManager.getWorkInfosForUniqueWorkFlow(ImportWork.NAME),
    ) { l, s, cards, infos ->
        val marks = l.picked?.marks.orEmpty()
        DebugState(
            settings = s,
            availability = l.availability,
            backup = BackupState(l.searching, l.searched, l.picked?.name, l.skipped),
            combos = marks.groupingBy { it.combo }.eachCount().map { ComboCount(it.key, it.value) }.sortedByDescending { it.count },
            books = marks.filter { it.combo in s.styles }.groupBy { it.fileKey }
                .map { (key, ms) -> BookCount(key, ms.first().bookTitle, ms.size) }
                .sortedBy { it.title.lowercase() },
            book = l.book,
            sendToAnki = l.sendToAnki,
            importState = ImportWork.state(infos.firstOrNull()),
            cards = cards,
            busy = l.busy,
            notice = l.notice,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DebugState())

    fun refreshAvailability() = launchSafely {
        val availability = withContext(io) { anki.availability() }
        local.update { it.copy(availability = availability) }
    }

    fun setBackupTree(uri: Uri) = launchSafely {
        withContext(io) { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        settings.update { it.copy(backupTree = uri) }
        findBackup()
    }

    fun setBooksTree(uri: Uri) = launchSafely {
        withContext(io) { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        settings.update { it.copy(booksTree = uri) }
    }

    fun selectService(id: String) = launchSafely { settings.update { it.copy(activeServiceId = id) } }

    fun editService(id: String, transform: (LlmService) -> LlmService) = launchSafely {
        settings.update { s -> s.copy(services = s.services.map { if (it.id == id) transform(it) else it }) }
    }

    fun addService(name: String) = launchSafely {
        val service = LlmService(id = UUID.randomUUID().toString(), name = name, baseUrl = "", apiKey = "", model = "")
        settings.update { it.copy(services = it.services + service, activeServiceId = service.id) }
    }

    fun findNewestBackup() = launchSafely { findBackup() }

    fun toggleStyle(combo: StyleCombo) = launchSafely {
        settings.update { s -> s.copy(styles = if (combo in s.styles) s.styles - combo else s.styles + combo) }
    }

    fun selectBook(fileKey: String?) = local.update { it.copy(book = fileKey) }

    fun setSendToAnki(send: Boolean) = local.update { it.copy(sendToAnki = send) }

    fun startImport() = launchSafely {
        val l = local.value
        val request = ImportRequest(l.picked?.file, settings.current().styles, l.book, l.sendToAnki)
        ImportWork.enqueue(workManager, ImportWork.input(request))
    }

    fun retryFailed() = launchSafely { ImportWork.enqueue(workManager, ImportWork.retryInput()) }

    fun checkAnki() = whileBusy { Notice.Checked(withContext(io) { ankiCheck.check() }) }

    fun sendAllPending() = whileBusy {
        Notice.Sent(withContext(io) { sender.send(db.cards().byStatus(CardStatus.PENDING).filter { it.elsewhereDeck == null }.map { it.id }) })
    }

    fun deleteAllCreated() = whileBusy { Notice.Deleted(withContext(io) { sender.deleteAllCreated() }) }

    fun setTestDeck(on: Boolean) = launchSafely { settings.update { it.copy(deckOverride = if (on) TEST_DECK else null) } }

    fun exportCards(uri: Uri) = whileBusy {
        val count = withContext(io) {
            val cards = db.cards().observeAll().first()
            val json = cardsExportJson(cards, db.marks().all(), db.books().all())
            checkNotNull(resolver.openOutputStream(uri)) { "cannot open $uri" }.use { it.write(json.toByteArray()) }
            cards.size
        }
        Notice.Exported(count)
    }

    fun exportPositionReport(uri: Uri) = whileBusy {
        val backup = checkNotNull(local.value.picked) { "no backup" }
        val source = bookSource()
        val summary = withContext(io) {
            val sentences = db.cards().observeAll().first().associate { it.id to it.sentenceHtml }
            val cardSentences = db.marks().all().mapNotNull { m -> m.cardId?.let(sentences::get)?.let { m.key to it } }.toMap()
            checkNotNull(resolver.openOutputStream(uri)) { "cannot open $uri" }.use { out ->
                writePositionReport(out, appVersion, backup.file, backup.marks, cardSentences, source::copy, source::read)
            }
        }
        Notice.PositionsReported(summary)
    }

    fun forgetUnsent() = whileBusy { Notice.Forgotten(withContext(io) { db.cards().deleteUnsent() }) }

    private suspend fun findBackup() {
        val tree = settings.current().backupTree ?: return
        local.update { it.copy(searching = true) }
        try {
            val search = finder.newest(tree)
            local.update { it.copy(searched = true, picked = search.chosen, skipped = search.skipped, book = null) }
            val present = search.chosen?.marks.orEmpty().mapTo(HashSet()) { it.combo }
            settings.update { s -> s.copy(styles = defaultStyles(s.styles, present)) }
        } finally {
            local.update { it.copy(searching = false) }
        }
    }

    private fun whileBusy(action: suspend () -> Notice) = launchSafely {
        local.update { it.copy(busy = true) }
        try {
            val notice = action()
            local.update { it.copy(notice = notice) }
        } finally {
            local.update { it.copy(busy = false) }
        }
    }

    private fun launchSafely(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError(e)
            }
        }
    }

    private fun showError(e: Throwable) = local.update { it.copy(notice = Notice.Error(e.message ?: e::class.simpleName.orEmpty())) }

    companion object {
        private const val TEST_DECK = "Moon+::Test"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as App
                val c = app.container
                val info = app.packageManager.getPackageInfo(app.packageName, 0)
                DebugViewModel(
                    app.contentResolver, c.db, c.settings, c.backupFinder, c.sender, c.ankiCheck, c.anki, c.workManager, c.io,
                    c::bookSource, "${info.versionName} (${info.longVersionCode})",
                )
            }
        }
    }
}

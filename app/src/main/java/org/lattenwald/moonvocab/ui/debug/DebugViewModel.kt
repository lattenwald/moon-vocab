package org.lattenwald.moonvocab.ui.debug

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.work.WorkManager
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lattenwald.moonvocab.App
import org.lattenwald.moonvocab.data.backup.BackupFinder
import org.lattenwald.moonvocab.data.books.FolderBookSource
import org.lattenwald.moonvocab.data.db.AppDatabase
import org.lattenwald.moonvocab.data.settings.Settings
import org.lattenwald.moonvocab.domain.import.CardSender
import org.lattenwald.moonvocab.work.ImportWork

enum class DebugAction { DELETE_NOTES, FORGET_UNSENT, EXPORT_CARDS, POSITION_REPORT }

sealed interface Outcome {
    data class Deleted(val notes: Int) : Outcome
    data class Forgotten(val cards: Int) : Outcome
    data class Exported(val cards: Int) : Outcome
    data class Reported(val summary: PositionReportSummary) : Outcome
    data object NoBackupFolder : Outcome
    data object NoBackup : Outcome
    data class Failed(val message: String) : Outcome
}

sealed interface ReportStep {
    data object Finding : ReportStep
    data class Writing(val progress: PositionReportProgress) : ReportStep
}

data class DebugState(
    val testDeck: String? = null,
    val importRunning: Boolean = false,
    val running: DebugAction? = null,
    val reportStep: ReportStep? = null,
    val outcomes: Map<DebugAction, Outcome> = emptyMap(),
) {
    val idle: Boolean get() = running == null && !importRunning
}

class DebugViewModel(
    private val resolver: ContentResolver,
    private val db: AppDatabase,
    private val settings: Settings,
    private val finder: BackupFinder,
    private val sender: CardSender,
    workManager: WorkManager,
    private val io: CoroutineDispatcher,
    private val bookSource: suspend () -> FolderBookSource,
    private val appVersion: String,
) : ViewModel() {
    private data class Local(
        val running: DebugAction? = null,
        val reportStep: ReportStep? = null,
        val outcomes: Map<DebugAction, Outcome> = emptyMap(),
    )

    private val local = MutableStateFlow(Local())

    val state: StateFlow<DebugState> = combine(
        local, settings.snapshot, workManager.getWorkInfosForUniqueWorkFlow(ImportWork.NAME),
    ) { l, s, infos ->
        DebugState(
            testDeck = s.deckOverride,
            importRunning = ImportWork.state(infos.firstOrNull()).running,
            running = l.running,
            reportStep = l.reportStep,
            outcomes = l.outcomes,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DebugState())

    fun setTestDeck(on: Boolean) = viewModelScope.launch { settings.update { it.copy(deckOverride = if (on) TEST_DECK else null) } }

    fun deleteAllCreated() = run(DebugAction.DELETE_NOTES) { Outcome.Deleted(withContext(io) { sender.deleteAllCreated() }) }

    fun forgetUnsent() = run(DebugAction.FORGET_UNSENT) { Outcome.Forgotten(withContext(io) { db.cards().deleteUnsent() }) }

    fun exportCards(uri: Uri) = run(DebugAction.EXPORT_CARDS) {
        val count = withContext(io) {
            val cards = db.cards().observeAll().first()
            val json = cardsExportJson(cards, db.marks().all(), db.books().all())
            checkNotNull(resolver.openOutputStream(uri)) { "cannot open $uri" }.use { it.write(json.toByteArray()) }
            cards.size
        }
        Outcome.Exported(count)
    }

    fun exportPositionReport(uri: Uri) = run(DebugAction.POSITION_REPORT) {
        local.update { it.copy(reportStep = ReportStep.Finding) }
        val tree = settings.current().backupTree
        val backup = tree?.let { finder.newest(it).chosen }
        if (backup == null) {
            withContext(io) { runCatching { DocumentsContract.deleteDocument(resolver, uri) } } // the picker already made the file
            return@run if (tree == null) Outcome.NoBackupFolder else Outcome.NoBackup
        }
        val source = bookSource()
        val summary = withContext(io) {
            val sentences = db.cards().observeAll().first().associate { it.id to it.sentenceHtml }
            val cardSentences = db.marks().all().mapNotNull { m -> m.cardId?.let(sentences::get)?.let { m.key to it } }.toMap()
            checkNotNull(resolver.openOutputStream(uri)) { "cannot open $uri" }.use { out ->
                writePositionReport(out, appVersion, backup.file, backup.marks, cardSentences, source::copy, source::read) { p ->
                    local.update { it.copy(reportStep = ReportStep.Writing(p)) }
                }
            }
        }
        Outcome.Reported(summary)
    }

    private fun run(action: DebugAction, block: suspend () -> Outcome) {
        if (!state.value.idle) return
        local.update { it.copy(running = action, outcomes = it.outcomes - action) }
        viewModelScope.launch {
            val outcome = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Outcome.Failed(e.message ?: e::class.simpleName.orEmpty())
            }
            local.update { it.copy(running = null, reportStep = null, outcomes = it.outcomes + (action to outcome)) }
        }
    }

    companion object {
        const val TEST_DECK = "Moon+::Test"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as App
                val c = app.container
                val info = app.packageManager.getPackageInfo(app.packageName, 0)
                DebugViewModel(
                    app.contentResolver, c.db, c.settings, c.backupFinder, c.sender, c.workManager, c.io,
                    c::bookSource, "${info.versionName} (${info.longVersionCode})",
                )
            }
        }
    }
}

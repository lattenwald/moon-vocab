package org.lattenwald.moonvocab.work

import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.io.File
import org.lattenwald.moonvocab.data.llm.LlmException
import org.lattenwald.moonvocab.domain.import.ImportProgress
import org.lattenwald.moonvocab.domain.import.ImportRequest
import org.lattenwald.moonvocab.domain.import.ImportSummary
import org.lattenwald.moonvocab.domain.mark.parseStyleCombo

object ImportWork {
    const val NAME = "import"

    private const val BACKUP = "backup"
    private const val STYLES = "styles"
    private const val BOOK = "book"
    private const val SEND = "send"
    private const val RETRY_FAILED = "retryFailed"

    private const val DONE = "done"
    private const val TOTAL = "total"
    private const val BOOK_TITLE = "bookTitle"

    private const val NEW_CARDS = "newCards"
    private const val FAILED = "failed"
    private const val ALREADY_CARD = "alreadyCard"
    private const val SKIPPED_DISCARDED = "skippedDiscarded"
    private const val SENSE_FAILED = "senseFailed"
    private const val SENT = "sent"
    private const val INCOMPLETE_BACKUP = "incompleteBackup"
    private const val STOPPED = "stopped"
    private const val SEND_ERROR = "sendError"
    private const val ERROR = "error"

    fun enqueue(workManager: WorkManager, input: Data) {
        val request = OneTimeWorkRequestBuilder<ImportWorker>().setInputData(input).build()
        workManager.enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun input(request: ImportRequest): Data = workDataOf(
        BACKUP to request.backup?.path,
        STYLES to request.styles.map { it.encode() }.toTypedArray(),
        BOOK to request.onlyBookFileKey,
        SEND to request.sendToAnki,
    )

    fun retryInput(): Data = workDataOf(RETRY_FAILED to true)

    fun isRetry(data: Data): Boolean = data.getBoolean(RETRY_FAILED, false)

    fun request(data: Data) = ImportRequest(
        backup = data.getString(BACKUP)?.let(::File),
        styles = data.getNullableStringArray(STYLES).orEmpty().filterNotNull().mapNotNull(::parseStyleCombo).toSet(),
        onlyBookFileKey = data.getString(BOOK),
        sendToAnki = data.getBoolean(SEND, false),
    )

    fun progress(p: ImportProgress): Data = workDataOf(DONE to p.done, TOTAL to p.total, BOOK_TITLE to p.bookTitle)

    fun progress(data: Data): ImportProgress? =
        if (TOTAL in data.keyValueMap) ImportProgress(data.getInt(DONE, 0), data.getInt(TOTAL, 0), data.getString(BOOK_TITLE)) else null

    fun output(s: ImportSummary): Data = workDataOf(
        NEW_CARDS to s.newCards,
        FAILED to s.failed,
        ALREADY_CARD to s.alreadyCard,
        SKIPPED_DISCARDED to s.skippedDiscarded,
        SENSE_FAILED to s.senseFailed,
        SENT to s.sent,
        INCOMPLETE_BACKUP to s.incompleteBackup,
        STOPPED to s.stopped?.name,
        SEND_ERROR to s.sendError,
    )

    fun summary(data: Data) = ImportSummary(
        newCards = data.getInt(NEW_CARDS, 0),
        failed = data.getInt(FAILED, 0),
        alreadyCard = data.getInt(ALREADY_CARD, 0),
        skippedDiscarded = data.getInt(SKIPPED_DISCARDED, 0),
        senseFailed = data.getInt(SENSE_FAILED, 0),
        sent = data.getInt(SENT, 0),
        incompleteBackup = data.getString(INCOMPLETE_BACKUP),
        stopped = data.getString(STOPPED)?.let { name -> LlmException.Stop.Kind.entries.firstOrNull { it.name == name } },
        sendError = data.getString(SEND_ERROR),
    )

    fun failure(message: String): Data = workDataOf(ERROR to message)

    fun failureMessage(data: Data): String? = data.getString(ERROR)
}

package org.lattenwald.moonvocab.work

import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.lattenwald.moonvocab.domain.import.ImportProgress
import org.lattenwald.moonvocab.domain.import.ImportRequest
import org.lattenwald.moonvocab.domain.mark.parseStyleCombo

class WorkRunQueue(private val workManager: WorkManager) : RunQueue {
    override val active: Flow<ActiveRun?> = workManager.getWorkInfosForUniqueWorkFlow(NAME)
        .map { infos -> infos.firstOrNull { !it.state.isFinished }?.let { ActiveRun(ImportWork.progress(it.progress)) } }

    override suspend fun enqueue(job: ImportJob): Boolean {
        val request = OneTimeWorkRequestBuilder<ImportWorker>().setInputData(ImportWork.input(job)).build()
        workManager.enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request).await()
        // KEEP drops the request while unfinished work exists.
        return workManager.getWorkInfoByIdFlow(request.id).first() != null
    }

    private companion object {
        const val NAME = "import"
    }
}

internal object ImportWork {
    private const val BACKUP = "backup"
    private const val STYLES = "styles"
    private const val BOOK = "book"
    private const val SEND = "send"
    private const val RETRY_FAILED = "retryFailed"

    private const val DONE = "done"
    private const val TOTAL = "total"
    private const val BOOK_TITLE = "bookTitle"

    fun input(job: ImportJob): Data = when (job) {
        ImportJob.RetryFailed -> workDataOf(RETRY_FAILED to true)
        is ImportJob.Import -> workDataOf(
            BACKUP to job.request.backup?.path,
            STYLES to job.request.styles.map { it.encode() }.toTypedArray(),
            BOOK to job.request.onlyBookFileKey,
            SEND to job.request.sendToAnki,
        )
    }

    fun job(data: Data): ImportJob = if (data.getBoolean(RETRY_FAILED, false)) {
        ImportJob.RetryFailed
    } else {
        ImportJob.Import(
            ImportRequest(
                backup = data.getString(BACKUP)?.let(::File),
                styles = data.getNullableStringArray(STYLES).orEmpty().filterNotNull().mapNotNull(::parseStyleCombo).toSet(),
                onlyBookFileKey = data.getString(BOOK),
                sendToAnki = data.getBoolean(SEND, false),
            ),
        )
    }

    fun progress(p: ImportProgress): Data = workDataOf(DONE to p.done, TOTAL to p.total, BOOK_TITLE to p.bookTitle)

    fun progress(data: Data): ImportProgress? =
        if (TOTAL in data.keyValueMap) ImportProgress(data.getInt(DONE, 0), data.getInt(TOTAL, 0), data.getString(BOOK_TITLE)) else null
}

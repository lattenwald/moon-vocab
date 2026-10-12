package org.lattenwald.moonvocab.data.backup

import kotlin.coroutines.cancellation.CancellationException
import org.lattenwald.moonvocab.domain.mark.RawMark

data class BackupDocument(val documentId: String, val name: String, val lastModified: Long) {
    val isBackup: Boolean get() = name.endsWith(".mrpro", ignoreCase = true)
}

data class BackupPick(val chosen: BackupDocument?, val marks: List<RawMark>, val skipped: List<BackupDocument>)

fun pickNewestComplete(documents: List<BackupDocument>, read: (BackupDocument) -> BackupContents): BackupPick {
    val skipped = mutableListOf<BackupDocument>()
    for (doc in documents.filter { it.isBackup }.sortedByDescending { it.lastModified }) {
        val contents = try {
            read(doc)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BackupContents.Incomplete(e.message ?: e.toString())
        }
        when (contents) {
            is BackupContents.Complete -> return BackupPick(doc, contents.marks, skipped)
            is BackupContents.Incomplete -> skipped += doc
        }
    }
    return BackupPick(null, emptyList(), skipped)
}

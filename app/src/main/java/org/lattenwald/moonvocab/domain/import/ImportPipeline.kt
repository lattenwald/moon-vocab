package org.lattenwald.moonvocab.domain.import

import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.lattenwald.moonvocab.data.backup.BackupContents
import org.lattenwald.moonvocab.data.backup.BackupReader
import org.lattenwald.moonvocab.data.books.BookSource
import org.lattenwald.moonvocab.data.books.BookText
import org.lattenwald.moonvocab.data.books.isoLanguage
import org.lattenwald.moonvocab.data.db.AppDatabase
import org.lattenwald.moonvocab.data.db.BookEntity
import org.lattenwald.moonvocab.data.db.CardEntity
import org.lattenwald.moonvocab.data.db.MarkEntity
import org.lattenwald.moonvocab.data.llm.LlmException
import org.lattenwald.moonvocab.data.settings.Settings
import org.lattenwald.moonvocab.data.settings.SettingsSnapshot
import org.lattenwald.moonvocab.domain.card.CardDraft
import org.lattenwald.moonvocab.domain.card.CardEvent
import org.lattenwald.moonvocab.domain.card.CardStatus
import org.lattenwald.moonvocab.domain.card.DraftResult
import org.lattenwald.moonvocab.domain.card.Translation
import org.lattenwald.moonvocab.domain.card.TranslationItem
import org.lattenwald.moonvocab.domain.card.Translator
import org.lattenwald.moonvocab.domain.card.buildDraft
import org.lattenwald.moonvocab.domain.card.next
import org.lattenwald.moonvocab.domain.mark.RawMark
import org.lattenwald.moonvocab.domain.mark.StyleCombo
import org.lattenwald.moonvocab.domain.mark.fileKey
import org.lattenwald.moonvocab.domain.text.NormalizedText
import org.lattenwald.moonvocab.domain.text.SentenceResult
import org.lattenwald.moonvocab.domain.text.findSentence
import org.lattenwald.moonvocab.domain.text.normalize

data class ImportRequest(val backup: File?, val styles: Set<StyleCombo>, val onlyBookFileKey: String?, val sendToAnki: Boolean)

data class ImportProgress(val done: Int, val total: Int, val bookTitle: String?)

data class ImportSummary(
    val newCards: Int,
    val failed: Int,
    val sent: Int,
    val incompleteBackup: String?,
    val stopped: LlmException.Stop.Kind?,
    val sendError: String?,
)

class ImportPipeline(
    private val db: AppDatabase,
    private val reader: BackupReader,
    private val books: suspend () -> BookSource,
    private val translator: Translator,
    private val sender: CardSender,
    private val check: AnkiCheck,
    private val settings: Settings,
    private val io: CoroutineDispatcher,
    private val draft: (SentenceResult, Translation, Int) -> DraftResult = ::buildDraft,
) {
    suspend fun run(request: ImportRequest, progress: (ImportProgress) -> Unit): ImportSummary {
        var incomplete: String? = null
        if (request.backup != null) {
            when (val contents = withContext(io) { reader.read(request.backup) }) {
                is BackupContents.Incomplete -> incomplete = contents.reason
                is BackupContents.Complete -> store(contents.marks, request)
            }
        }
        val s = settings.current()
        val run = Run(books())
        val todo = db.marks().withoutCard(null)
            .filter { request.onlyBookFileKey == null || run.book(it.bookId).fileKey == request.onlyBookFileKey }

        var done = 0
        var newCards = 0
        var failed = 0
        var sent = 0
        var sendError: String? = null
        var stopped: LlmException.Stop.Kind? = null
        for (batch in todo.chunked(BATCH_SIZE)) {
            val prepared = batch.map { run.prepare(it) }
            val ready = prepared.filter { it.error == null }
            var failure: String? = null
            val translations = try {
                if (ready.isEmpty()) emptyMap() else translator.translate(ready.map { it.item }, s.targetLanguage, s.maxExcerptWords)
            } catch (e: LlmException.Stop) {
                stopped = e.kind
                break
            } catch (e: LlmException.Failed) {
                failure = e.message ?: e::class.simpleName
                emptyMap()
            }
            for (p in ready) translations[p.item.id]?.let { run.learnLanguage(p.mark.bookId, it) }

            val cards = prepared.map { p ->
                val book = run.book(p.mark.bookId)
                val card = try {
                    card(p, book, failure, translations[p.item.id], s)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failedCard(p.mark, book, s, internalError(e))
                }
                card to listOf(p.mark.id)
            }
            val ids = db.writeBatch(cards)
            newCards += cards.count { it.first.status == CardStatus.PENDING }
            failed += cards.count { it.first.status == CardStatus.FAILED }

            if (request.sendToAnki) {
                val toSend = ids.filterIndexed { i, _ -> cards[i].first.let { it.status == CardStatus.PENDING && it.elsewhereDeck == null } }
                if (toSend.isNotEmpty()) {
                    val result = withContext(io) { sender.send(toSend) }
                    sent += result.sent
                    sendError = sendError ?: result.error
                }
            }
            done += batch.size
            progress(ImportProgress(done, todo.size, run.book(batch.last().bookId).title))
        }
        withContext(io) { check.run() }
        return ImportSummary(newCards, failed, sent, incomplete, stopped, sendError)
    }

    suspend fun retryFailed(): ImportSummary {
        val failed = db.cards().byStatus(CardStatus.FAILED).map { it.id }
        if (failed.isNotEmpty()) db.cards().delete(failed) // mark.cardId is ON DELETE SET NULL
        return run(ImportRequest(backup = null, styles = emptySet(), onlyBookFileKey = null, sendToAnki = false)) {}
    }

    private suspend fun store(marks: List<RawMark>, request: ImportRequest) {
        val bookIds = HashMap<String, Long>()
        val entities = marks.filter { it.combo in request.styles }.mapNotNull { m ->
            if (request.onlyBookFileKey != null && m.fileKey != request.onlyBookFileKey) return@mapNotNull null
            val bookId = bookIds.getOrPut(m.fileKey) {
                db.books().upsertByFileKey(
                    BookEntity(fileKey = m.fileKey, title = m.bookTitle, author = null, language = null, relativePath = m.bookPath),
                )
            }
            MarkEntity(
                key = m.key, bookId = bookId, chapter = m.chapter, position = m.position, text = m.text, style = m.combo.style,
                color = m.combo.color, markedAt = m.markedAt, cardId = null,
            )
        }
        db.marks().insertIgnoringKnown(entities)
    }

    private fun card(p: Prepared, book: BookEntity, failure: String?, t: Translation?, s: SettingsSnapshot): CardEntity {
        val result = when {
            p.error != null -> DraftResult.Invalid(p.error)
            failure != null -> DraftResult.Invalid(failure)
            t == null -> DraftResult.Invalid("no translation returned")
            else -> draft(p.sentence, t, s.maxExcerptWords)
        }
        return when (result) {
            is DraftResult.Ok -> pendingCard(result.draft, book, s)
            is DraftResult.Invalid -> failedCard(p.mark, book, s, result.reason)
        }
    }

    private fun pendingCard(d: CardDraft, book: BookEntity, s: SettingsSnapshot) = CardEntity(
        uuid = UUID.randomUUID().toString(), bookId = book.id, sourceLanguage = isoLanguage(d.sourceLanguage) ?: book.language,
        targetLanguage = s.targetLanguage, word = d.word, lemma = d.lemma, grammar = d.grammar, excerptHtml = d.excerptHtml,
        translation = d.translation, meaning = d.meaning, sentenceHtml = d.sentenceHtml, sourceNote = d.sourceNote, widened = d.widened,
        invented = d.invented, elsewhereDeck = null, status = next(null, CardEvent.TRANSLATED), previousStatus = null,
        ankiNoteId = null, ankiMod = null, sentAt = null, error = null,
    )

    private fun failedCard(mark: MarkEntity, book: BookEntity, s: SettingsSnapshot, error: String) = CardEntity(
        uuid = UUID.randomUUID().toString(), bookId = book.id, sourceLanguage = book.language, targetLanguage = s.targetLanguage,
        word = mark.text.trim(), lemma = "", grammar = "", excerptHtml = "", translation = "", meaning = "", sentenceHtml = "",
        sourceNote = "", widened = false, invented = false, elsewhereDeck = null, status = next(null, CardEvent.TRANSLATION_FAILED),
        previousStatus = null, ankiNoteId = null, ankiMod = null, sentAt = null, error = error,
    )

    private class Prepared(val mark: MarkEntity, val sentence: SentenceResult, val item: TranslationItem, val error: String? = null)

    private fun internalError(e: Exception) = "internal error: " + (e.message ?: e::class.simpleName)

    private inner class Run(private val source: BookSource) {
        private val books = HashMap<Long, BookEntity>()
        private val texts = HashMap<Long, BookText?>()
        private val normalized = HashMap<Long, List<NormalizedText>>()

        suspend fun book(id: Long): BookEntity = books.getOrPut(id) { checkNotNull(db.books().byId(id)) { "no book $id" } }

        suspend fun prepare(mark: MarkEntity): Prepared {
            val text = text(mark.bookId) // outside the guard: an unlistable books folder must fail the import
            val book = book(mark.bookId)
            fun item(sentence: String?) = TranslationItem(mark.id.toString(), mark.text, sentence, book.title, book.language)
            return try {
                val sentence = if (text == null) {
                    SentenceResult.NotFound
                } else {
                    val locale = book.language?.let(Locale::forLanguageTag) ?: Locale.ROOT
                    val chapters = normalized[mark.bookId]
                        ?: withContext(io) { text.chapters.map(::normalize) }.also { normalized[mark.bookId] = it }
                    withContext(io) { findSentence(chapters, text.chapters, mark.chapter, mark.position, mark.text, locale) }
                }
                Prepared(mark, sentence, item((sentence as? SentenceResult.Found)?.let { normalize(it.sentence).text }))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Prepared(mark, SentenceResult.NotFound, item(null), internalError(e))
            }
        }

        suspend fun learnLanguage(bookId: Long, t: Translation) {
            if (book(bookId).language == null) isoLanguage(t.sourceLanguage)?.let { setLanguage(bookId, it) }
        }

        private suspend fun text(bookId: Long): BookText? {
            if (bookId in texts) return texts[bookId]
            val book = book(bookId)
            val text = withContext(io) { source.text(book.relativePath ?: book.fileKey) }
            texts[bookId] = text
            if (book.language == null) isoLanguage(text?.language)?.let { setLanguage(bookId, it) }
            if (book.author == null) text?.author?.takeIf { it.isNotBlank() }?.let { setAuthor(bookId, it) }
            return text
        }

        private suspend fun setAuthor(bookId: Long, author: String) {
            db.books().setAuthorIfMissing(bookId, author)
            books[bookId] = book(bookId).copy(author = author)
        }

        private suspend fun setLanguage(bookId: Long, language: String) {
            db.books().setLanguageIfMissing(bookId, language)
            books[bookId] = book(bookId).copy(language = language)
        }
    }

    private companion object {
        const val BATCH_SIZE = 10
    }
}

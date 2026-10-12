package org.lattenwald.moonvocab.domain.import

import android.util.Log
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.lattenwald.moonvocab.data.anki.AnkiAvailability
import org.lattenwald.moonvocab.data.anki.AnkiGateway
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
import org.lattenwald.moonvocab.domain.card.KnownCard
import org.lattenwald.moonvocab.domain.card.Merge
import org.lattenwald.moonvocab.domain.card.NewCard
import org.lattenwald.moonvocab.domain.card.SenseJudge
import org.lattenwald.moonvocab.domain.card.Translation
import org.lattenwald.moonvocab.domain.card.TranslationItem
import org.lattenwald.moonvocab.domain.card.Translator
import org.lattenwald.moonvocab.domain.card.buildDraft
import org.lattenwald.moonvocab.domain.card.next
import org.lattenwald.moonvocab.domain.card.resolveMerges
import org.lattenwald.moonvocab.domain.card.senseQuestions
import org.lattenwald.moonvocab.domain.mark.RawMark
import org.lattenwald.moonvocab.domain.mark.StyleCombo
import org.lattenwald.moonvocab.domain.mark.fileKey
import org.lattenwald.moonvocab.domain.text.MarkLocator
import org.lattenwald.moonvocab.domain.text.NoteRef
import org.lattenwald.moonvocab.domain.text.SentenceResult
import org.lattenwald.moonvocab.domain.text.normalize
import org.lattenwald.moonvocab.domain.text.sentenceAt

data class ImportRequest(val backup: File?, val styles: Set<StyleCombo>, val onlyBookFileKey: String?, val sendToAnki: Boolean)

data class ImportProgress(val done: Int, val total: Int, val bookTitle: String?)

data class ImportSummary(
    val newCards: Int,
    val failed: Int,
    val alreadyCard: Int,
    val skippedDiscarded: Int,
    val senseFailed: Int,
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
    private val judge: SenseJudge,
    private val anki: AnkiGateway,
    private val sender: CardSender,
    private val check: AnkiCheck,
    private val settings: Settings,
    private val io: CoroutineDispatcher,
    private val draft: (SentenceResult, Translation, Int) -> DraftResult = ::buildDraft,
) {
    suspend fun run(request: ImportRequest, progress: (ImportProgress) -> Unit): ImportSummary {
        var incomplete: String? = null
        var notes = emptyMap<String, List<NoteRef>>()
        if (request.backup != null) {
            when (val contents = withContext(io) { reader.read(request.backup) }) {
                is BackupContents.Incomplete -> incomplete = contents.reason
                is BackupContents.Complete -> {
                    store(contents.marks, request)
                    notes = contents.marks.groupBy({ it.fileKey }, { NoteRef(it.chapter, it.position, it.text) })
                }
            }
        }
        val s = settings.current()
        val run = Run(books(), notes)
        val todo = db.marks().withoutCard(null)
            .filter { request.onlyBookFileKey == null || run.book(it.bookId).fileKey == request.onlyBookFileKey }

        var done = 0
        var newCards = 0
        var failed = 0
        var alreadyCard = 0
        var skippedDiscarded = 0
        var senseFailed = 0
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

            val built = prepared.map { p ->
                val book = run.book(p.mark.bookId)
                val t = translations[p.item.id]
                val card = try {
                    card(p, book, failure, t, s)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failedCard(p.mark, book, s, internalError(e))
                }
                Built(p, card, t?.focus)
            }
            val merged = try {
                merge(built)
            } catch (e: LlmException.Stop) {
                stopped = e.kind
                break
            }
            val cards = search(merged.cards, run)
            val ids = db.writeBatch(cards, merged.links)
            newCards += cards.count { it.first.status == CardStatus.PENDING }
            failed += cards.count { it.first.status == CardStatus.FAILED }
            alreadyCard += merged.alreadyCard
            skippedDiscarded += merged.skippedDiscarded
            if (merged.senseFailed) senseFailed++

            if (request.sendToAnki && sendError == null) {
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
        return ImportSummary(newCards, failed, alreadyCard, skippedDiscarded, senseFailed, sent, incomplete, stopped, sendError)
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
                color = m.combo.color, markedAt = m.markedAt, cardId = null, split = m.split,
            )
        }
        db.marks().insertIgnoringKnown(entities)
        for (e in entities) if (e.split != 0) db.marks().setSplit(e.key, e.split)
    }

    private suspend fun merge(built: List<Built>): Merged {
        val batch = built.mapIndexedNotNull { i, b ->
            val focus = b.focus ?: return@mapIndexedNotNull null
            if (b.card.status != CardStatus.PENDING) return@mapIndexedNotNull null
            NewCard(i, b.card.sourceLanguage, b.card.lemma, b.card.word, focus, b.prepared.item.sentence, b.card.translation, b.card.meaning)
        }
        val known = batch.map { it.language }.distinct().flatMap { db.cards().senseCandidates(it) }
            .map { KnownCard(it.id, it.status, it.sourceLanguage, it.lemma, it.word, it.translation, it.meaning) }
        val questions = senseQuestions(batch, known)
        var senseFailed = false
        val answers = if (questions.isEmpty()) {
            emptyMap()
        } else {
            try {
                judge.judge(questions)
            } catch (e: LlmException.Failed) {
                Log.w(TAG, "Sense check failed", e)
                senseFailed = true
                emptyMap()
            }
        }
        val merges = resolveMerges(batch, answers, known)

        val markIds = built.map { mutableListOf(it.prepared.mark.id) }
        val links = mutableListOf<Pair<Long, Long>>()
        var alreadyCard = 0
        var skippedDiscarded = 0
        for ((i, merge) in merges) {
            val markId = built[i].prepared.mark.id
            when (merge) {
                Merge.New -> Unit
                is Merge.IntoBatch -> {
                    markIds[merge.index] += markId
                    alreadyCard++
                }
                is Merge.IntoKnown -> {
                    links += markId to merge.cardId
                    when (merge.status) {
                        CardStatus.PENDING, CardStatus.SENT -> alreadyCard++
                        CardStatus.DISCARDED, CardStatus.DELETED_IN_ANKI -> skippedDiscarded++
                        CardStatus.FAILED -> Unit
                    }
                }
            }
        }
        val kept = built.indices.filter { (merges[it] ?: Merge.New) == Merge.New }.map { built[it] to markIds[it].toList() }
        return Merged(kept, links, alreadyCard, skippedDiscarded, senseFailed)
    }

    private suspend fun search(cards: List<Pair<Built, List<Long>>>, run: Run): List<Pair<CardEntity, List<Long>>> {
        val searchable = cards.any { it.first.card.status == CardStatus.PENDING } && run.ankiReady()
        return cards.map { (b, markIds) ->
            val card = if (searchable && b.card.status == CardStatus.PENDING) b.card.copy(elsewhereDeck = elsewhere(b)) else b.card
            card to markIds
        }
    }

    private suspend fun elsewhere(b: Built): String? {
        val lemma = b.card.lemma
        val terms = listOfNotNull(lemma, b.focus?.takeUnless { it.equals(lemma, ignoreCase = true) }).filter { it.isNotBlank() }
        if (terms.isEmpty()) return null
        return try {
            withContext(io) { anki.findElsewhere(terms) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "AnkiDroid search failed", e)
            null
        }
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

    private class Built(val prepared: Prepared, val card: CardEntity, val focus: String?)

    private class Merged(
        val cards: List<Pair<Built, List<Long>>>,
        val links: List<Pair<Long, Long>>,
        val alreadyCard: Int,
        val skippedDiscarded: Int,
        val senseFailed: Boolean,
    )

    private class LoadedBook(val text: BookText?, var locator: MarkLocator? = null)

    private fun internalError(e: Exception) = "internal error: " + (e.message ?: e::class.simpleName)

    private inner class Run(private val source: BookSource, private val notes: Map<String, List<NoteRef>>) {
        private val books = HashMap<Long, BookEntity>()
        private val loaded = object : LinkedHashMap<Long, LoadedBook>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, LoadedBook>) = size > BOOKS_IN_MEMORY
        }
        private var availability: AnkiAvailability? = null

        suspend fun ankiReady(): Boolean =
            (availability ?: withContext(io) { anki.availability() }.also { availability = it }) == AnkiAvailability.READY

        suspend fun book(id: Long): BookEntity = books.getOrPut(id) { checkNotNull(db.books().byId(id)) { "no book $id" } }

        suspend fun prepare(mark: MarkEntity): Prepared {
            val loadedBook = load(mark.bookId) // outside the guard: an unlistable books folder must fail the import
            val text = loadedBook.text
            val book = book(mark.bookId)
            fun item(sentence: String?) = TranslationItem(mark.id.toString(), mark.text, sentence, book.title, book.language)
            return try {
                val sentence = if (text == null) {
                    SentenceResult.NotFound
                } else {
                    val locale = book.language?.let(Locale::forLanguageTag) ?: Locale.ROOT
                    val locator = loadedBook.locator ?: withContext(io) {
                        MarkLocator(text.chapters.map(::normalize), notes[book.fileKey].orEmpty(), text.chaptersMatchMoonReader)
                    }.also { loadedBook.locator = it }
                    withContext(io) {
                        locator.locate(mark.chapter, mark.position, mark.text)
                            ?.let { sentenceAt(text.chapters, locator.chapters, it, locale) } ?: SentenceResult.NotFound
                    }
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

        private suspend fun load(bookId: Long): LoadedBook {
            loaded[bookId]?.let { return it }
            val book = book(bookId)
            val text = withContext(io) { source.text(book.relativePath ?: book.fileKey) }
            val loadedBook = LoadedBook(text).also { loaded[bookId] = it }
            if (book.language == null) isoLanguage(text?.language)?.let { setLanguage(bookId, it) }
            if (book.author == null) text?.author?.takeIf { it.isNotBlank() }?.let { setAuthor(bookId, it) }
            return loadedBook
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
        const val BOOKS_IN_MEMORY = 3
        const val TAG = "ImportPipeline"
    }
}

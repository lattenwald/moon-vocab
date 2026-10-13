package org.lattenwald.moonvocab.domain.cardpage

import org.lattenwald.moonvocab.data.anki.AnkiAvailability
import org.lattenwald.moonvocab.data.db.CardEntity
import org.lattenwald.moonvocab.data.llm.LlmException
import org.lattenwald.moonvocab.domain.card.CardExtras
import org.lattenwald.moonvocab.domain.card.CardField
import org.lattenwald.moonvocab.domain.card.CardFields
import org.lattenwald.moonvocab.domain.card.CardStatus
import org.lattenwald.moonvocab.domain.card.FormResult
import org.lattenwald.moonvocab.domain.card.OpenResult
import org.lattenwald.moonvocab.domain.card.SaveResult
import org.lattenwald.moonvocab.domain.card.cardFields
import org.lattenwald.moonvocab.domain.card.mergeAfterConflict
import org.lattenwald.moonvocab.domain.card.parseForm
import org.lattenwald.moonvocab.domain.card.toForm
import org.lattenwald.moonvocab.domain.card.with
import org.lattenwald.moonvocab.domain.import.DeleteResult
import org.lattenwald.moonvocab.domain.import.SendResult
import org.lattenwald.moonvocab.domain.import.UndoResult

sealed interface CardSource {
    data object Fresh : CardSource
    data class Unavailable(val availability: AnkiAvailability) : CardSource
    data class ReadFailed(val message: String) : CardSource
    data object Deleted : CardSource
}

enum class CardDialog { None, ConfirmDelete, ConfirmLeave }

enum class Op { OPEN, LOCAL_SAVE, SAVE, SEND, DISCARD, RESTORE, DELETE, TRANSLATE, FORGET, UNDO }

enum class AfterLocalSave { STAY, SEND, LEAVE, DISCARD }

val NO_FIELDS = CardFields("", "", "", "", "", "", "")

val EDITABLE = setOf(CardStatus.PENDING, CardStatus.SENT)

data class CardEditState(
    val card: CardEntity? = null,
    val source: CardSource = CardSource.Fresh,
    val baseline: CardFields = NO_FIELDS,
    val form: CardFields = NO_FIELDS,
    val extras: CardExtras? = null,
    val editing: Boolean = false,
    val invalid: Set<CardField> = emptySet(),
    val running: Op? = Op.OPEN,
    val queued: List<CardIntent> = emptyList(),
    val importRunning: Boolean = false,
    val dialog: CardDialog = CardDialog.None,
    val closed: Boolean = false,
) {
    val busy: Boolean get() = running != null
    val dirty: Boolean get() = extras != null || form != baseline.toForm()
    val parsed: FormResult get() = parseForm(form, baseline)
}

sealed interface CardMessage {
    data object Sent : CardMessage
    data object Discarded : CardMessage
    data object Saved : CardMessage
    data class Conflict(val fields: Set<CardField>) : CardMessage
    data object NoteGone : CardMessage
    data object NotPending : CardMessage
    data object Deleted : CardMessage
    data object DeleteRefused : CardMessage
    data object Retranslated : CardMessage
    data class Stopped(val kind: LlmException.Stop.Kind) : CardMessage
    data class Unavailable(val availability: AnkiAvailability) : CardMessage
    data class SendFailed(val message: String) : CardMessage
    data class MissingFields(val fields: List<String>) : CardMessage
    data class UndoKept(val count: Int) : CardMessage
    data class Failed(val message: String) : CardMessage
}

sealed interface CardEffect {
    data class Open(val after: AfterLocalSave?) : CardEffect
    data class SaveLocal(val fields: CardFields, val extras: CardExtras?, val then: AfterLocalSave) : CardEffect
    data class Save(val baseline: CardFields, val fields: CardFields, val extras: CardExtras?) : CardEffect
    data object Send : CardEffect
    data object Discard : CardEffect
    data object Restore : CardEffect
    data object Delete : CardEffect
    data object Retranslate : CardEffect
    data object CancelRetranslate : CardEffect
    data object Forget : CardEffect
    data object StartImport : CardEffect
    data object UndoSend : CardEffect
}

sealed interface CardIntent {
    data class Edit(val field: CardField, val value: String) : CardIntent
    data object StartEditing : CardIntent
    data object Done : CardIntent
    data object Send : CardIntent
    data object SendAsNew : CardIntent
    data object Discard : CardIntent
    data object Restore : CardIntent
    data object Save : CardIntent
    data object AskDelete : CardIntent
    data object ConfirmDelete : CardIntent
    data object DismissDialog : CardIntent
    data object TranslateAgain : CardIntent
    data object Back : CardIntent
    data object ConfirmLeave : CardIntent
    data class Undo(val message: CardMessage) : CardIntent
    data object Resumed : CardIntent
    data class ImportRunning(val running: Boolean) : CardIntent

    data class Opened(val result: OpenResult, val after: AfterLocalSave?) : CardIntent
    data class LocalSaved(val card: CardEntity?, val then: AfterLocalSave) : CardIntent
    data class SaveDone(val result: SaveResult) : CardIntent
    data class SendDone(val result: SendResult) : CardIntent
    data class DiscardDone(val changed: Boolean) : CardIntent
    data object RestoreDone : CardIntent
    data class DeleteDone(val result: DeleteResult) : CardIntent
    data class Retranslated(val result: RetranslateResult) : CardIntent
    data class Forgotten(val count: Int) : CardIntent
    data class UndoDone(val result: UndoResult) : CardIntent
    data class Failed(val effect: CardEffect, val message: String) : CardIntent
}

data class CardStep(val state: CardEditState, val effects: List<CardEffect> = emptyList(), val message: CardMessage? = null)

val CardMessage.undoable: Boolean get() = this == CardMessage.Sent || this == CardMessage.Discarded

fun reduce(s: CardEditState, intent: CardIntent): CardStep = when (intent) {
    is CardIntent.Edit -> if (s.busy) CardStep(s) else CardStep(s.withForm(s.form.with(intent.field, intent.value)))
    CardIntent.StartEditing -> CardStep(if (s.card?.status in EDITABLE) s.copy(editing = true) else s)
    CardIntent.Done -> done(s)
    CardIntent.Send -> send(s)
    CardIntent.SendAsNew -> start(s, Op.SEND, CardEffect.Send)
    CardIntent.Discard -> discard(s)
    CardIntent.Restore -> start(s, Op.RESTORE, CardEffect.Restore)
    CardIntent.Save -> save(s)
    CardIntent.AskDelete -> CardStep(s.copy(dialog = CardDialog.ConfirmDelete))
    CardIntent.ConfirmDelete -> start(s.copy(dialog = CardDialog.None), Op.DELETE, CardEffect.Delete)
    CardIntent.DismissDialog -> CardStep(s.copy(dialog = CardDialog.None))
    CardIntent.TranslateAgain -> translateAgain(s)
    CardIntent.Back -> back(s)
    CardIntent.ConfirmLeave -> CardStep(s.copy(dialog = CardDialog.None, closed = true))
    is CardIntent.Undo -> undo(s, intent)
    CardIntent.Resumed -> resumed(s)
    is CardIntent.ImportRunning -> CardStep(s.copy(importRunning = intent.running))

    is CardIntent.Opened -> opened(s, intent.result, intent.after)
    is CardIntent.LocalSaved -> localSaved(s, intent.card, intent.then)
    is CardIntent.SaveDone -> saveDone(s, intent.result)
    is CardIntent.SendDone -> CardStep(s.copy(editing = false), listOf(CardEffect.Open(null)), sendMessage(intent.result))
    is CardIntent.DiscardDone -> CardStep(s, listOf(CardEffect.Open(null)), CardMessage.Discarded.takeIf { intent.changed })
    CardIntent.RestoreDone -> CardStep(s, listOf(CardEffect.Open(null)))
    is CardIntent.DeleteDone -> CardStep(s, listOf(CardEffect.Open(null)), deleteMessage(intent.result))
    is CardIntent.Retranslated -> if (s.running != Op.TRANSLATE) CardStep(s) else retranslated(s, intent.result)
    is CardIntent.Forgotten ->
        if (intent.count == 0) CardStep(s, listOf(CardEffect.Open(null))) else finish(s.copy(closed = true), effects = listOf(CardEffect.StartImport))
    is CardIntent.UndoDone -> CardStep(s, listOf(CardEffect.Open(null)), undoMessage(intent.result))
    is CardIntent.Failed -> {
        val leaving = (intent.effect as? CardEffect.SaveLocal)?.then == AfterLocalSave.LEAVE
        val message = if (intent.effect == CardEffect.Send) CardMessage.SendFailed(intent.message) else CardMessage.Failed(intent.message)
        finish(if (leaving) s.copy(dialog = CardDialog.ConfirmLeave) else s, message)
    }
}

private fun start(s: CardEditState, op: Op, effect: CardEffect): CardStep =
    if (s.busy) CardStep(s) else CardStep(s.copy(running = op), listOf(effect))

private fun finish(s: CardEditState, message: CardMessage? = null, effects: List<CardEffect> = emptyList()): CardStep {
    val idle = s.copy(running = null)
    val next = idle.queued.firstOrNull() ?: return CardStep(idle, effects, message)
    val step = reduce(idle.copy(queued = idle.queued.drop(1)), next)
    return CardStep(step.state, effects + step.effects, message ?: step.message)
}

private fun queue(s: CardEditState, intent: CardIntent) = CardStep(s.copy(queued = s.queued + intent))

private fun CardEditState.withForm(form: CardFields): CardEditState {
    val s = copy(form = form)
    return s.copy(invalid = (s.parsed as? FormResult.Invalid)?.fields.orEmpty())
}

private fun CardEditState.showing(card: CardEntity, source: CardSource = this.source): CardEditState {
    val fields = card.cardFields()
    return copy(
        card = card, source = source, baseline = fields, form = fields.toForm(), extras = null,
        editing = editing && card.status in EDITABLE, invalid = emptySet(),
    )
}

private fun done(s: CardEditState): CardStep {
    if (s.busy) return CardStep(s)
    val fields = when (val parsed = s.parsed) {
        is FormResult.Invalid -> return CardStep(s.copy(invalid = parsed.fields))
        is FormResult.Ok -> parsed.fields
    }
    if (s.card?.status != CardStatus.PENDING || !s.dirty) return CardStep(s.copy(editing = false, invalid = emptySet()))
    return start(s, Op.LOCAL_SAVE, CardEffect.SaveLocal(fields, s.extras, AfterLocalSave.STAY))
}

private fun send(s: CardEditState): CardStep {
    if (!s.dirty) return start(s, Op.SEND, CardEffect.Send)
    return localSaveThen(s, AfterLocalSave.SEND)
}

private fun discard(s: CardEditState): CardStep {
    if (s.card?.status != CardStatus.PENDING || !s.dirty) return start(s, Op.DISCARD, CardEffect.Discard)
    return localSaveThen(s, AfterLocalSave.DISCARD)
}

private fun localSaveThen(s: CardEditState, then: AfterLocalSave): CardStep {
    if (s.busy) return CardStep(s)
    return when (val parsed = s.parsed) {
        is FormResult.Invalid -> CardStep(s.copy(editing = true, invalid = parsed.fields))
        is FormResult.Ok -> start(s, Op.LOCAL_SAVE, CardEffect.SaveLocal(parsed.fields, s.extras, then))
    }
}

private fun save(s: CardEditState): CardStep {
    if (s.busy) return CardStep(s)
    return when (val parsed = s.parsed) {
        is FormResult.Invalid -> CardStep(s.copy(editing = true, invalid = parsed.fields))
        is FormResult.Ok -> start(s, Op.SAVE, CardEffect.Save(s.baseline, parsed.fields, s.extras))
    }
}

private fun translateAgain(s: CardEditState): CardStep = when (s.card?.status) {
    CardStatus.FAILED -> if (s.importRunning) CardStep(s) else start(s, Op.FORGET, CardEffect.Forget)
    CardStatus.PENDING, CardStatus.SENT -> start(s, Op.TRANSLATE, CardEffect.Retranslate)
    else -> CardStep(s)
}

private fun back(s: CardEditState): CardStep {
    // Leaving doesn't wait for a translation; other actions are short.
    if (s.running == Op.TRANSLATE) {
        val step = back(s.copy(running = null))
        return step.copy(effects = listOf(CardEffect.CancelRetranslate) + step.effects)
    }
    if (s.busy) return queue(s, CardIntent.Back)
    if (!s.dirty) return CardStep(s.copy(closed = true))
    val fields = (s.parsed as? FormResult.Ok)?.fields
    if (s.card?.status != CardStatus.PENDING || fields == null) return CardStep(s.copy(dialog = CardDialog.ConfirmLeave))
    return start(s, Op.LOCAL_SAVE, CardEffect.SaveLocal(fields, s.extras, AfterLocalSave.LEAVE))
}

private fun undo(s: CardEditState, intent: CardIntent.Undo): CardStep {
    if (!intent.message.undoable) return CardStep(s)
    if (s.busy) return queue(s, intent)
    return when (intent.message) {
        CardMessage.Sent -> start(s, Op.UNDO, CardEffect.UndoSend)
        else -> start(s, Op.RESTORE, CardEffect.Restore)
    }
}

private fun resumed(s: CardEditState): CardStep {
    val unread = s.source is CardSource.Unavailable || s.source is CardSource.ReadFailed
    return if (unread) start(s, Op.OPEN, CardEffect.Open(null)) else CardStep(s)
}

private fun opened(s: CardEditState, result: OpenResult, after: AfterLocalSave?): CardStep {
    val (card, source) = when (result) {
        is OpenResult.Fresh -> result.card to CardSource.Fresh
        is OpenResult.Local -> result.card to when {
            result.unavailable != null -> CardSource.Unavailable(result.unavailable)
            result.error != null -> CardSource.ReadFailed(result.error)
            else -> CardSource.Fresh
        }
        is OpenResult.Deleted -> result.card to CardSource.Deleted
        OpenResult.Gone -> return finish(s.copy(closed = true))
    }
    val editable = card.status in EDITABLE
    val shown = if (editable && s.dirty) {
        val fresh = card.cardFields()
        s.copy(card = card, source = source, baseline = fresh).withForm(mergeAfterConflict(s.form, s.baseline, fresh, emptySet()))
    } else {
        s.showing(card, source)
    }
    if (after == null) return finish(shown)
    if (!editable) return finish(shown, CardMessage.NotPending)
    return finish(shown.copy(editing = true, dialog = if (after == AfterLocalSave.LEAVE) CardDialog.ConfirmLeave else shown.dialog))
}

private fun localSaved(s: CardEditState, card: CardEntity?, then: AfterLocalSave): CardStep {
    if (card == null) return CardStep(s, listOf(CardEffect.Open(then)))
    val shown = s.showing(card).copy(editing = false)
    return when (then) {
        AfterLocalSave.STAY -> finish(shown)
        AfterLocalSave.SEND -> CardStep(shown.copy(running = Op.SEND), listOf(CardEffect.Send))
        AfterLocalSave.DISCARD -> CardStep(shown.copy(running = Op.DISCARD), listOf(CardEffect.Discard))
        AfterLocalSave.LEAVE -> finish(shown.copy(closed = true))
    }
}

private fun saveDone(s: CardEditState, r: SaveResult): CardStep = when (r) {
    is SaveResult.Saved -> finish(s.showing(r.card).copy(editing = false), CardMessage.Saved)
    is SaveResult.Conflict -> {
        val fresh = r.card.cardFields()
        finish(s.copy(card = r.card, baseline = fresh).withForm(mergeAfterConflict(s.form, s.baseline, fresh, r.fields)), CardMessage.Conflict(r.fields))
    }
    is SaveResult.Deleted -> finish(s.showing(r.card, CardSource.Deleted).copy(editing = false), CardMessage.NoteGone)
    is SaveResult.Unavailable -> finish(s.copy(source = CardSource.Unavailable(r.availability)), CardMessage.Unavailable(r.availability))
    is SaveResult.Failed ->
        finish(s, if (r.missingFields.isNotEmpty()) CardMessage.MissingFields(r.missingFields) else CardMessage.Failed(r.message))
}

private fun retranslated(s: CardEditState, r: RetranslateResult): CardStep = when (r) {
    is RetranslateResult.Ok -> {
        val t = r.retranslation
        val form = s.form.copy(word = t.word, transcription = t.transcription, grammar = t.grammar, translation = t.translation, meaning = t.meaning)
        finish(s.copy(extras = t.extras, editing = true).withForm(form), CardMessage.Retranslated)
    }
    is RetranslateResult.Stopped -> finish(s, CardMessage.Stopped(r.kind))
    is RetranslateResult.Failed -> finish(s, CardMessage.Failed(r.message))
}

private fun sendMessage(r: SendResult): CardMessage? = when {
    r.unavailable != null -> CardMessage.Unavailable(r.unavailable)
    r.sentIds.isNotEmpty() -> CardMessage.Sent
    r.missingFields.isNotEmpty() -> CardMessage.MissingFields(r.missingFields)
    r.error != null -> CardMessage.SendFailed(r.error)
    else -> null
}

private fun deleteMessage(r: DeleteResult): CardMessage? = when (r) {
    DeleteResult.Deleted -> CardMessage.Deleted
    DeleteResult.NotSent -> null
    DeleteResult.Refused -> CardMessage.DeleteRefused
    is DeleteResult.Unavailable -> CardMessage.Unavailable(r.availability)
    is DeleteResult.Failed -> CardMessage.Failed(r.message)
}

private fun undoMessage(r: UndoResult): CardMessage? = when {
    r.unavailable != null -> CardMessage.Unavailable(r.unavailable)
    r.kept > 0 -> CardMessage.UndoKept(r.kept)
    else -> null
}

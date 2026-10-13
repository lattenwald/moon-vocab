package org.lattenwald.moonvocab.domain.cardpage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lattenwald.moonvocab.data.anki.AnkiAvailability
import org.lattenwald.moonvocab.data.db.CardEntity
import org.lattenwald.moonvocab.domain.card.CardExtras
import org.lattenwald.moonvocab.domain.card.CardField
import org.lattenwald.moonvocab.domain.card.CardStatus
import org.lattenwald.moonvocab.domain.card.OpenResult
import org.lattenwald.moonvocab.domain.card.SaveResult
import org.lattenwald.moonvocab.domain.card.cardFields

private fun card(status: CardStatus) = CardEntity(
    id = 1, uuid = "u", bookId = 1, sourceLanguage = "de", targetLanguage = "en", word = "Haus", lemma = "Haus", grammar = "n",
    excerptHtml = "", translation = "house", meaning = "a building", sentenceHtml = "", sourceNote = "", widened = false, invented = false,
    elsewhereDeck = null, status = status, previousStatus = null, ankiNoteId = if (status == CardStatus.SENT) 7 else null, ankiMod = null,
    sentAt = null, error = null,
)

private val pending = card(CardStatus.PENDING)
private val sent = card(CardStatus.SENT)

private fun opened(card: CardEntity, result: OpenResult = OpenResult.Fresh(card)): CardEditState =
    reduce(CardEditState(), CardIntent.Opened(result, null)).state

private fun CardEditState.then(intent: CardIntent): CardStep = reduce(this, intent)

private fun CardEditState.edit(field: CardField, value: String): CardEditState = then(CardIntent.Edit(field, value)).state

class CardEditSessionTest {
    @Test fun editsAreRefusedWhileAnActionRuns() {
        val saving = opened(sent).edit(CardField.TRANSLATION, "home").then(CardIntent.Save).state
        val typed = saving.edit(CardField.TRANSLATION, "homes")
        assertEquals("home", typed.form.translation)

        val saved = typed.then(CardIntent.SaveDone(SaveResult.Saved(sent.copy(translation = "home")))).state
        assertEquals("home", saved.form.translation)
        assertFalse(saved.dirty)
    }

    @Test fun undoTappedDuringAnotherActionRunsWhenItEnds() {
        val translating = opened(sent).then(CardIntent.TranslateAgain).state
        val waiting = translating.then(CardIntent.Undo(CardMessage.Sent))
        assertEquals(emptyList<CardEffect>(), waiting.effects)

        val after = waiting.state.then(CardIntent.Retranslated(RetranslateResult.Failed("timeout")))
        assertEquals(listOf(CardEffect.UndoSend), after.effects)
        assertEquals(Op.UNDO, after.state.running)
        assertEquals(CardMessage.Failed("timeout"), after.message)
    }

    @Test fun discardSavesUnsavedEditsFirstSoRestoreBringsThemBack() {
        val edited = opened(pending).edit(CardField.TRANSLATION, "home")
        val discarding = edited.then(CardIntent.Discard)
        assertEquals(
            listOf(CardEffect.SaveLocal(pending.copy(translation = "home").cardFields(), null, AfterLocalSave.DISCARD)),
            discarding.effects,
        )

        val saved = discarding.state.then(CardIntent.LocalSaved(pending.copy(translation = "home"), AfterLocalSave.DISCARD))
        assertEquals(listOf(CardEffect.Discard), saved.effects)
    }

    @Test fun aReloadKeepsUnsavedEditsAndTakesTheRestFromTheStoredCard() {
        val edited = opened(sent, OpenResult.Local(sent, AnkiAvailability.NO_PERMISSION, null)).edit(CardField.TRANSLATION, "home")
        val rechecking = edited.then(CardIntent.Resumed)
        assertEquals(listOf(CardEffect.Open(null)), rechecking.effects)

        val changedInAnki = sent.copy(meaning = "where one lives")
        val reloaded = rechecking.state.then(CardIntent.Opened(OpenResult.Fresh(changedInAnki), null)).state
        assertEquals("home", reloaded.form.translation)
        assertEquals("where one lives", reloaded.form.meaning)
        assertEquals(changedInAnki.cardFields(), reloaded.baseline)
        assertEquals(CardSource.Fresh, reloaded.source)
    }

    @Test fun aConflictShowsAnkiDroidsTextForConflictingAndUntouchedFieldsAndKeepsOtherEdits() {
        val saving = opened(sent).edit(CardField.TRANSLATION, "home").edit(CardField.MEANING, "a dwelling").then(CardIntent.Save).state
        val fresh = sent.copy(translation = "residence", grammar = "n, -es")
        val step = saving.then(CardIntent.SaveDone(SaveResult.Conflict(fresh, setOf(CardField.TRANSLATION))))

        assertEquals("residence", step.state.form.translation)
        assertEquals("a dwelling", step.state.form.meaning)
        assertEquals("n, -es", step.state.form.grammar)
        assertEquals(CardMessage.Conflict(setOf(CardField.TRANSLATION)), step.message)
    }

    @Test fun backDuringTranslateAgainCancelsItAndSavesThePendingEdits() {
        val translating = opened(pending).edit(CardField.TRANSLATION, "home").then(CardIntent.TranslateAgain).state
        val leaving = translating.then(CardIntent.Back)
        assertEquals(
            listOf(CardEffect.CancelRetranslate, CardEffect.SaveLocal(pending.copy(translation = "home").cardFields(), null, AfterLocalSave.LEAVE)),
            leaving.effects,
        )

        val late = Retranslation("Haus", "", "n", "building", "", CardExtras("Haus", false))
        val ignored = leaving.state.then(CardIntent.Retranslated(RetranslateResult.Ok(late))).state
        assertEquals("home", ignored.form.translation)

        val left = ignored.then(CardIntent.LocalSaved(pending.copy(translation = "home"), AfterLocalSave.LEAVE)).state
        assertTrue(left.closed)
    }

    @Test fun backDuringASaveLeavesWhenTheSaveEnds() {
        val saving = opened(sent).edit(CardField.TRANSLATION, "home").then(CardIntent.Save).state
        val waiting = saving.then(CardIntent.Back)
        assertEquals(emptyList<CardEffect>(), waiting.effects)
        assertFalse(waiting.state.closed)

        val saved = waiting.state.then(CardIntent.SaveDone(SaveResult.Saved(sent.copy(translation = "home")))).state
        assertTrue(saved.closed)
    }

    @Test fun backOnAPendingCardSentMeanwhileKeepsTheEditsAndAsks() {
        val leaving = opened(pending).edit(CardField.TRANSLATION, "home").then(CardIntent.Back).state
        val notSaved = leaving.then(CardIntent.LocalSaved(null, AfterLocalSave.LEAVE))
        assertEquals(listOf(CardEffect.Open(AfterLocalSave.LEAVE)), notSaved.effects)

        val reloaded = notSaved.state.then(CardIntent.Opened(OpenResult.Fresh(sent), AfterLocalSave.LEAVE)).state
        assertEquals(CardDialog.ConfirmLeave, reloaded.dialog)
        assertEquals("home", reloaded.form.translation)
        assertTrue(reloaded.editing)
        assertFalse(reloaded.closed)
    }

    @Test fun editsToACardNoLongerEditableAreDroppedWithAMessage() {
        val saving = opened(pending).edit(CardField.TRANSLATION, "home").then(CardIntent.Done).state
        val notSaved = saving.then(CardIntent.LocalSaved(null, AfterLocalSave.STAY)).state
        val discarded = pending.copy(status = CardStatus.DISCARDED, previousStatus = CardStatus.PENDING)
        val step = notSaved.then(CardIntent.Opened(OpenResult.Local(discarded, null, null), AfterLocalSave.STAY))

        assertEquals(CardMessage.NotPending, step.message)
        assertFalse(step.state.dirty)
        assertFalse(step.state.busy)
    }

    @Test fun onlyACardWhoseNoteCouldNotBeReadIsReopenedOnResume() {
        assertEquals(emptyList<CardEffect>(), opened(sent).then(CardIntent.Resumed).effects)

        val unavailable = opened(sent).edit(CardField.TRANSLATION, "home").then(CardIntent.Save).state
            .then(CardIntent.SaveDone(SaveResult.Unavailable(AnkiAvailability.NOT_SET_UP))).state
        assertEquals(CardSource.Unavailable(AnkiAvailability.NOT_SET_UP), unavailable.source)
        assertEquals(listOf(CardEffect.Open(null)), unavailable.then(CardIntent.Resumed).effects)
    }
}

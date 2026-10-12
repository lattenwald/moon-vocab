# Moon+ Vocab milestone 4 (Cards and library) implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tapping a card opens its page. There the user reads both faces, edits any field and saves the change into the AnkiDroid note without losing its review history. They can also send, discard, restore, translate again, delete from AnkiDroid or preview the card. A Library tab lists the books, and each book page lists its cards. The app checks its AnkiDroid notes on start.

**Architecture:**
- Logic that can break lives in unit-tested code:
  - `domain/card/CardEdit.kt` (pure): the edit form with `[brackets]` for the focus, its validation, and the save plan that refuses to overwrite a field changed in AnkiDroid;
  - `domain/cardpage/CardEditor.kt`: the fresh read on open, save to the note, and local save of a pending card, all under the shared AnkiDroid lock;
  - `domain/cardpage/CardRetranslator.kt`: a new translation for one card, returned without writing;
  - `domain/library/Library.kt` (pure) and two new queries: the Library list and the book page's sections;
  - `domain/card/CardPreview.kt` (pure): the note type's templates filled with a card's fields;
  - `CardSender` gains send-as-new and delete-from-AnkiDroid, and `AnkiCheck` gains the app-start check.
- Navigation 3 replaces the tab state: three tabs, pushed pages for book, card and preview.
- The Compose layer stays thin and has no unit tests.

**Tech Stack:** Unchanged from milestone 3. New dependencies:
- `androidx.navigation3:navigation3-runtime` and `androidx.navigation3:navigation3-ui` `1.2.0`;
- `androidx.lifecycle:lifecycle-viewmodel-navigation3` at the existing `androidxLifecycle` version (`2.11.0`).

All three were checked as the latest stable on Google Maven on 2026-10-12.

**Spec:** [`docs/superpowers/specs/2026-10-11-moon-vocab-design.md`](../specs/2026-10-11-moon-vocab-design.md). Section numbers (§n) refer to it:
- §3.4 Card page;
- §3.5 Library and book page;
- §4.1 Note type;
- §5.6 Keeping in step with AnkiDroid;
- §5.8 the card status table;
- §6 UI implementation;
- §8 Errors.

Current decisions are in [`docs/DECISIONS.md`](../../DECISIONS.md).

**Layout and copy reference:** [`docs/design/prototype.html`](../../design/prototype.html).
- The card page is `PAGES.card`, the book page `PAGES.book`, the Library `renderLibrary`, and the preview `PAGES.anki`.
- Their CSS is under `.lrow`, `.bookhead`, `.face` and `.ankicard`.
- The English copy is in `T.en` and in `Object.assign(T.en, { x: {…} })`.

Read only the parts your task names.

## Decisions made for this milestone

The user said "go" without reviewing choices one by one. These are mine, and each is cheap to change:

1. **Actions on the card page keep you on the page.** After Send, Discard, Save or Restore, the page shows the card's new state, with a snackbar and Undo where the Inbox offers one. The prototype closes the page instead. Staying works the same from the Inbox and from a book page, and needs no messages between screens. This goes in `DEVIATIONS.md`.
2. **Editing.**
   - Edit mode turns the six fields into text fields: word, grammar, excerpt, translation, meaning and sentence. In the excerpt and the sentence, the focus is written in `[square brackets]`.
   - A pending card's edits are saved in the app when you tap Done or leave the page.
   - A sent card's edits wait for "Save changes to Anki".
3. **Save never overwrites AnkiDroid's newer text.** Save reads the note again and writes only the fields you changed. A field you changed that has also changed in AnkiDroid since the page opened is not written. The page then shows AnkiDroid's text for it and says so.
4. **Translate again.**
   - On a pending or sent card it asks for a new translation in place. The result fills word, grammar, translation and meaning as unsaved edits, following the spec's "same status" rows. Excerpt and sentence stay as they are.
   - On a failed card it works as in the Inbox: the card is forgotten, a resume import runs, and the page closes.
5. **Delete from AnkiDroid asks first.** The card becomes discarded, with Pending as its previous status, so Restore puts it back in the Inbox.
6. **Library and book page.**
   - The Library lists every book that has at least one card, grouped by language, newest mark first.
   - The book page shows: To review (pending and failed); In AnkiDroid (the latest 50 by send time); and a collapsed Discarded section that also holds cards deleted in AnkiDroid.
   - "Showing the latest 50. All N are in AnkiDroid." appears only when N is over 50.
7. **Preview** draws the note type's own front and back templates and CSS, filled with the card's fields, in a WebView. It has Show answer and no grading buttons. The prototype's buttons only cycled through cards. This goes in `DEVIATIONS.md`.
8. **Navigation.**
   - The back stack always starts with the Inbox. The other tabs sit on top of it, so Back from Library or Settings returns to the Inbox (ticket mv-gh2y).
   - The Inbox keeps its scroll position across tab switches (ticket mv-q291).
   - Both tickets close in Task 6.
9. **The app-start check** runs when `MainActivity` is created fresh and the last successful check was 10 minutes or more ago. Every successful check, including the one after an import, records its time.

## Global Constraints

Paths:
- Main code is under `app/src/main/java/org/lattenwald/moonvocab/`.
- Resources are under `app/src/main/res/`.
- Unit tests are under `app/src/test/java/org/lattenwald/moonvocab/`.

Repository:
- This milestone runs on `master` directly.
- Each task ends with one commit on `master`: `git add` the files it changed, then `git commit -m "<subject>"`. One-line subject, no attribution of any kind.

Unchanged from milestones 1–3:
- Room stays at schema version 1. Tasks add queries only. A task that finds it must change an entity stops and asks.
- `deckOverride` still defaults to `Moon+::Test`.
- Strings go in `values/strings.xml` only, with counts in `plurals`. Russian comes in milestone 5.
- No DI framework. IO dispatchers are passed in. `MainActivity` stays an `AppCompatActivity` with the AppCompat theme parent.
- The app touches only notes it created, addressed by `card.ankiNoteId` (§5.6). Fields are written by name through `AnkiGateway.update`, which passes other fields through.
- `CardSender`, `AnkiCheck`, `InboxActions` and the new `CardEditor` take the one `ankiLock` from `AppContainer`.

Fixed values:
- App-start check interval: 10 minutes (§5.6).
- Book page: the latest 50 cards in AnkiDroid (§3.5).
- Mark underline: `#D9480F` light and `#FF8A50` dark. In the preview it comes from the note type's CSS (`NoteTypeSpec.CSS`).

Repo rules (`AGENTS.md`):
- No test theater. Compose code has no unit tests. Its logic lives in the files listed under Architecture.
- Deferred work becomes a `tk` ticket.
- Ask before touching the phone.
- Edit files with the file-editing tools. Never use shell redirection, `sed -i` or scripts. Exception: `curl -o` icon downloads in Tasks 7 and 8.

`docs/DECISIONS.md` and `docs/DEVIATIONS.md`:
- Every task edits the DECISIONS lines its change makes untrue and adds the lines in its **Decisions** block.
- Tasks 7 and 9 add the DEVIATIONS entries for decisions 1 and 7 above.

Commands:
- `timeout --signal=KILL 540 make check` runs the unit tests and lint.
- `timeout --signal=KILL 540 ./gradlew :app:testDebugUnitTest --tests '<pattern>'` runs one class.
- `timeout --signal=KILL 540 make debug` checks that the app builds.

Icons:
- Material Symbols Rounded, fetched with `curl -sSfL -o res/drawable/ic_<name>.xml https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/<name>/materialsymbolsrounded/<name>_24px.xml`.
- Then remove the `android:tint` line with the Edit tool.

Out of scope:
- Milestone 5:
  - Settings pages (Settings stays the debug screen);
  - the card design preview in Settings;
  - Russian strings;
  - the share target;
  - predictive-back polish beyond what Navigation 3 gives;
  - first-run setup.
- Re-adopting notes after a reinstall (§5.8).

## Review Focus

1. **The user edits a field on a sent card's page while the same field changes in AnkiDroid**, by a desktop sync or AnkiDroid's own editor. Save must not overwrite AnkiDroid's text: that field is reported and shown fresh, and the other edits are kept. Tested in Task 2 (`planReportsAConflictOnAFieldChangedOnBothSides`) and Task 3 (`saveStopsWhenAnEditedFieldChangedInAnkiDroid`).
2. **The note is deleted in AnkiDroid before the page opens or before Save.** The page shows the deleted state and offers Send as new card, without a crash or an error toast, and Save doesn't lose the edits. Tested in Task 3 (`openMarksAMissingNoteDeletedInAnki`, `saveOnAMissingNoteKeepsTheEditsLocally`) and Task 1 (`sendAsNewSendsACardDeletedInAnki`).
3. **Restore after Delete from AnkiDroid.** The card goes back to the Inbox as pending. It never becomes a sent card without a note. Tested in Task 1 (`restoreAfterDeleteFromAnkiReturnsTheCardToPending`).
4. **An excerpt edit that loses the focus**: no brackets, two pairs, or empty brackets. Save stays disabled with the field marked. An excerpt left unchanged keeps its original HTML even when its text contains a literal `[`. Tested in Task 2.
5. **A pending card is sent while its page holds unsaved edits**, by an import in Send to Anki mode, say. The page's local save must not write over the now-sent card. Tested in Task 3 (`saveLocalRefusesACardThatIsNoLongerPending`).

---

### Task 1: Send as new, delete from AnkiDroid, app-start check

**Files:**
- Modify: `domain/import/CardSender.kt`, `domain/import/AnkiCheck.kt`, `AppContainer.kt`, `MainActivity.kt`
- Test: `domain/import/CardSenderTest.kt`, `domain/import/AnkiCheckTest.kt`, `domain/import/ImportTestBase.kt`

**Interfaces:**
- Produces:
  - `CardSender.send(cardIds)` now sends PENDING and DELETED_IN_ANKI cards among the ids. A PENDING card moves with `CardEvent.SEND`, a DELETED_IN_ANKI card with `CardEvent.SEND_AS_NEW`. `SendResult` is unchanged; `skipped` counts ids in any other status.
  - `data class DeleteResult(val deleted: Boolean, val unavailable: AnkiAvailability?, val error: String?)`.
  - `suspend fun CardSender.deleteFromAnki(cardId: Long): DeleteResult`. The card must be SENT with a note id. When AnkiDroid deletes the note, the card becomes `next(SENT, DELETE_FROM_ANKI)` (DISCARDED) with `previousStatus = PENDING`, and `ankiNoteId`, `sentAt` and `ankiMod` are cleared. Otherwise nothing changes. The save is `NonCancellable`, as in `send`.
  - `AnkiCheck(db, anki, lock: Mutex, settings: Settings, clock: () -> Long)`. A `check()` that reaches AnkiDroid and finishes writes `lastCheckAt = clock()` (the field already exists in `SettingsSnapshot`).
  - `suspend fun AnkiCheck.runIfDue(): Boolean` calls `run()` and returns `true` when `clock() - lastCheckAt >= START_INTERVAL_MS` (`10 * 60_000L`). Otherwise it returns `false` and does nothing.
  - `AppContainer.appScope: CoroutineScope` (`SupervisorJob() + io`).

- [ ] **Step 1: Write the failing tests**

```kotlin
// CardSenderTest
@Test fun sendAsNewSendsACardDeletedInAnki() = runTest {
    pipeline().run(req(backup(1)), {})
    val id = db.cards().byStatus(PENDING).single().id
    sender.send(listOf(id))
    fakeAnki.notes.clear()
    check.run()
    assertEquals(DELETED_IN_ANKI, db.cards().byIds(listOf(id)).single().status)
    val r = sender.send(listOf(id))
    assertEquals(listOf(id), r.sentIds)
    val card = db.cards().byIds(listOf(id)).single()
    assertEquals(SENT, card.status)
    assertEquals(fakeAnki.notes.keys.single(), card.ankiNoteId)
}
@Test fun deleteFromAnkiDeletesTheNoteAndDiscardsTheCard() = runTest {
    pipeline().run(req(backup(1)), {})
    val id = db.cards().byStatus(PENDING).single().id
    sender.send(listOf(id))
    assertEquals(DeleteResult(deleted = true, unavailable = null, error = null), sender.deleteFromAnki(id))
    val card = db.cards().byIds(listOf(id)).single()
    assertEquals(DISCARDED, card.status)
    assertEquals(PENDING, card.previousStatus)
    assertNull(card.ankiNoteId); assertNull(card.sentAt); assertNull(card.ankiMod)
    assertTrue(fakeAnki.notes.isEmpty())
}
@Test fun restoreAfterDeleteFromAnkiReturnsTheCardToPending() = runTest {
    pipeline().run(req(backup(1)), {})
    val id = db.cards().byStatus(PENDING).single().id
    sender.send(listOf(id))
    sender.deleteFromAnki(id)
    InboxActions(db, ankiLock).restore(listOf(id))
    assertEquals(PENDING, db.cards().byIds(listOf(id)).single().status)
}
@Test fun deleteFromAnkiKeepsTheCardWhenAnkiDroidRefuses() = runTest {
    pipeline().run(req(backup(1)), {})
    val id = db.cards().byStatus(PENDING).single().id
    sender.send(listOf(id))
    val before = db.cards().byIds(listOf(id)).single()
    fakeAnki.refuseDelete += before.ankiNoteId!!
    assertFalse(sender.deleteFromAnki(id).deleted)
    assertEquals(before, db.cards().byIds(listOf(id)).single())
}
@Test fun deleteFromAnkiChangesNothingWhileAnkiDroidIsUnavailable() = runTest {
    pipeline().run(req(backup(1)), {})
    val id = db.cards().byStatus(PENDING).single().id
    sender.send(listOf(id))
    fakeAnki.availability = AnkiAvailability.NO_PERMISSION
    assertEquals(DeleteResult(false, AnkiAvailability.NO_PERMISSION, null), sender.deleteFromAnki(id))
    assertEquals(SENT, db.cards().byIds(listOf(id)).single().status)
}

// AnkiCheckTest
private var now = T0
private val clocked by lazy { AnkiCheck(db, fakeAnki, ankiLock, settings) { now } }

@Test fun aFinishedCheckRecordsItsTime() = runTest {
    clocked.check()
    assertEquals(now, settings.current().lastCheckAt)
}
@Test fun anUnavailableCheckRecordsNoTime() = runTest {
    fakeAnki.availability = AnkiAvailability.NOT_INSTALLED
    clocked.check()
    assertEquals(0L, settings.current().lastCheckAt)
}
@Test fun runIfDueSkipsWithinTenMinutes() = runTest {
    sendOne()
    clocked.check()
    now += 10 * 60_000L - 1
    val reads = fakeAnki.reads
    assertFalse(clocked.runIfDue())
    assertEquals(reads, fakeAnki.reads)
}
@Test fun runIfDueRunsAfterTenMinutes() = runTest {
    sendOne()
    clocked.check()
    now += 10 * 60_000L
    val reads = fakeAnki.reads
    assertTrue(clocked.runIfDue())
    assertEquals(reads + 1, fakeAnki.reads)
    assertEquals(now, settings.current().lastCheckAt)
}
```

`sendOne()` imports one card and sends it, so that the check has a note to read. Write it as a helper in `AnkiCheckTest`.

- [ ] **Step 2: Run them and see them fail.** Run `./gradlew :app:testDebugUnitTest --tests '*CardSenderTest' --tests '*AnkiCheckTest'`.
- [ ] **Step 3: Implement.**
  - `send` selects with `db.cards().byIds(cardIds)` filtered to the two statuses, in id order.
  - `ImportTestBase.check` becomes `AnkiCheck(db, fakeAnki, ankiLock, settings) { NOW }`.
  - In `MainActivity.onCreate`, when `savedInstanceState == null`, launch `container.ankiCheck.runIfDue()` in `container.appScope`.
- [ ] **Step 4: Run `make check` and see it pass.**
- [ ] **Step 5: Update `docs/DECISIONS.md` and commit** with `feat: send as new, delete from AnkiDroid and the app-start check`.

  Decisions (section "Import pipeline", the `CardSender` and check lines):
  - `send` sends PENDING cards and cards deleted in AnkiDroid. Other ids count as skipped.
  - `deleteFromAnki` discards a sent card only when AnkiDroid deleted its note. Its previous status becomes PENDING.
  - A check that reaches AnkiDroid and finishes records `lastCheckAt`. When `MainActivity` is created fresh, the check runs if the last one is 10 minutes old or more.

---

### Task 2: Card edit model

**Files:**
- Create: `domain/card/CardEdit.kt`
- Test: `domain/card/CardEditTest.kt`

**Interfaces:**
- Consumes: `markedText`, `markHtml`, `singleBracketPair`, `escapeHtml`, `ankiToPlain`, `NoteTypeSpec.HTML_FIELDS`.
- Produces:

```kotlin
enum class CardField(val ankiName: String) {
    WORD("Word"), GRAMMAR("Grammar"), EXCERPT("Excerpt"), TRANSLATION("Translation"), MEANING("Meaning"), SENTENCE("Sentence")
}

// Stored form: excerpt and sentence are HTML with a mark span; the others are plain text.
data class CardFields(val word: String, val grammar: String, val excerptHtml: String, val translation: String, val meaning: String, val sentenceHtml: String) {
    operator fun get(f: CardField): String
}
data class CardExtras(val lemma: String, val widened: Boolean)

fun CardEntity.cardFields(): CardFields
fun CardEntity.withFields(f: CardFields, extras: CardExtras? = null): CardEntity

// Edit form: same shape, excerpt and sentence as text with the focus in [brackets].
fun CardFields.toForm(): CardFields

sealed interface FormResult {
    data class Ok(val fields: CardFields) : FormResult
    data class Invalid(val fields: Set<CardField>) : FormResult
}
fun parseForm(form: CardFields, original: CardFields): FormResult

sealed interface SavePlan {
    /** [anki]: AnkiDroid field name → HTML value, only the fields to write. [result]: what the card stores afterwards. */
    data class Write(val anki: Map<String, String>, val result: CardFields) : SavePlan
    data class Conflict(val fields: Set<CardField>, val fresh: CardFields) : SavePlan
}
fun planSave(baseline: CardFields, edited: CardFields, note: Map<String, String>): SavePlan
```

Rules:
- **`toForm`:**
  - Excerpt and sentence go through `markedText` and get `[` and `]` around the mark.
  - An empty value stays empty. A value with no mark stays as its text.
- **`parseForm`:**
  - A form value equal to `original.toForm()`'s value maps back to the original stored value unchanged. This keeps HTML the form can't show.
  - Changed plain fields are trimmed.
  - Word and translation must not be blank.
  - A changed excerpt must hold exactly one bracket pair (`singleBracketPair`). It becomes `markHtml(text without the brackets, open, close - 1)`, which HTML-escapes the text.
  - A changed sentence must be empty or hold exactly one pair, converted the same way.
  - Every failing field is listed in `Invalid`.
- **`planSave`:**
  - `fresh` reads the note: plain fields through `ankiToPlain`, Excerpt and Sentence as raw HTML, as `reconcile` does. A field missing from the note keeps the baseline value.
  - changed = fields where `edited != baseline`;
  - conflicting = changed fields whose `fresh` value differs from `baseline`.
  - If any field conflicts, the plan is `Conflict(conflicting, fresh)`.
  - Otherwise it is `Write`:
    - `anki` holds the changed fields: plain values through `escapeHtml`, HTML values as they are;
    - `result` is `fresh` with the changed fields overlaid.

- [ ] **Step 1: Write the failing tests**

```kotlin
private val stored = CardFields(
    word = "get the upper hand", grammar = "phr.",
    excerptHtml = """such an <span class="mark">upper hand</span> of me""",
    translation = "взять верх", meaning = "",
    sentenceHtml = """whenever my hypos get such an <span class="mark">upper hand</span> of me [sic]""",
)
private fun note(f: CardFields) = mapOf(
    "Word" to f.word, "Grammar" to f.grammar, "Excerpt" to f.excerptHtml, "Translation" to f.translation,
    "Meaning" to f.meaning, "Sentence" to f.sentenceHtml, "Book" to "Moby-Dick", "AppId" to "u1",
)

@Test fun formShowsTheFocusInBrackets() =
    assertEquals("such an [upper hand] of me", stored.toForm().excerptHtml)
@Test fun anUnchangedFormGivesBackTheStoredHtml() =
    assertEquals(FormResult.Ok(stored), parseForm(stored.toForm(), stored)) // the sentence's literal [sic] must not invalidate it
@Test fun bracketsBecomeTheMarkAndTypedHtmlIsEscaped() {
    val r = parseForm(stored.toForm().copy(excerptHtml = "a < b [upper hand]"), stored) as FormResult.Ok
    assertEquals("""a &lt; b <span class="mark">upper hand</span>""", r.fields.excerptHtml)
}
@Test fun anExcerptWithoutOneBracketPairIsInvalid() {
    for (bad in listOf("no focus", "[a] and [b]", "[ ] x", "a ]b[")) {
        assertEquals(FormResult.Invalid(setOf(CardField.EXCERPT)), parseForm(stored.toForm().copy(excerptHtml = bad), stored))
    }
}
@Test fun anEmptySentenceIsValid() =
    assertEquals("", (parseForm(stored.toForm().copy(sentenceHtml = ""), stored) as FormResult.Ok).fields.sentenceHtml)
@Test fun blankWordAndTranslationAreInvalid() = assertEquals(
    FormResult.Invalid(setOf(CardField.WORD, CardField.TRANSLATION)),
    parseForm(stored.toForm().copy(word = " ", translation = ""), stored),
)
@Test fun planWritesOnlyChangedFieldsEscaped() {
    val edited = stored.copy(translation = "одолеть <редк.>")
    assertEquals(
        SavePlan.Write(mapOf("Translation" to "одолеть &lt;редк.&gt;"), edited),
        planSave(stored, edited, note(stored)),
    )
}
@Test fun planKeepsAnkiEditsToUntouchedFields() {
    val edited = stored.copy(translation = "одолеть")
    val plan = planSave(stored, edited, note(stored.copy(meaning = "<b>to win</b>"))) as SavePlan.Write
    assertEquals(setOf("Translation"), plan.anki.keys)
    assertEquals("to win", plan.result.meaning)
    assertEquals("одолеть", plan.result.translation)
}
@Test fun planReportsAConflictOnAFieldChangedOnBothSides() {
    val edited = stored.copy(translation = "одолеть", grammar = "idiom")
    val inAnki = stored.copy(translation = "взять верх, одержать верх")
    assertEquals(
        SavePlan.Conflict(setOf(CardField.TRANSLATION), inAnki),
        planSave(stored, edited, note(inAnki)),
    )
}
@Test fun planWithNoChangesWritesNothing() =
    assertEquals(SavePlan.Write(emptyMap(), stored), planSave(stored, stored, note(stored)))
```

- [ ] **Step 2: Run them and see them fail.**
- [ ] **Step 3: Implement `CardEdit.kt`** to the rules above. `withFields` copies the six values and, when `extras` is given, `lemma` and `widened`.
- [ ] **Step 4: Run `make check` and see it pass.**
- [ ] **Step 5: Update `docs/DECISIONS.md` and commit** with `feat: card edit form and save plan`.

  Decisions (new section "Card page"):
  - In the edit form, the excerpt's and the sentence's focus is written in square brackets. A field left unchanged keeps its stored HTML.
  - Word and translation can't be blank. A changed excerpt needs one bracket pair, and a changed sentence needs none or one.
  - Saving writes only the fields the user changed. A changed field that also changed in AnkiDroid since the page read it is not written; the page shows AnkiDroid's text instead.

---

### Task 3: Card editor

**Files:**
- Create: `domain/cardpage/CardEditor.kt`
- Modify: `domain/card/Reconcile.kt` (receives `refreshed`), `domain/import/AnkiCheck.kt` (uses it), `AppContainer.kt`
- Test: `domain/cardpage/CardEditorTest.kt` (extends `ImportTestBase`), `domain/import/FakeAnki.kt` (gains `var updateError: Exception?`, thrown by `update` when set)

**Interfaces:**
- Consumes: Task 2's `CardFields`, `CardExtras`, `withFields`, `planSave` and `SavePlan`; `reconcile`, `next`, `NoteTypeChanged`.
- Produces:
  - `fun CardEntity.refreshed(fields: Map<String, String>, mod: Long): CardEntity` in `Reconcile.kt`, moved out of `AnkiCheck` without changes.
  - `CardEditor(db: AppDatabase, anki: AnkiGateway, lock: Mutex)`, created in `AppContainer` with `ankiLock` as `cardEditor`.
  - `OpenResult`:

```kotlin
sealed interface OpenResult {
    data class Fresh(val card: CardEntity) : OpenResult
    data class Local(val card: CardEntity, val unavailable: AnkiAvailability?, val error: String?) : OpenResult
    data class Deleted(val card: CardEntity) : OpenResult
    data object Gone : OpenResult
}
```

  - `suspend fun open(cardId: Long): OpenResult`:
    - no such card → `Gone`;
    - a card that isn't SENT → `Local(card, null, null)`;
    - SENT, under the lock:
      - AnkiDroid not READY → `Local(card, availability, null)`;
      - the note is missing → the card becomes `next(SENT, NOTE_MISSING)` and the result is `Deleted`;
      - when `reconcile` reports a refresh → the card is saved `refreshed` and returned as `Fresh`. Otherwise `Fresh(card)`;
      - a gateway exception → `Local(card, null, message)`.
  - `SaveResult`:

```kotlin
sealed interface SaveResult {
    data class Saved(val card: CardEntity) : SaveResult
    data class Conflict(val card: CardEntity, val fields: Set<CardField>) : SaveResult
    data class Deleted(val card: CardEntity) : SaveResult
    data class Unavailable(val availability: AnkiAvailability) : SaveResult
    data class Failed(val message: String, val missingFields: List<String> = emptyList()) : SaveResult
}
```

  - `suspend fun save(cardId: Long, baseline: CardFields, edited: CardFields, extras: CardExtras?): SaveResult`, under the lock:
    - the card is gone → `Failed("card not found")`;
    - the card is DELETED_IN_ANKI → the edits are saved in Room → `Deleted`;
    - the card is in any other status but SENT, or has no note id → `Failed("card is not in AnkiDroid")`;
    - AnkiDroid not READY → `Unavailable`;
    - the note is missing → the edits are saved in Room and the card becomes DELETED_IN_ANKI → `Deleted`;
    - the plan is `Conflict` → the card is saved `refreshed` from the note → `Conflict(refreshed card, fields)`. Nothing is written to AnkiDroid;
    - the plan is `Write`:
      - when `anki` isn't empty, call `anki.update(noteId, 0L, plan.anki, emptySet())`. `update` ignores `mid` and keeps the note's tags;
      - then save `card.withFields(plan.result, extras)` with `ankiMod = null`, and return `Saved`;
    - `NoteTypeChanged` → `Failed(message, missing)`. Any other exception → `Failed(message)`.

    Every Room write after AnkiDroid has changed runs in `NonCancellable`.
  - `suspend fun saveLocal(cardId: Long, edited: CardFields, extras: CardExtras?): CardEntity?`, under the lock. It updates and returns a PENDING card. For any other status it returns `null` and changes nothing.

- [ ] **Step 1: Write the failing tests.** Each test imports one card with `pipeline().run(req(backup(1)), {})`. The SENT ones then call `sender.send`. Use `fields = card.cardFields()` as the baseline.

```kotlin
@Test fun openReadsAChangeMadeInAnkiDroid()            // edit the note's Translation in fakeAnki.notes → Fresh, Room holds the new translation
@Test fun openMarksAMissingNoteDeletedInAnki()         // fakeAnki.notes.clear() → Deleted, status DELETED_IN_ANKI
@Test fun openShowsTheLocalCopyWhileAnkiDroidIsUnavailable() // NO_PERMISSION → Local(card, NO_PERMISSION, null), nothing written
@Test fun saveWritesTheChangedFieldAndKeepsTheOthers() {
    // Change Meaning in fakeAnki.notes to "<b>to win</b>", then save with translation = "одолеть".
    // Expect: note Translation == "одолеть", note Meaning still "<b>to win</b>",
    // and the Room card has translation "одолеть" and meaning "to win".
}
@Test fun saveStopsWhenAnEditedFieldChangedInAnkiDroid() {
    // Change the note's Translation to "X". Save with the baseline's translation edited to "Y".
    // Expect: Conflict(card with translation "X", setOf(TRANSLATION)), and the note's Translation still "X".
}
@Test fun saveOnAMissingNoteKeepsTheEditsLocally()     // clear notes, save → Deleted, Room card DELETED_IN_ANKI with the edited translation
@Test fun saveReportsMissingNoteTypeFields()           // fakeAnki.updateError = NoteTypeChanged(listOf("Meaning")) → Failed(_, listOf("Meaning"))
@Test fun saveStoresRetranslatedExtras()               // extras = CardExtras("upper hand", widened = true) → Room lemma and widened updated
@Test fun saveLocalUpdatesAPendingCard()
@Test fun saveLocalRefusesACardThatIsNoLongerPending()  // send it first → saveLocal returns null, the SENT card is unchanged
```

  Write each body to its comment, with `assertEquals` on the named values.
- [ ] **Step 2: Run them and see them fail.**
- [ ] **Step 3: Move `refreshed` and implement `CardEditor`.** `AnkiCheckTest` must still pass unchanged.
- [ ] **Step 4: Run `make check` and see it pass.**
- [ ] **Step 5: Update `docs/DECISIONS.md` and commit** with `feat: card editor with fresh read and conflict-safe save`.

  Decisions ("Card page"):
  - A sent card's page reads its note when it opens, under the AnkiDroid lock. A missing note makes the card DELETED_IN_ANKI.
  - Save sends AnkiDroid only the changed fields and no tags. When the note is gone, the edits are kept in the app and the card becomes DELETED_IN_ANKI.
  - A pending card's edits are saved in the app only while it is still PENDING.

---

### Task 4: Translate one card again

**Files:**
- Create: `domain/cardpage/CardRetranslator.kt`
- Modify: `data/db/Daos.kt` (`MarkDao.firstTextForCard`), `AppContainer.kt`
- Test: `domain/cardpage/CardRetranslatorTest.kt` (extends `ImportTestBase`)

**Interfaces:**
- Consumes: `Translator`, `TranslationItem`, `LlmException`, `markedText`, `Settings`.
- Produces:
  - `MarkDao`: `@Query("SELECT text FROM mark WHERE cardId = :cardId ORDER BY id LIMIT 1") suspend fun firstTextForCard(cardId: Long): String?`.
  - `data class Retranslation(val word: String, val grammar: String, val translation: String, val meaning: String, val extras: CardExtras)`.
  - The result type:

```kotlin
sealed interface RetranslateResult {
    data class Ok(val retranslation: Retranslation) : RetranslateResult
    data class Stopped(val kind: LlmException.Stop.Kind) : RetranslateResult
    data class Failed(val message: String) : RetranslateResult
}
```

  - `CardRetranslator(db: AppDatabase, translator: Translator, settings: Settings)`, created in `AppContainer` with the existing `TranslationService` as `retranslator`.
  - `suspend fun retranslate(cardId: Long): RetranslateResult`. It writes nothing.
    - It sends one `TranslationItem`:
      - `id = cardId.toString()`;
      - `marked`: the card's first mark text, or the card's word when it has no mark;
      - `sentence`: the plain text of `sentenceHtml`, or of `excerptHtml` when the sentence is empty (an invented example), or null when both are empty;
      - `bookTitle` and `sourceLanguage`: the book's title and the card's `sourceLanguage`.
    - The target is the card's own `targetLanguage`, with `maxExcerptWords` from settings.
    - The reply maps to `Ok`: `headword` becomes word, plus grammar, translation, meaning, and `CardExtras(lemma, widened)`.
    - A reply without the card's id → `Failed("no translation returned")`.
    - `LlmException.Stop` → `Stopped(kind)`. `LlmException.Failed` → `Failed(message)`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun retranslateSendsTheMarkTheStoredSentenceAndTheCardsTarget()
    // Capture the Translator's items and target. Expect marked == word(0), sentence == markedText(card.sentenceHtml).text,
    // sourceLanguage == card.sourceLanguage, and target == card.targetLanguage, even after settings change targetLanguage to "de".
@Test fun retranslateReturnsTheNewFieldsAndWritesNothing()
    // The Translator returns tr(item).copy(translation = "новый", widened = true) → Ok with translation "новый", extras.widened true; Room card unchanged
@Test fun aStopAndAFailureAreReported()
    // The Translator throws LlmException.Stop(AUTH) → Stopped(AUTH); throws LlmException.Failed("boom") → Failed("boom")
```

  Build `LlmException` values the way `ImportPipelineTest` does.
- [ ] **Step 2: Run them and see them fail.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run `make check` and see it pass.**
- [ ] **Step 5: Update `docs/DECISIONS.md` and commit** with `feat: translate a single card again in place`.

  Decisions ("Card page"):
  - Translate again on a pending or sent card asks for one translation of the card's first mark, in its stored sentence, into the card's own target language. Word, grammar, translation, meaning, lemma and the phrase flag come back as unsaved edits. Excerpt and sentence stay.
  - Translate again on a failed card forgets it and starts a resume import, as in the Inbox.

---

### Task 5: Library and book page model

**Files:**
- Create: `data/db/LibraryRow.kt`, `domain/library/Library.kt`
- Modify: `data/db/Daos.kt`, `domain/inbox/InboxList.kt` (make `inboxCard` internal)
- Test: `domain/library/LibraryTest.kt` (pure), `data/db/LibraryQueriesTest.kt` (Robolectric, in the style of `MarkQueriesTest`)

**Interfaces:**
- Produces:
  - `data class LibraryRow(val bookId: Long, val title: String, val author: String?, val language: String?, val inAnki: Int, val toReview: Int, val newestMark: Long?)`.
  - `CardDao.observeLibrary(): Flow<List<LibraryRow>>`. The SQL counts with subqueries, so a card with several marks counts once:

```sql
SELECT b.id AS bookId, b.title, b.author, b.language,
  (SELECT COUNT(*) FROM card WHERE card.bookId = b.id AND card.status = 'SENT') AS inAnki,
  (SELECT COUNT(*) FROM card WHERE card.bookId = b.id AND card.status IN ('PENDING','FAILED')) AS toReview,
  (SELECT MAX(mark.markedAt) FROM mark WHERE mark.bookId = b.id) AS newestMark
FROM book b WHERE EXISTS (SELECT 1 FROM card WHERE card.bookId = b.id)
```

  - `CardDao.observeBook(bookId: Long): Flow<List<InboxRow>>`: `observeInbox`'s query with `WHERE card.bookId = :bookId` in place of the status filter.
  - `data class LibraryGroup(val language: String?, val books: List<LibraryRow>)`.
  - `data class Library(val groups: List<LibraryGroup>, val books: Int, val inAnki: Int)`.
  - `fun library(rows: List<LibraryRow>): Library`:
    - groups are ordered by book count descending, then language code, with the null language last;
    - books in a group are ordered by `newestMark` descending, then `bookId` descending.
  - `data class BookPage(val toReview: List<InboxCard>, val inAnki: List<InboxCard>, val inAnkiTotal: Int, val discarded: List<InboxCard>, val deletedInAnki: Set<Long>)`.
  - `fun bookPage(rows: List<InboxRow>, latest: Int = 50): BookPage`:
    - `toReview`: PENDING and FAILED, in the Inbox order (newest mark, then id, descending);
    - `inAnki`: SENT, by `sentAt` descending then id descending, the first `latest`. `inAnkiTotal` counts them all;
    - `discarded`: DISCARDED and DELETED_IN_ANKI, newest mark first. `deletedInAnki` holds the ids of the latter.

- [ ] **Step 1: Write the failing tests**

```kotlin
// LibraryQueriesTest
@Test fun libraryCountsEachCardOnce()        // a SENT card linked to two marks, a PENDING card, a FAILED card, a DISCARDED card in one book → inAnki 1, toReview 2
@Test fun libraryOmitsBooksWithoutCards()
@Test fun observeBookReturnsEveryStatusOfThatBookOnly()

// LibraryTest
@Test fun groupsByLanguageWithOtherLast()            // rows en, en, de, null → groups [en(2), de(1), null(1)]
@Test fun booksInAGroupAreNewestMarkFirst()
@Test fun libraryTotalsBooksAndCardsInAnki()
@Test fun bookPageSplitsCardsByStatus()
@Test fun inAnkiShowsTheLatestFiftyBySendTime()      // 60 SENT with sentAt 1..60 → inAnki.size 50, first sentAt 60, inAnkiTotal 60
@Test fun cardsDeletedInAnkiAreListedWithDiscarded()
```

  Write each body to its comment. Build `InboxRow` and `LibraryRow` values directly in `LibraryTest`.
- [ ] **Step 2: Run them and see them fail.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run `make check` and see it pass.**
- [ ] **Step 5: Update `docs/DECISIONS.md` and commit** with `feat: library and book page model`.

  Decisions (new section "Library"):
  - The Library lists every book with at least one card, grouped by the book's language: most books first, Other last. Within a group, newest mark first.
  - A book page shows To review (pending and failed), In AnkiDroid (the latest 50 by send time), and Discarded, which also holds cards deleted in AnkiDroid.

---

### Task 6: Navigation 3 shell

Fixes tickets mv-gh2y (Back on Settings exits the app) and mv-q291 (the Inbox's scroll position resets on a tab switch). Close both with `tk close` in this task's commit.

**Files:**
- Create: `ui/Nav.kt`
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`, `ui/AppShell.kt`
- No tests: this is wiring. `make check`, `make debug` and the phone check in Task 10 cover it.

**Interfaces:**
- Produces, in `ui/Nav.kt`:
  - `@Serializable data object InboxKey : NavKey`, `LibraryKey`, `SettingsKey`;
  - `@Serializable data class BookKey(val bookId: Long) : NavKey`, `CardKey(val cardId: Long)`, `PreviewKey(val cardId: Long)`;
  - `val TabKeys: List<NavKey>`: Inbox and Settings here, with Library added by Task 8;
  - `fun NavBackStack<NavKey>.selectTab(tab: NavKey)`. It leaves `[InboxKey]` for the Inbox and `[InboxKey, tab]` for any other tab, and does nothing when that tab is already on top.
- `AppShell` takes no parameters, as now.

Behaviour:
- `rememberNavBackStack(InboxKey)` holds the stack. `NavDisplay` gets:
  - `onBack = { backStack.removeLastOrNull() }`;
  - `entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator())`;
  - an `entryProvider` with `InboxKey` and `SettingsKey`. Later tasks add the other keys.
- The bottom bar shows only when the top key is in `TabKeys`, and hides while the Inbox selects, as now. Its items call `selectTab`.
- `InboxViewModel` stays activity-scoped. `AppShell` creates it outside `NavDisplay` for the badge and the selection state, as now.
- Tab destinations receive the shell's padding as now. Pushed pages (later tasks) draw their own top bar and insets.
- Tab switches cross-fade, without the push slide. Set it through the entries' `NavDisplay` transition metadata; check the exact names in navigation3-ui `1.2.0`. Pushed pages keep `NavDisplay`'s default transitions and predictive back.
- `InboxScreen`'s `onOpenSettings` calls `selectTab(SettingsKey)`.
- The Inbox's `rememberLazyListState` is saved by the entry's saveable state while the Inbox stays at the bottom of the stack. That is the mv-q291 fix: no extra code.

- [ ] **Step 1: Add the dependencies.** In `[versions]`, add `navigation3 = "1.2.0"`. Add these libraries:
  - `androidx-navigation3-runtime` and `androidx-navigation3-ui`, on `navigation3`;
  - `androidx-lifecycle-viewmodel-navigation3`, on `androidxLifecycle`.

  Then add them to `implementation`.
- [ ] **Step 2: Write `Nav.kt` and rebuild `AppShell`** to the behaviour above.
- [ ] **Step 3: Run `make check` and `make debug` and see both pass.**
- [ ] **Step 4: Update `docs/DECISIONS.md` and commit** with `feat: navigation 3 shell with the Inbox at the root`.

  Decisions ("UI"): replace "Tabs are plain Compose state. Navigation 3 arrives with the card page in milestone 4." with:
  - Navigation 3 holds one back stack that starts with the Inbox. Another tab sits on top of it, and pages are pushed above. Back from a tab returns to the Inbox.
  - The bottom bar shows only on tabs. Tab switches cross-fade.

---

### Task 7: Card page

**Files:**
- Create: `ui/card/CardPageViewModel.kt`, `ui/card/CardPage.kt`, `ui/card/CardFaces.kt` (the two faces, read and edit forms)
- Modify:
  - `ui/AppShell.kt` (the `CardKey` entry);
  - `ui/inbox/InboxScreen.kt` and `ui/inbox/InboxRows.kt`: a tap outside selection calls `onOpenCard(id)`;
  - `values/strings.xml`;
  - `docs/DEVIATIONS.md`;
  - icons: `ic_arrow_back`, `ic_visibility`, `ic_more_vert`, `ic_restore_from_trash`, `ic_translate`.

**Interfaces:**
- Consumes:
  - from Task 1: `CardSender.send`, `undoSend` and `deleteFromAnki`;
  - from Tasks 2–4: `CardEditor`, `CardRetranslator`, `parseForm`, `toForm`, `CardField`;
  - `InboxActions.discard`, `restore` and `forgetForRetranslation`;
  - `ImportWork.enqueue`, `MarkedExcerpt`, `BookFontFamily`.
- Produces:
  - `CardPageViewModel(cardId, db, editor, sender, actions, retranslator, workManager, io)`, with `fun factory(cardId: Long): ViewModelProvider.Factory` in the style of `InboxViewModel.Factory`.
  - `@Composable fun CardPage(cardId: Long, onBack: () -> Unit, onPreview: ((Long) -> Unit)?)`. A null `onPreview` hides the Preview action. The `PreviewKey` entry arrives in Task 9, so until then the shell passes `null`.

ViewModel state:
- `card: CardEntity?` and `book: BookEntity?`;
- `source`: Fresh, Local (with availability) or Deleted, from `editor.open`;
- `baseline: CardFields`, `form: CardFields`, `extras: CardExtras?`;
- `editing: Boolean` and `invalid: Set<CardField>`;
- `busy: Boolean`, which disables actions while one runs;
- `importRunning: Boolean`, from the unique import work;
- `dialog`: none, confirm delete, or confirm leave.

Events go to the page's own `SnackbarHostState`, and a new one replaces the one showing, as in the Inbox.

Behaviour by status:

| Status | Bottom actions | Overflow menu | Edit | Preview |
|---|---|---|---|---|
| PENDING | Discard (tonal), Send to Anki (filled, off when the translation is blank or the form is invalid) | Translate again | yes | yes |
| FAILED | Discard (tonal), Translate again (filled, off while an import runs) | none | no | no |
| SENT | Save changes to Anki (filled, on when there are unsaved changes and the source isn't Local) | Translate again, Delete from AnkiDroid | yes | yes |
| DISCARDED | Restore (filled) | none | no | no |
| DELETED_IN_ANKI | Send as new card (filled) | none | no | no |

Preview is also off while there are unsaved changes.

Actions:
- **Send** (pending): first `saveLocal` when the form has changes, then `sender.send(listOf(id))`.
  - Success: snackbar `card_snack_sent` with Undo, which calls `undoSend`. The page reloads with `editor.open`.
  - Unavailable, error, or missing fields: the Inbox's strings, as `InboxScreen.eventMessage` maps them.
- **Send as new card** (deleted in AnkiDroid): `sender.send(listOf(id))`, with the same messages.
- **Discard**: `actions.discard`. Snackbar `card_snack_discarded` with Undo, which calls `actions.restore`.
- **Restore**: `actions.restore`.
- **Save** (sent): `parseForm`, then `editor.save(id, baseline, fields, extras)`.
  - `Saved`: snackbar `card_snack_saved`. The baseline becomes the saved card, and edit mode ends.
  - `Conflict`: snackbar `card_snack_conflict` with the field labels. The baseline becomes the refreshed card. The form takes the fresh values for the conflicting fields and keeps the user's text in the others.
  - `Deleted`: snackbar `card_snack_note_gone`. The page shows the deleted state.
  - `Unavailable` or `Failed`: the Inbox's strings.
- **Delete from AnkiDroid**: confirm with `card_delete_title`, `card_delete_text`, `card_delete_confirm` and `card_cancel`, then `sender.deleteFromAnki`.
  - Deleted: `card_snack_deleted`.
  - Not deleted: `card_snack_delete_refused`, or the unavailable message.
- **Translate again:**
  - pending or sent: `retranslator.retranslate`. `Ok` fills word, grammar, translation and meaning into the form, sets `extras`, enters edit mode, and shows `card_snack_retranslated`. `Stopped` uses the `inbox_result_stopped_*` strings, and `Failed` uses `inbox_action_failed`;
  - failed: `actions.forgetForRetranslation(listOf(id))`, then the same `ImportWork.enqueue` call as `InboxViewModel.translateAgain`, then `onBack()`.
- **Edit and Done:** the top bar's toggle.
  - Done runs `parseForm`. Invalid fields show `card_field_required` or `card_brackets_help` as error text, and edit mode stays.
  - On a pending card, Done saves with `saveLocal`. A `null` result means the card is no longer pending, so the page reloads.
  - On a sent card, Done keeps the edits on the page until Save.
- **Back** (`BackHandler` while there are unsaved changes):
  - a pending card with a valid form saves locally and leaves;
  - otherwise a dialog asks, with `card_leave_title`, `card_leave_keep` and `card_leave_discard`.

Layout, following `PAGES.card`:
- **Top bar:** Back, the word as title, then Preview (`ic_visibility`), Edit or Done (`ic_edit` or `ic_check`), and the overflow menu (`ic_more_vert`) when it has items.
- **Body:** a scrolling column.
  - Subheader `card_front`. A face (rounded `surfaceContainerLowest` block, like `.face`) with the word, the grammar in italic `BookFontFamily`, and the excerpt through `MarkedExcerpt`.
  - Subheader `card_back`. A face with:
    - the translation, or `card_no_translation` in `error`;
    - the meaning when it isn't blank;
    - a divider;
    - the sentence through `MarkedExcerpt`, or the excerpt when the sentence is empty;
    - the book line `Title, Author`.
  - The labels as in Inbox rows: Phrase, Invented and Elsewhere.
- **Notes under the faces:**
  - `card_sent_note` on a sent card;
  - `card_local_note` when the source is Local with an availability;
  - `card_deleted_note` on a card deleted in AnkiDroid;
  - `card_discarded_note` on a discarded card;
  - the error on a failed card.
- **Edit mode:** the faces become `OutlinedTextField`s, labelled with the field names below. Excerpt and sentence carry `card_brackets_help` as supporting text.
- **Bottom:** the actions sit in a bottom row above the navigation bar inset.

Strings:

| Name | English |
|---|---|
| `card_front` | Front |
| `card_back` | Back |
| `card_edit` | Edit card |
| `card_done` | Done editing |
| `card_preview` | Preview in AnkiDroid |
| `card_more` | More actions |
| `card_navigate_back` | Back |
| `card_send` | Send to Anki |
| `card_discard` | Discard |
| `card_translate_again` | Translate again |
| `card_save` | Save changes to Anki |
| `card_delete` | Delete from AnkiDroid |
| `card_restore` | Restore |
| `card_send_as_new` | Send as new card |
| `card_no_translation` | No translation yet |
| `card_sent_note` | This card is in AnkiDroid. Saving updates it there and keeps its review history. |
| `card_local_note` | AnkiDroid isn\'t available, so this is the app\'s copy. Saving is off until AnkiDroid is back. |
| `card_deleted_note` | This card was deleted in AnkiDroid. Send it again to make a new card. |
| `card_discarded_note` | You discarded this card. |
| `card_field_word` | Word |
| `card_field_grammar` | Grammar |
| `card_field_excerpt` | Excerpt |
| `card_field_translation` | Translation |
| `card_field_meaning` | Meaning |
| `card_field_sentence` | Sentence |
| `card_brackets_help` | Put the word you marked in [square brackets]. |
| `card_field_required` | Can\'t be empty |
| `card_snack_sent` | Sent to AnkiDroid |
| `card_snack_discarded` | Card discarded |
| `card_snack_saved` | Card updated in AnkiDroid |
| `card_snack_deleted` | Deleted from AnkiDroid |
| `card_snack_delete_refused` | AnkiDroid didn\'t delete the note, so the card stays there. |
| `card_snack_conflict` | Changed in AnkiDroid while you were editing: %1$s. The page now shows AnkiDroid\'s text. |
| `card_snack_note_gone` | This card was deleted in AnkiDroid. Your changes are kept here. |
| `card_snack_retranslated` | New translation filled in. |
| `card_delete_title` | Delete from AnkiDroid? |
| `card_delete_text` | The note and its review history are deleted in AnkiDroid. The card stays here as discarded. |
| `card_delete_confirm` | Delete |
| `card_cancel` | Cancel |
| `card_leave_title` | Discard your changes? |
| `card_leave_keep` | Keep editing |
| `card_leave_discard` | Discard |

Undo reuses `inbox_undo`.

- [ ] **Step 1: Download the icons**, as in Global Constraints.
- [ ] **Step 2: Build `CardPageViewModel`** to the actions above. Every call to AnkiDroid, the database or the translator runs on `io`, and its exception becomes a snackbar, as in `InboxViewModel`.
- [ ] **Step 3: Build `CardPage` and `CardFaces`, and add the `CardKey` entry.** Wire `onOpenCard` from the Inbox: a tap on a card outside selection pushes `CardKey(id)`. Taps during selection still toggle.
- [ ] **Step 4: Add the DEVIATIONS entry.**
  - Plan said: the prototype closes the card page after Send, Discard and Save.
  - Actual: the page stays open, shows the new state, and offers Undo in its own snackbar.
  - Reason: the same behaviour from the Inbox and from a book page, with no messages between screens.
- [ ] **Step 5: Run `make check` and `make debug` and see both pass.** Lint must report no missing strings.
- [ ] **Step 6: Update `docs/DECISIONS.md` and commit** with `feat: card page with editing, save to AnkiDroid and card actions`.

  Decisions:
  - "UI": delete "Tapping a card outside selection does nothing until milestone 4."
  - "Card page":
    - Tapping a card outside selection opens its page.
    - Card page actions keep the page open and show their result there, with Undo for send and discard.
    - A pending card's edits are saved in the app on Done or Back. A sent card's edits wait for Save.
    - Delete from AnkiDroid asks first.

---

### Task 8: Library tab and book page

**Files:**
- Create: `ui/library/LibraryViewModel.kt`, `ui/library/LibraryScreen.kt`, `ui/library/BookViewModel.kt`, `ui/library/BookPage.kt`, `ui/common/LanguageName.kt`
- Modify:
  - `ui/Nav.kt` (`TabKeys` gets Library);
  - `ui/AppShell.kt` (the Library tab item and the `LibraryKey` and `BookKey` entries);
  - `ui/inbox/InboxRows.kt` (uses `languageName`);
  - `values/strings.xml`;
  - icons: `ic_menu_book`, `ic_chevron_right`, `ic_expand_more`.

**Interfaces:**
- Consumes: from Task 5, `CardDao.observeLibrary`, `observeBook`, `library` and `bookPage`; `BookDao.byId`; `CardKey`.
- Produces:
  - `@Composable fun languageName(code: String?): String`, moved out of `InboxRows`. It gives `Locale.forLanguageTag(code).getDisplayLanguage(uiLocale)`, capitalised, or `inbox_language_other` for null.
  - `LibraryViewModel(db, io)`, activity-scoped like `InboxViewModel`, so the tab keeps its data.
  - `BookViewModel(bookId, db, io)` with `factory(bookId)`.

Behaviour:
- **Bottom bar:** Inbox, Library (`ic_menu_book`, `nav_library`), Settings.
- **Library**, following `renderLibrary`:
  - `LargeFlexibleTopAppBar`, title `library_title`, subtitle `library_subtitle` built from the `library_books` and `library_cards_in_anki` plurals.
  - One group per language: a subheader with `languageName`, then segmented rows (the Inbox's row shapes).
  - Each row has:
    - the title in `BookFontFamily` SemiBold 16 sp;
    - the author in `onSurfaceVariant` 13 sp;
    - the meta line `library_in_anki`, then, when `toReview > 0`, `, ` and `library_to_review` in bold;
    - a trailing `ic_chevron_right`.
  - A tap pushes `BookKey`.
  - With no books, show `library_empty_title` and `library_empty_text`, in the Inbox's empty style.
- **Book page**, following `PAGES.book`:
  - `LargeFlexibleTopAppBar` with Back, the title in `BookFontFamily`, and the subtitle `book_subtitle` ("%1$s, %2$s": author and language name, or just the language when there's no author).
  - Under it, a meta line: `library_in_anki`, plus `, ` and `library_to_review` when that count is above 0.
  - Sections:
    - `book_to_review` rows;
    - `book_in_anki` rows, with `book_latest` under them only when `inAnkiTotal > 50`;
    - a `book_discarded` header showing its count, with `ic_expand_more`. It expands on tap, and its state is saveable.
  - Each row shows the word and the grammar (italic `BookFontFamily`) on one line, then the translation, or `card_no_translation` in `error`. Cards deleted in AnkiDroid add the label `book_deleted_label`.
  - A tap pushes `CardKey`.
  - A section with no cards is left out.

Strings:

| Name | English |
|---|---|
| `nav_library` | Library |
| `library_title` | Library |
| `library_books` | plurals `%d book` / `%d books` |
| `library_cards_in_anki` | plurals `%d card in Anki` / `%d cards in Anki` |
| `library_subtitle` | %1$s, %2$s |
| `library_in_anki` | plurals `%d in Anki` / `%d in Anki` |
| `library_to_review` | plurals `%d to review` / `%d to review` |
| `library_empty_title` | No books yet |
| `library_empty_text` | Books show up here after you import a Moon+ backup. |
| `book_subtitle` | %1$s, %2$s |
| `book_to_review` | To review |
| `book_in_anki` | In AnkiDroid |
| `book_latest` | Showing the latest %1$d. All %2$d are in AnkiDroid. |
| `book_discarded` | plurals `Discarded (%d)` / `Discarded (%d)` |
| `book_deleted_label` | Deleted in AnkiDroid |

- [ ] **Step 1: Download the icons.**
- [ ] **Step 2: Move `languageName`, then build the two ViewModels and screens, and add the entries.**
- [ ] **Step 3: Run `make check` and `make debug` and see both pass.**
- [ ] **Step 4: Update `docs/DECISIONS.md` and commit** with `feat: library tab and book page`.

  Decisions ("UI"): "The app opens on the Inbox. The bottom bar has Inbox and Settings…" becomes "The app opens on the Inbox. The bottom bar has Inbox, Library and Settings. Settings shows the debug screen until milestone 5."

---

### Task 9: AnkiDroid preview

**Files:**
- Create: `domain/card/CardPreview.kt`, `ui/card/PreviewPage.kt`
- Modify: `ui/AppShell.kt` (the `PreviewKey` entry and `CardPage`'s `onPreview`), `values/strings.xml`, `docs/DEVIATIONS.md`
- Test: `domain/card/CardPreviewTest.kt`

**Interfaces:**
- Consumes: `NoteTypeSpec.FRONT`, `BACK` and `CSS`; `ankiFields`; `bookLine` (already `internal` in `CardSender.kt`).
- Produces:
  - `fun fillTemplate(template: String, fields: Map<String, String>, frontSide: String = ""): String`. It supports `{{Name}}`, `{{#Name}}…{{/Name}}` (kept when the field isn't blank) and `{{FrontSide}}`. Unknown names become empty. Values are inserted as they are: fields are already HTML.
  - `fun previewHtml(fields: Map<String, String>, showAnswer: Boolean, night: Boolean, background: String, text: String): String` returns a whole HTML document:
    - a viewport meta;
    - `<style>` with `NoteTypeSpec.CSS`, plus `body{margin:0;background:<background>;color:<text>}`;
    - `<body class="card[ nightMode]">` holding the filled front, or the filled back with the front as `{{FrontSide}}`.

    `background` and `text` are `#RRGGBB`, taken from the theme's `surfaceContainerLowest` and `onSurface`.

- [ ] **Step 1: Write the failing tests**

```kotlin
private val fields = mapOf("Word" to "die Schnalle", "Grammar" to "f., -n", "Excerpt" to """eine <span class="mark">Schnalle</span>""",
    "Translation" to "пряжка", "Meaning" to "", "Sentence" to "", "Book" to "Title", "Source" to "")
@Test fun fieldsAreInsertedAsHtml() = assertTrue(fillTemplate(NoteTypeSpec.FRONT, fields).contains("""<span class="mark">Schnalle</span>"""))
@Test fun aSectionForABlankFieldIsDropped() = assertFalse(fillTemplate(NoteTypeSpec.BACK, fields).contains("class=\"mean\""))
@Test fun aSectionForAFilledFieldIsKept() = assertTrue(fillTemplate(NoteTypeSpec.BACK, fields + ("Meaning" to "a buckle")).contains("""<div class="mean">a buckle</div>"""))
@Test fun theBackStartsWithTheFront() {
    val front = fillTemplate(NoteTypeSpec.FRONT, fields)
    assertTrue(fillTemplate(NoteTypeSpec.BACK, fields, front).startsWith(front))
}
@Test fun nightModeAddsTheClass() {
    assertTrue(previewHtml(fields, showAnswer = false, night = true, background = "#000000", text = "#FFFFFF").contains("class=\"card nightMode\""))
    assertTrue(previewHtml(fields, showAnswer = false, night = false, background = "#FFFFFF", text = "#000000").contains("class=\"card\""))
}
```

- [ ] **Step 2: Run them and see them fail.**
- [ ] **Step 3: Implement `CardPreview.kt`.**
- [ ] **Step 4: Build `PreviewPage(cardId, onBack)`**, following `PAGES.anki` without its grade buttons.
  - Top bar: Back and `preview_title`.
  - The card is drawn in an `AndroidView` `WebView` with JavaScript off, through `loadDataWithBaseURL(null, html, "text/html", "utf-8", null)`.
  - The fields are `card.ankiFields(bookLine(book))`, loaded from Room on `io`.
  - A bottom button switches between `preview_show_answer` and `preview_show_question`. A tap on the card shows the answer.
  - `night` follows the app theme's dark mode.
  - The shell passes `onPreview = { backStack.add(PreviewKey(it)) }` to `CardPage`.
- [ ] **Step 5: Add the DEVIATIONS entry.**
  - Plan said: the prototype's preview has AnkiDroid's grade buttons, which step to the next card.
  - Actual: Show answer and Show question only.
  - Reason: the grade buttons did nothing real, and the preview shows one card.
- [ ] **Step 6: Run `make check` and `make debug` and see both pass.**
- [ ] **Step 7: Update `docs/DECISIONS.md` and commit** with `feat: card preview with the note type's own templates`.

  Strings: `preview_title` "Card in AnkiDroid", `preview_show_answer` "Show answer", `preview_show_question` "Show question".

  Decisions ("Card page"):
  - The preview fills the note type's front and back templates and CSS with the card's fields, in a WebView with JavaScript off. Its background and text colours follow the app theme. Dark mode adds `nightMode`.
  - Preview is off while the page has unsaved changes.

---

### Task 10: On the phone (user-driven)

The user installs the release APK (`make release`) over the current build. Nothing runs on the device without their go-ahead.

Checks:
1. Tap an Inbox card. The page shows both faces with the wave. Edit the translation, tap Done, go back, and reopen: the change stayed.
2. Send from the page, then Undo. The note leaves AnkiDroid, and the card is pending again.
3. Edit a sent card's translation and save. In AnkiDroid the note shows the new text with its review history. Then edit the same field in AnkiDroid, and save a different edit from the app without reopening the page: the app reports the conflict and shows AnkiDroid's text.
4. Delete a note in AnkiDroid, then open its card. The page says it was deleted and offers Send as new card.
5. Delete from AnkiDroid on the page, confirm, then Restore. The card is back in the Inbox.
6. Use Translate again on a sent card: new values fill the fields, and Save writes them.
7. Open the Library, then a book, the Discarded section, and a card. Back returns step by step. Back on Library or Settings returns to the Inbox. The Inbox keeps its scroll position across tabs.
8. Open a card page, close the app from recents or let Android kill it, and return: the app comes back on the same page.
9. Preview a card in light and dark mode, and use Show answer.

Fix each reported defect in its own commit (`fix: <what>`), then build and send a new APK.

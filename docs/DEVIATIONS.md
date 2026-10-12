# Deviations

Departures from the spec or a plan: what the plan said, what was done instead, and why. Newest on top. An entry is deleted when it stops being true.

## 2026-10-12: `ServicePage` takes an `onOpen` callback

- Plan said: `ServicePage(id: String, onBack: () -> Unit)`.
- Actual: `ServicePage(id, onBack, onOpen: (String) -> Unit)`; Duplicate opens the copy in place of the original's page.
- Reason: the copy becomes active, and the user duplicates a service to change it; staying on the original would edit the wrong one.

## 2026-10-12: The picker reads the phone's languages with `LocaleManagerCompat.getSystemLocales`

- Plan said: the device languages for Suggested come from `LocaleListCompat.getAdjustedDefault()`.
- Actual: `LocaleManagerCompat.getSystemLocales(context)`, for Suggested and for Same as phone.
- Reason: below API 33 AppCompat sets the app language as the default `LocaleList`, so `getAdjustedDefault()` lists the app's override, not the phone's languages.

## 2026-10-12: The Library's to-review count is SemiBold in the primary colour

- Plan said: the Library's to-review count is bold.
- Actual: SemiBold in the primary colour, as in the prototype.
- Reason: the prototype.

## 2026-10-12: An AnkiDroid error during Delete from AnkiDroid shows its message

- Plan said: Delete from AnkiDroid shows `card_snack_delete_refused` or the unavailable message.
- Actual: an AnkiDroid error during delete shows `inbox_action_failed` with the message.
- Reason: a refusal and an error are different cases.

## 2026-10-12: The card preview's background is the Inbox row colour

- Plan said: the preview background is `surfaceContainerLowest`.
- Actual: `inboxColors().row`, the Inbox row colour.
- Reason: the prototype's `--row-bg`; `surfaceContainerLowest` looks sunk in dark mode.

## 2026-10-12: The card preview has no grade buttons

- Plan said: the prototype's preview has AnkiDroid's grade buttons, which step to the next card.
- Actual: Show answer and Show question only.
- Reason: the grade buttons did nothing real, and the preview shows one card.

## 2026-10-12: The card page stays open after its actions

- Plan said: the prototype closes the card page after Send, Discard and Save.
- Actual: the page stays open, shows the new state, and offers Undo in its own snackbar.
- Reason: the same behaviour from the Inbox and from a book page, with no messages between screens.

## 2026-10-12: The import sheet's ViewModel reports a bad pick and confirms the start

- Plan said: M3 Task 9's `SheetState.NoBackup` is `(skipped)`, `SheetState.Ready` is `(backup, skipped, counts, selected, sendToAnki)`, `FolderUnreadable` comes from a `SecurityException`, and the ViewModel has `fun start()`, which enqueues the import and closes the sheet.
- Actual: `NoBackup` and `Ready` also carry `pickIncomplete: Boolean = false`, which shows `sheet_change_incomplete`. `Ready` also carries `importRunning: Boolean = false`: when unfinished import work exists, Start enqueues nothing, keeps the sheet open and shows `sheet_import_running`. `start(onStarted: () -> Unit)` runs after the sheet's pending settings writes and calls `onStarted`, which closes the sheet, only once the work is enqueued. An `IOException` from `newest` also gives `FolderUnreadable`.
- Reason: the plan's states have nowhere to keep the "incomplete pick" line beside the previous backup. A Start that closed the sheet at once could close it over a settings error that the screen must show. `ImportWork.enqueue` keeps unfinished work, so a Start during an import would be dropped silently. A deleted or moved folder can't be listed and throws `IOException`, and spec §8 asks the user to choose the folder again.

## 2026-10-12: A send that stops partway gives Sent with its error

- Plan said: M3 Task 8's send maps any `error` to `SendFailed`, and `Sent(sentIds, stayed = ids.size - sentIds.size)` only when there is no error.
- Actual: when `sentIds` isn't empty, one `Sent(sentIds, stayed = skipped, error)` is emitted. Its snackbar names the cards sent, the cards without a translation that stayed, and "Sending stopped: <error>", and offers Undo. `SendFailed` alone means nothing was sent. `stayed` is `SendResult.skipped`, so cards left unsent because the send stopped aren't counted as lacking a translation.
- Reason: the cards that did go to AnkiDroid would otherwise leave the Inbox with no Undo. A new snackbar replaces the shown one, so a separate `SendFailed` would replace the `Sent` snackbar and its Undo.

## 2026-10-12: Inbox actions take the AnkiDroid lock

- Plan said: M3 Task 2's `class InboxActions(private val db: AppDatabase)` runs each method in one transaction.
- Actual: `InboxActions(db, lock)` takes the `Mutex` that `CardSender` and `AnkiCheck` share, and runs each method's transaction under it.
- Reason: a send saves its snapshot of PENDING cards one by one. A card discarded meanwhile would be overwritten as SENT, and a card deleted for translate again would leave its note orphaned in AnkiDroid.

## 2026-10-12: The Inbox has a generic Failed event

- Plan said: M3 Task 8's `InboxEvent` has seven cases, and only sending reports errors.
- Actual: `InboxEvent.Failed(message)`, shown as `inbox_action_failed` ("Something went wrong: %1$s"), reports a failed discard, restore, translate again, or undo of a send.
- Reason: UI calls to AnkiDroid or the database report errors in the screen, and none of the seven cases fits these actions.

## 2026-10-12: The Send button sits beside the floating toolbar

- Plan said: M3 Task 8's `HorizontalFloatingToolbar(expanded = true)` holds a Send FAB in its `floatingActionButton` slot, showing `ic_send` and `inbox_send_n`.
- Actual: the toolbar holds Discard and Translate again. A 64 dp `ExtendedFloatingActionButton` with `ic_send` and "Send N" sits 10 dp to its right in a `Row`, as in the prototype. With nothing selected it is drawn at 45 % alpha, marked disabled for accessibility, and ignores taps.
- Reason: the toolbar's slot measures the FAB as a fixed square (56–80 dp), so "Send N" can't fit. FABs have no `enabled` parameter.

## 2026-10-12: Swipe state is not saveable, and send and discard report a stay

- Plan said: M3 Task 8's rows take their state from `rememberSwipeToDismissBoxState(positionalThreshold = { it * 0.35f })`, and the ViewModel's actions are `send(ids)` and `discard(ids)`.
- Actual: each row builds `SwipeToDismissBoxState(Settled, positionalThreshold = { it * 0.35f })` in `remember(card.id)`. `send` and `discard` take an optional `onStayed` callback, which runs when the cards stay in the Inbox; the swiped row passes one that snaps it back.
- Reason: `SwipeToDismissBox` calls `onDismiss` from an effect keyed on the settled value, and the list keeps saveable state per key. An undone card would come back in its dismissed state and be sent or discarded again. Events don't say which row a `Stayed` or error belongs to, so the row learns it through the callback.

## 2026-10-12: "N selected" is a plural

- Plan said: M3 Task 8's `inbox_selected` is a string, `%1$d selected`.
- Actual: it is a `plurals` with the same text for `one` and `other`.
- Reason: counts go through `plurals`, and lint flags the string as `PluralsCandidate`.

## 2026-10-12: The Inbox state has a `loaded` flag

- Plan said: M3 Task 7's `InboxState` is `(list, selection, import, anki, missingFields)`.
- Actual: it also has `loaded`, false in the initial value and true from the first combined emission. The empty state shows only when `loaded` is true.
- Reason: the initial value holds an empty list, so without the flag "All caught up" flashes before the first query result.

## 2026-10-12: AnkiDroid's permission fallback is new code

- Plan said: M3 Task 7's banner reuses `DebugScreen`'s permission handling, "including its fallback to app settings".
- Actual: `DebugScreen` had no fallback. `ui/common/AnkiPermission.kt` adds `rememberAnkiPermissionRequest`, which opens the app's system settings when the request is denied without a rationale. The Inbox banner and `DebugScreen` both use it.
- Reason: once the user denies the permission for good, the system answers without a dialog, and only the app's settings can grant it.

## 2026-10-12: The mark wave spans character boxes

- Plan said: M3 Task 6's `MarkedExcerpt` runs the wave's x from `getHorizontalPosition(start)` to `getHorizontalPosition(end)` on each line the mark covers.
- Actual: on each line, x runs from the outer left to the outer right edge of `getBoundingBox` of the segment's first and last visible characters. A segment ends at the mark's end or at `getLineEnd(line, visibleEnd = true)`, whichever is earlier.
- Reason: when a mark wraps onto the next line, its segment ends at the line's end offset. `getHorizontalPosition` at that offset reports the next line's start, which would draw the wave backwards across the line. The boxes also cover RTL text.

## 2026-10-12: A picked backup is copied under a temporary name first

- Plan said: M3 Task 5's `BackupFinder.open` copies the picked document into `cacheDir/backups` under its display name, reads the copy, and deletes it when the backup is incomplete.
- Actual: `open` copies to `<display name>.part`, reads that, and renames it to the display name only when the backup is complete. An incomplete copy is deleted, and the previously chosen copy stays.
- Reason: a picked file with the same name as the chosen backup would otherwise overwrite it, and an incomplete pick would then delete the backup the screen still uses.

## 2026-10-11: A wrong base URL or model stops the import

- Plan said: Task 8's `LlmException.Stop.Kind` is `AUTH, CREDITS` (401/403, 402). Spec §5.3 stops the import only for 401, 403 and 402. Any other client error fails its batch.
- Actual: `Kind` has a third value, `CONFIG`. A base URL that is empty or not a valid http(s) URL throws `Stop(CONFIG, "Invalid base URL")` before sending, and HTTP 404 throws `Stop(CONFIG, …)` with the response excerpt. The import stops as for the other kinds, and the debug screen says to check the service's base URL and model.
- Reason: like a rejected key, a wrong base URL or model fails every later batch the same way. Failing each batch would turn the whole backlog into failed cards. OpenRouter answers 404 for an unknown model, and a wrong path gives 404 too.

## 2026-10-11: An incomplete backup still resumes pending marks

- Plan said: Task 12's `ImportPipeline.run`, step 1: "`Incomplete` returns a summary with `incompleteBackup` set."
- Actual: an incomplete backup adds no marks, but the run goes on to process the marks already stored without a card, as a resume does. The summary still has `incompleteBackup` set.
- Reason: marks left over from an interrupted or stopped import would otherwise wait until a complete backup turns up.

## 2026-10-11: Cards show the normalized text

- Plan said: spec §5.2: "The normalized text keeps an offset map back to the original, so the sentence shown on the card is the book's own text."
- Actual: `buildDraft` normalizes the found sentence and maps the mark into it (ruling R15). The card's Sentence and Excerpt come from the normalized text: no soft hyphens, whitespace runs collapsed to one space, NFC, and curly quotes and apostrophes turned straight.
- Reason: this keeps soft hyphens and line breaks out of the cards, and lets the model's focus `Schnallen` match the book's `Schnal­len`. The cost is straight quotes on cards where the book has curly ones.

## 2026-10-11: Sentences are found within a paragraph

- Plan said: spec §5.2, step 3, takes the enclosing sentence with `BreakIterator`, and if it is shorter than 6 words adds the neighbouring sentence on each side.
- Actual: the sentence and its widening stay inside the paragraph (the text between two newlines) that holds the match (ruling R14). A mark that spans a newline is its own sentence.
- Reason: Android's ICU `BreakIterator` breaks sentences at a newline and the JDK's doesn't, so without the scoping, unit tests and the device would disagree. A heading also shouldn't merge into the sentence after it. The cost is that a sentence of 1–5 words alone in its paragraph stays short.

## 2026-10-11: Model excerpts have no lower word bound

- Plan said: spec §4.1 gives the Excerpt as "6 to N words", and the plan's global constraints give the range as 6–15.
- Actual: a model excerpt is accepted with any number of words up to `maxExcerptWords` (ruling R18). Only the upper bound is enforced.
- Reason: the plan's own `validModelExcerptIsUsed` test expects a 5-word model excerpt to be used. The spec's "6 to N" reads as approximate.

## 2026-10-11: The AnkiDroid gateway reads notes with a null projection

- Plan said: Task 11's `read` queries `Note.CONTENT_URI` with projection `_id, mid, flds, tags, mod`.
- Actual: the query passes a `null` projection and looks the five columns up by name.
- Reason: AnkiDroid's provider maps the projection through aliased default columns and rejects an explicit `_id`. Its default note projection already has all five columns.

## 2026-10-11: The import worker also retries failed cards, and the debug screen edits saved services

- Plan said: Task 12 builds `ImportWorker`, which runs `pipeline.run` from an `ImportRequest`. Task 13's debug screen has Base URL, API key and model fields, and "Retry failed" with no stated path. The worker calls `setForeground` with a `dataSync` notification.
- Actual: `ImportWorker` is built in Task 13 together with `AppContainer`. Its input data has a `retryFailed` flag; with it, the worker calls `pipeline.retryFailed()` instead of `run`, and "Retry failed" enqueues that under the same unique `import` work. If `setForeground` throws `IllegalStateException`, the worker logs it and imports without the notification. The debug screen has a dropdown of saved translation services, Name, Base URL, API key and Model fields that edit the active one, and an "Add service" button that adds an empty "Custom" service and makes it active.
- Reason: the worker needs `App.container`, which Task 13 creates. One worker keeps all long work on one foreground path and makes Retry and Import exclusive. Android 12+ refuses a foreground-service start while the app is in the background, for example when WorkManager reruns the import after the process was killed, and failing the whole import over a missing notification would lose that run. The settings already hold a list of services (the "saved translation services" entry below).

## 2026-10-11: Send failures are reported, not thrown

- Plan said: Task 12's `CardSender.send` returns `SendResult(sent, skipped, unavailable)`, and `ImportSummary` is `(newCards, failed, sent, incompleteBackup, stopped)`.
- Actual: `SendResult` has a fourth field, `error: String?`, and `ImportSummary` has a sixth, `sendError: String?` (the first send error of the run). `send` catches any exception except `CancellationException`, stops, and returns the counts so far. Cards not yet sent stay `pending`.
- Reason: the AnkiDroid gateway can throw anything (an NPE across Binder, `NoteTypeChanged`, `IllegalStateException`), and a failed send must not fail the import or lose the cards already written.

## 2026-10-11: The import pipeline takes a book source factory

- Plan said: Task 12's `ImportPipeline` takes `books: BookSource`, cached per pipeline run.
- Actual: it takes `books: suspend () -> BookSource` and calls it once at the start of each `run`. The factory is suspending because the production one reads the books folder from `Settings`. The production source is `FolderBookSource(folder: BookFolder?, cacheDir: File)` in `data/books`, next to the `BookSource` interface. It lists the folder once, caches each book's text (or null) by Moon+ path, and treats any error while copying or parsing a book as "no text". A subfolder that can't be listed is skipped. If the chosen folder itself can't be listed, the import fails.
- Reason: a source made once per pipeline would keep book texts and a stale folder listing across imports. One broken book must not stop an import; its marks get model-written examples (spec §8). A folder that can't be listed at all, for example after its permission was revoked, would quietly give every mark a model-written example.

## 2026-10-11: The stored note type id is checked against the model list

- Plan said: Task 11's `ensureNoteType` uses the stored id if `getFieldList(knownId)` is non-null (spec §4.1: "checks the id still exists with `getFieldList(mid)`").
- Actual: it reads `getModelList()` once and uses the stored id if the list contains it. If not, it takes the smallest id among note types named "Moon+ Vocab", and if there are none it creates one. A null model list throws `IllegalStateException`.
- Reason: for a deleted mid, AnkiDroid's provider throws a NullPointerException across Binder rather than returning null, so `getFieldList` never reports the type as missing.

## 2026-10-11: `writeBatch` returns the inserted card ids

- Plan said: Task 10's `AppDatabase.writeBatch(cards: List<Pair<CardEntity, List<Long>>>)` inserts each card and links its marks in one transaction, and returns nothing.
- Actual: the function is `writeBatch(batch: List<Pair<CardEntity, List<Long>>>): List<Long>`. It returns the inserted card ids, in input order.
- Reason: a parameter named `cards` shadows the `cards()` DAO accessor inside the body. The pipeline needs the new card ids without querying for them again.

## 2026-10-11: Settings keep a list of saved translation services

- Plan said: Task 10's `SettingsSnapshot` holds `baseUrl`, `apiKey` and `model`.
- Actual: `SettingsSnapshot` holds `services: List<LlmService>` and `activeServiceId`, plus a computed `activeService`, which is the service with that id or else the first one. The services are stored as one JSON string preference, and the active id as a second preference. The default is a single OpenRouter service with the plan's base URL and model. A missing or unparsable list, or an empty one, falls back to that default. `update` trims every service's base URL, key and model.
- Reason: the user asked for saved translation services (spec §5.3).

## 2026-10-11: A model excerpt's ellipses come from its position, and it must mark the chosen occurrence

- Plan said: Task 7's `acceptModelExcerpt(modelExcerpt, sentence, focus, maxWords)` returns the model's excerpt "with the ellipses kept and the brackets removed".
- Actual: the model's own ellipses are dropped. `…` goes before the excerpt when it doesn't start at the beginning of the sentence, and after it when it doesn't end at the sentence's end. The function takes an extra `focusStart` and accepts the excerpt only if its bracketed focus lands exactly there and the excerpt starts and ends on word boundaries; otherwise the window cut is used.
- Reason: the model's ellipses don't reliably show where text was cut. With a repeated focus, the model can bracket a different occurrence than the one nearest the mark, and the card's excerpt and sentence would then mark different words.

## 2026-10-11: Two SentenceFinder test inputs are changed

- Plan said: Task 5's `shiftedChapterNumberStillFinds` uses chapter 8 `"He was hapless as ever. Next one."` and expects `"He was hapless as ever."`. `repeatedWordPicksNearestToPosition` fills the gap between the two moles with `"x ".repeat(200)`.
- Actual: chapter 8 is `"He was as hapless as ever. Next one."` and the test expects `"He was as hapless as ever."`. The filler is `"Waves rolled in. ".repeat(50)`.
- Reason: `"He was hapless as ever."` has 5 words, so the plan's own step 7 (fewer than 6 words) widens it with `" Next one."`. The `"x x …"` filler contains no sentence break, so `BreakIterator` returns the whole text as one sentence, and that sentence cannot start with "That noble mole".

## 2026-10-11: A zipped file named `.fb2` is detected by its content

- Plan said: Task 4 dispatches on the file name alone: `.fb2` is FB2 and `.fb2.zip` is the first `.fb2` entry inside a zip.
- Actual: a `.fb2` file that starts with the zip signature `PK\3\4` is read like a `.fb2.zip`.
- Reason: some libraries hand out zipped FB2s named plain `.fb2`. In the 1515-book probe, such files gave no chapters when parsed as XML.

## 2026-10-11: FB2 poem lines are chapter text

- Plan said: a FB2 chapter's text is its `p` elements' text joined with `\n`.
- Actual: `p` and `v` elements (poem lines inside `stanza`) are joined with `\n`, in document order.
- Reason: otherwise a word marked inside a poem is never found and gets an invented example.

## 2026-10-11: EPUB OPF elements are matched in any namespace

- Plan said: Task 4 finds the spine through `itemref/@idref` → manifest `item/@href`, with `dc:language`, `dc:title` and `dc:creator` read from the OPF.
- Actual: the selectors are namespace-agnostic (`*|spine > *|itemref`, `*|manifest > *|item`, `*|metadata *|title`, `*|rootfile`).
- Reason: some OPFs prefix every element (`<opf:package>`, `<opf:spine>`, `<opf:item>`, as in *Termination Shock*), and plain selectors find no chapters in them. In a probe of 1515 real books, this was one of the 19 books still without chapters after the parser fix.

## 2026-10-11: FB2 chapters come from the first body even when it is named

- Plan said: chapters come only from `body` elements without a `name` attribute (ruling R9).
- Actual: chapters come from the first `body` whatever its name, plus any later `body` without a name. `<body name="notes">` and `<body name="comments">` are still skipped.
- Reason: the FB2 spec makes the first body the main text and allows it a name. Real books use this (`<body name="СОДЕРЖАНИЕ">` in Švejk, `<body name="Содержание">` in Idoru), and under R9 they gave zero chapters. In the 1515-book probe, the rule, together with reading zipped files named `.fb2`, took the FB2 books without chapters from 16 to 1, and that one is a file of zero bytes.

## 2026-10-11: EPUB chapter text uses the XML parser and separates blocks

- Plan said: each spine document's text is `Jsoup.parse(html).body().wholeText()`.
- Actual: spine documents are parsed with `Parser.xmlParser()`. A `\n` is inserted after every block element (`p, div, h1`–`h6, li, blockquote, pre, tr, dt, dd, br`) before `wholeText()`.
- Reason: FB2→EPUB converters write a self-closing `<title/>`, which the HTML parser reads as an open title that swallows the whole body. With the plan's code, 281 of 708 real EPUBs had empty chapters. The same converters write `<p>…</p><p>…</p>` with no whitespace between them, so `wholeText()` glues paragraphs together ("конец.Начало") and `BreakIterator` can't split the sentences. A glue heuristic flagged about 420 books before the change and 28 after. Of those 28, 18 are technical books where code such as `foo.Bar` matches. The other 10 are novels and other prose (Stardust, Altered Carbon, Cryptonomicon, several of them FB2) whose source text itself lacks the space after a full stop ("came over to me.You were"), which no parser change can fix.

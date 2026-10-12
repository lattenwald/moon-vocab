# Decisions

## Repository
- Agents stage and commit their own changes throughout development: one commit per task while implementing a plan, on the branch the plan names. Agents never push unasked. One-line subject, no attribution.
- `make release` builds the minified release APK, signed with `~/.android/debug.keystore`. `app/proguard-rules.pro` holds no app rules.
- Robolectric tests run with `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED`.
- Tests that use MockWebServer run under `RobolectricTestRunner`. `isReturnDefaultValues` stays off.
- `kotlin-test` is a test dependency.
- The AnkiDroid AAR's `DuplicateCrowdInStrings` lint check is disabled.

## Backup and marks
- `RawMark` carries `combo: StyleCombo` (style + signed ARGB colour).
- A mark's file key is `fileKeyOf(bookPath)`: the basename, lowercased.
- `StyleCombo` is stored as `"STYLE:color"` through `StyleCombo.encode()` / `parseStyleCombo()`.
- `BookEntity.relativePath` stores the original Moon+ book path. Books are located by it on resume, falling back to the file key.

## Book text
- EPUB spine documents are parsed with jsoup's XML parser.
- Inside an EPUB, whitespace runs in text nodes outside `<pre>` collapse to one space. A `\n` follows every block element.
- EPUB block elements:
  - `p`, `div`, `h1`–`h6`, `li`, `blockquote`, `pre`, `tr`, `dt`, `dd`, `br`;
  - `section`, `article`, `aside`, `header`, `footer`;
  - `figcaption`, `caption`, `td`, `th`, `hr`;
  - `ul`, `ol`, `dl`, `table`, `address`.
- OPF selectors ignore namespace prefixes.
- An FB2 is parsed from its bytes, so the declared encoding applies.
- FB2 chapters come from the first `<body>`, plus every later `<body>` without a `name`.
- FB2 chapter text is the `p` and `v` lines, in document order.
- An FB2 is parsed only up to its first `<binary` after the first `<body`.
- A zip file named `.fb2` is read as a zipped FB2.
- `isoLanguage` maps ISO 639-2/B codes to two-letter codes and returns only two-letter codes in `Locale.getISOLanguages()`; anything else is null.
- A book whose file can't be found, copied or parsed has no text. Its marks get model-written examples.
- `FolderBookSource` keeps no texts. An `OutOfMemoryError` while reading one book gives that book no text.
- A books folder whose root can't be listed fails the import. Subfolders that can't be listed are skipped.

## Sentences, excerpts, cards
- The mark is searched case-insensitively, without changing string length.
- A sentence never crosses a paragraph `\n`. Widening a short sentence stays inside its paragraph.
- A mark that spans a paragraph break is its own sentence.
- Each run keeps the text and normalized chapters of at most 3 books, least recently used out.
- The model receives the normalized sentence.
- The card's Sentence and Excerpt are built from the normalized sentence: no soft hyphens, no line breaks, straight quotes.
- The focus word is located case-insensitively, at the occurrence nearest the mark.
- The model's excerpt is used only when all of these hold:
  - it is a substring of the sentence;
  - it starts and ends on word boundaries;
  - it has at most N words;
  - its bracketed focus falls on the chosen occurrence.
- Otherwise the app cuts a window of N words around the focus.
- The excerpt has no minimum length.
- The `…` before and after an excerpt mark where the sentence was cut.
- Two cards are compared for sense when their source languages are equal (null equals null) and their `senseKey(lemma)` values are equal.
- Sense candidates are known cards (SENT and PENDING first, then DISCARDED and DELETED_IN_ANKI, then by id) followed by earlier cards of the same batch. FAILED cards are never candidates.
- A mark that matches an earlier mark of its batch goes wherever that mark went.

## Translation
- BYOK only. Settings hold a list of saved services `(id, name, baseUrl, apiKey, model)` and one active service id.
- The default service is OpenRouter, `https://openrouter.ai/api/v1`, `anthropic/claude-haiku-5.5`, with an empty key.
- The services list is never empty.
- Every `LlmService` field except `id` has a default.
- The stored services JSON is rewritten only when the services change.
- Service templates, in order: OpenRouter, OpenAI, Google Gemini, Mistral, DeepSeek, Groq, Custom. A template fills only the name and base URL; Custom's are empty.
- An added or duplicated service becomes active. A duplicate keeps the base URL, key and model, and sits right after the original.
- The last service can't be deleted. Deleting the active service activates the next one, or the previous one when it was last.
- Translate into offers the prototype's 80 `LANGUAGE_CODES`.
- Suggested languages: the current one, then the phone's languages, then English; only listed codes, no repeats.
- Language search ignores case and matches the name in the UI language, the own name, the English and Russian names, or the code. Lists are sorted by the UI-language name with the UI locale's `Collator`.
- `LlmClient` never blocks: OkHttp `enqueue` with a cancellable coroutine.
- The production OkHttp client uses `callTimeout(60 s)` and `retryOnConnectionFailure(false)`.
- `LlmException.Stop` kinds:
  - `AUTH`: 401, 403, or a key with invalid characters;
  - `CREDITS`: 402;
  - `CONFIG`: 404, or an invalid or empty base URL.
- `LlmException.Failed` covers retries exhausted, other 4xx, and an unreadable reply.
- The API key is redacted from error messages and is never attached as a cause.
- Each item in a translation reply is parsed on its own. For a repeated id, the first valid result wins.
- A blank `lemma` becomes the `headword`.
- The model's `sourceLanguage` goes through `isoLanguage`.
- Same-sense questions for a batch go in one request to the active service, at most 8 candidates each.
- A judge's match is kept only when it is one of that question's own candidate refs.
- For a repeated id in a sense reply, the first result wins even when its match is null or invalid.
- Test connection translates one item with the edited service's own base URL, key and model: id `test`, marked `Schnallen`, sentence `Der Mantel hatte viele Taschen und Schnallen.`, book `Test`, source language `de`, at most 15 excerpt words. It does not wait out a 429 or 5xx.
- A connection test's `Failed.kind` is the `Stop` kind, or null for `LlmException.Failed`. A reply without the sample's translation is `NoTranslation`.
- `ConnectionTest` times with `SystemClock.elapsedRealtime`.
- Model suggestions come from `GET {baseUrl}/models` (`data[].id`, sorted, no repeats), with the key as a Bearer token when it isn't blank. Any failure gives no suggestions and no error.

## Storage and status
- `mark.cardId` is a foreign key to `card.id` with `ON DELETE SET NULL`.
- `withoutCard` orders by `markedAt DESC, id DESC`.
- `upsertByFileKey` keeps the id and an existing language, and updates the title. It updates the author and path only when the new value isn't null.
- The book's author and language are filled from the book text when they are missing.
- `next(PENDING|SENT, TRANSLATED)` keeps the status.
- `RESTORE` needs a previous status.
- `deckName`:
  - a blank override counts as no override;
  - `_` is read as `-`;
  - a missing or unknown language gives `Other`.
- `bookTags` sanitizes only the title. Letters are kept in any script.
- `Settings.snapshot` falls back to defaults on an `IOException`.
- Settings store only values that differ from their defaults. Writing a default removes its key.
- `reviewFirst` defaults to `true`, and `deckOverride` defaults to `Moon+::Test`.
- `theme` is `SYSTEM`, `LIGHT` or `DARK`, stored by name, default `SYSTEM`. `ankiAsked` defaults to `false`.
- `setupDone` defaults to `true` when `backupTree` is set, else `false`. Its key is stored only when the value differs from that default.

## AnkiDroid
- `ankiFields` HTML-escapes Word, Grammar, Translation, Meaning, Book and Source. Excerpt and Sentence are already HTML. AppId is the card's uuid.
- Book is `Title, Author`, or just `Title` when there is no author.
- A field changed in Anki is read back into the card as plain text with `ankiToPlain`. Excerpt and Sentence are kept as raw HTML.
- `reconcile` compares the fields the card stores (Word, Grammar, Excerpt, Translation, Meaning, Sentence, Source) that are present in the note: plain fields as `ankiToPlain` text, Excerpt and Sentence as raw HTML.
- `update` merges with the note's current note type in AnkiDroid, and writes the union of the note's tags and ours.
- `ensureNoteType`:
  - uses the stored id while it is in AnkiDroid's model list;
  - otherwise uses the smallest id named "Moon+ Vocab";
  - otherwise creates the note type.
- Templates and CSS are rewritten when the stored version is older. The new version is stored only when the rewrite succeeds.
- `ensureDeck` matches an exact name, otherwise creates the deck.
- Notes are read with a null projection, and columns are looked up by name. A note whose field list can't be read is an error.
- `findElsewhere` returns the first hit's deck name, `""` when the deck can't be read, null for no hit. It searches `-note:"Moon+ Vocab"` with a quoted `w:` term for the lemma and for the focus when it differs, ignoring case.
- Search terms are always quoted, and `\ " * _ : ( )` and a leading `-` are backslash-escaped.
- Notes are found by AppId with `mid:<mid> (AppId:<uuid> OR …)`, 50 uuids per query.

## Import pipeline
- `ImportPipeline` takes a book-source factory, called once per run.
- Batches of 10 marks, newest first. They may cross books.
- One transaction per batch: the cards are written and their marks linked.
- A card links one or more marks. The mark that made it is the first; later same-sense marks only link to it.
- Per batch: translate, merge same-sense marks, search other sources, then write and link in one transaction.
- A sense `Stop` ends the import with nothing written for that batch. A sense `Failed` means no merges for that batch, is logged, and counts in `ImportSummary.senseFailed`.
- AnkiDroid availability is read once per run for the other-source search. A search error means no label.
- A mark whose preparation or draft building throws becomes a FAILED card.
- An incomplete backup still resumes the pending marks.
- `Stop` ends the import with nothing written for that batch. `Failed` makes the batch's cards FAILED.
- After the loop, the pipeline runs `AnkiCheck.run()`, which never fails an import.
- `AnkiCheck.check()` throws on failure and returns `Changed(n)` or `Unavailable`.
- A check that reaches AnkiDroid and finishes records `lastCheckAt`. When `MainActivity` is created, the check runs if the last one is 10 minutes old or more.
- `AppContainer` creates one `Mutex`, shared by `CardSender`, `AnkiCheck`, `InboxActions` and `CardEditor`. Sends, deletes, undo, the check, the Inbox's discard, restore and translate again, and the card page's open, Save and local save run under it.
- `CardSender`:
  - `send` sends PENDING cards and cards deleted in AnkiDroid. Other ids count as skipped;
  - a gateway error stops the send and is reported in `SendResult.error`;
  - saving a sent card can't be cancelled;
  - the note type is ensured once per sender and again after a send error;
  - before adding, notes already in AnkiDroid with the card's AppId are adopted;
  - after a send error the import sends nothing more in that run.
- `deleteAllCreated` resets a card only when AnkiDroid deleted its note.
- `deleteFromAnki` discards a sent card only when AnkiDroid deleted its note. Its previous status becomes PENDING.
- `undoSend` deletes each sent card's note and returns the card to PENDING. A card whose note AnkiDroid doesn't delete stays SENT and counts as kept.
- A send stopped by a note type with missing fields reports them in `SendResult.missingFields`.
- `retryFailed` deletes FAILED cards, then resumes.

## Inbox
- The Inbox shows PENDING and FAILED cards.
- Discard keeps the card's status in `previousStatus`. Restore puts it back and clears it.
- Translate again deletes the chosen PENDING and FAILED cards that have no note, then starts a resume import without sending. Their marks get new cards.
- Inbox groups are ordered by their newest mark. Cards in a group are ordered by newest mark, then id, both descending.
- Groups whose newest marks are equal are ordered by book id, descending.
- Language chips appear only when the Inbox holds two or more languages. A card with no language counts as Other, listed last.
- A filter whose language left the Inbox falls back to All, and the stored filter becomes All.
- Selection is a pure reducer in `domain/inbox/Selection.kt`. The screen only maps fingers to rows and calls it.
- Long-press is 360 ms. Auto-scroll starts 72 dp from either list edge and moves at most 18 dp per frame.
- Swipe is off while selecting.
- Translate again is disabled while an import runs.
- Send and discard offer Undo in a snackbar. Undoing a send deletes the notes it created.
- A new Inbox snackbar replaces the one showing.
- A send that stops after sending some cards shows one snackbar: the count sent, the cards without a translation that stayed, `inbox_send_stopped` with the reason, and Undo.
- A send or undo that finds AnkiDroid unavailable updates the Inbox banner at once.
- Cards labelled "Already in Anki" can be sent from the Inbox.
- While an import runs, the Import button shows its progress and opens nothing.

## Card page
- In the edit form, the excerpt's and the sentence's focus is written in square brackets. A field left unchanged keeps its stored HTML.
- Word and translation can't be blank. A changed excerpt needs one bracket pair, and a changed sentence needs none or one.
- Changed form values are trimmed. A blank sentence is saved empty.
- Save sends AnkiDroid only the fields the user changed, and no tags. When any changed field also changed in AnkiDroid since the page read it, nothing is written to AnkiDroid. When the note is gone, the edits are kept in the app and the card becomes DELETED_IN_ANKI.
- On a conflict, the page shows AnkiDroid's text for the conflicting fields and for fields the user didn't change. The user's other edits stay.
- A sent card's page reads its note when it opens, under the AnkiDroid lock. A missing note makes the card DELETED_IN_ANKI.
- A pending card's edits are saved in the app only while it is still PENDING. Otherwise the page keeps them: as unsaved edits when the card is now SENT, else with `card_snack_not_pending`, and Back stays on the page.
- Translate again on a pending or sent card asks for one translation of the card's first mark, in its stored sentence, into the card's own target language. Word, grammar, translation, meaning, lemma and the phrase flag come back as unsaved edits. Excerpt and sentence stay.
- Translate again on a failed card forgets it and starts a resume import, as in the Inbox.
- Tapping a card outside selection opens its page.
- Card page actions keep the page open and show their result there, with Undo for send and discard.
- A pending card's edits are saved in the app on Done or Back. A sent card's edits wait for Save.
- Delete from AnkiDroid asks first.
- The preview fills the note type's front and back templates and CSS with the card's fields, in a WebView with JavaScript off. Its background is the Inbox row colour and its text colour the theme's `onSurface`. Dark mode adds `nightMode`.
- Preview is off while the page has unsaved changes.

## Library
- The Library lists every book with at least one card, grouped by the book's language: most books first, Other last. Within a group, newest mark first.
- A book page shows To review (pending and failed), In AnkiDroid (the latest 50 by send time), and Discarded, which also holds cards deleted in AnkiDroid.
- `LibraryViewModel` is activity-scoped, created in `AppShell`.
- The Library and the book page use the Inbox's row shapes and colours. A book page's Discarded section starts collapsed, and its open state is saveable.

## Import sheet
- The import sheet's import count is the new marks in the chosen combinations plus the stored marks with no card.
- A mark combination is named by its style and a colour name taken from its hue.
- `BackupFinder` keeps only the chosen backup's copy in `cacheDir/backups`.
- `BackupFinder.newest` and `open` run one at a time under the finder's own lock, held until a cancelled call's copy finishes.
- The import sheet reads the newest complete backup each time it opens, except when opened by a share, which shows the shared file.
- `ImportSheetViewModel` is activity-scoped: `AppShell` holds it and passes it to the Inbox.
- `InboxViewModel.requestImportSheet()` only opens the sheet; its caller first calls the sheet's `open()` or `change(uri)`.
- The sheet's Review first / Send to Anki choice is saved as `reviewFirst`, and its mark selection as `styles`.
- The import sheet runs its `BackupFinder` calls and settings writes one at a time, under one lock.
- A `SecurityException` or `IOException` from `BackupFinder.newest` shows FolderUnreadable.
- The sheet's Error state offers Try again (`open()`) and Choose folder.
- While unfinished import work exists, Start keeps the sheet open and shows `sheet_import_running`.

## Language picker
- One picker page, `LanguageKey(TARGET)` or `LanguageKey(APP)`, with the Inbox's segmented rows and a radio on each row.
- Translate into has a search field, then the line `settings_target_note`, then Suggested and All languages. While searching it shows only the matches, in one group, or `picker_none`.
- A language row shows the name in the UI language and, below it, the language's own name when that differs.
- The phone's languages come from `LocaleManagerCompat.getSystemLocales`, as language codes.
- Picking a target language writes `targetLanguage`, then pops. No snackbar.
- App language offers Same as phone (with the phone's language), English and Русский, without search. English and Русский are shown in their own names.
- Picking an app language calls `AppCompatDelegate.setApplicationLocales` (empty for Same as phone), then pops. The current choice comes from `AppCompatDelegate.getApplicationLocales()`.

## Translation services pages
- The Services page lists the saved services with a radio for the active one. A row shows the name and the model, or `services_no_model`. The radio selects; the row opens the service page.
- "Add service" opens a menu of the templates. The new service opens at once.
- The service page has name, address, API key (show/hide), model (with suggestions) and Test connection, with Duplicate and Delete in its overflow menu.
- Service edits are saved as typed, in keystroke order, on `appScope`, so leaving the page never drops one. The fields keep local `remember` state keyed by the service id.
- Delete asks first and is disabled for the last service. A service page whose service is gone pops.
- Duplicate replaces the page with the copy's page. A service's title is its name, else its address, else `service_unnamed`.
- Model suggestions are fetched when the service page opens and again 600 ms after the address or key stops changing; a change clears them at once. The model field shows at most 50 that contain the typed text, ignoring case.
- Changing the address, key or model clears the test result and cancels a running test.
- Test seconds show one decimal, formatted in the UI locale.

## Cards settings page
- Longest excerpt is a slider from 6 to 15 words, with no switch for the excerpt on the front.
- The example front is the sample card, cut with `cutExcerpt` and drawn by the card page's `FrontFace`. It follows the slider while dragging; `maxExcerptWords` is saved when the drag ends.
- The deck and book tag patterns are saved as typed. A blank field saves its default and shows it once the field loses focus.
- When the test deck is on, the deck field's help line names it.
- Card design opens the preview (`PreviewKey(null)`) with the sample card, cut to the stored longest excerpt.

## Settings page
- The Settings tab has a large title and the prototype's groups: Translation (service, Translate into), Look (Theme, App language), Moon+ Reader (Backups folder, Books folder, Review before sending), Cards (Cards, AnkiDroid), then Developer tools alone.
- Theme is a segmented button. Selecting writes `theme` and calls `setDefaultNightMode`.
- A folder row shows the path after `:` in the tree's document id, "Not chosen", or, when `persistedUriPermissions` holds no read grant for it, "Can't read this folder. Choose it again." in the error colour.
- Choosing a folder takes the persistable read permission, then saves the folder even if that failed.
- The AnkiDroid row reads Connected, Allow access (asks), or Not installed (opens the Play Store). It and the folder grants are refreshed on resume.
- Settings page writes run in `appScope` under one lock. Theme and Review before sending writes queue in tap order; a folder's write queues only after its permission is taken.

## First run
- Setup shows while `setupDone` is false; an install that already has a backup folder counts as set up. The first back-stack key comes from `App.setupDone`, read at start and set when setup finishes.
- Steps: Welcome, Folders, AnkiDroid, Translation, Ready. Folders to Translation show three progress bars; Back and Continue sit at the bottom. The step index is saveable.
- Folders: the backup folder is required. Its row shows how many `.mrpro` files it holds and the newest one's relative date, "No backups in this folder yet" (Continue still works), or, when it can't be listed, the unreadable line and Choose folder again. The books folder is optional.
- AnkiDroid: Continue waits for access. Not installed shows Get AnkiDroid (Play Store). A refused request shows the denied line. Availability is refreshed on resume.
- Translation: the active service's key with Test connection, then Use another service (Services page), then the Translate into row. Continue needs a non-blank key.
- While the active service's host is openrouter.ai, the setup key field's help reads "Get a key at openrouter.ai/keys. It stays on this phone.", with openrouter.ai/keys linking to `https://openrouter.ai/keys`.
- Ready counts the newest complete backup's marks in `defaultStyles` whose key isn't stored, with a progress indicator while it reads. It counts once per backup folder; returning to Ready keeps that count. Some new: Later or Import now. None, or the backup can't be read: Done.
- Finishing writes `setupDone = true` in `appScope` and replaces the back stack with the Inbox. Import now also calls the sheet's `open()` and `InboxViewModel.requestImportSheet()`, which the Inbox consumes to open the import sheet.
- Back goes to the previous step. On the first step, Back leaves the app.

## Share target
- `MainActivity` is `singleTask` and accepts `ACTION_SEND` with `EXTRA_STREAM` for any MIME type (`*/*`). It reads a share in `onCreate` (only without saved state, and not when launched from history) and in `onNewIntent`. A failed `EXTRA_STREAM` read is logged and ignored.
- A received share waits in the activity-scoped `ShareViewModel` (`SavedStateHandle`) until `AppShell` takes it.
- A shared file goes at once to `ImportSheetViewModel.share(uri)`, which copies it into the cache. The app then clears the Inbox selection and shows the Inbox tab with the sheet open.
- A shared file that isn't a complete backup shows `NoBackup` with the "incomplete" note, never the sheet's earlier state.
- A share that arrives while `App.setupDone` is false is dropped.

## App
- `ImportWorker` runs both import and retry-failed in the foreground, as `dataSync`.
- If `setForeground` is refused, the worker continues without the notification.
- App data is excluded from cloud backup and device transfer.
- UI calls to AnkiDroid, the database or SAF run on `io` and report errors in the screen. The Inbox, Library, book page and preview queries and the Inbox's AnkiDroid availability check only log theirs; a failed query shows an empty screen.
- The debug screen's service fields use `remember`, never `rememberSaveable`.
- The debug screen can turn off the test deck, export cards as JSON (no settings or keys), and forget PENDING and FAILED cards without a note id and all DELETED_IN_ANKI cards.
- The debug screen's Send all pending skips cards with an `elsewhereDeck`.

## UI
- Fonts are bundled variable TTFs from google/fonts: Roboto Flex for the UI and Literata for book text. The licences ship in `assets/licenses/`.
- Icons are Material Symbols Rounded vector drawables from google/material-design-icons, in `res/drawable/ic_*.xml`.
- The theme is System, Light or Dark. It is applied with `AppCompatDelegate.setDefaultNightMode` in `App.onCreate`, from a blocking read kept as `App.startSettings`, and again on every change. `isSystemInDarkTheme()` follows it.
- The mark underline is `#D9480F` in light mode and `#FF8A50` in dark mode, drawn as a wave under the focus.
- The app opens on the Inbox, or on setup while `setupDone` is false. The bottom bar has Inbox, Library and Settings. Settings shows the settings page.
- Navigation 3 holds one back stack that starts with the Inbox, or with `SetupKey` until setup finishes. Another tab sits on top of it, and pages are pushed above. Back from a tab returns to the Inbox.
- The bottom bar shows only on tabs. Tab switches cross-fade.
- The debug screen is the Developer tools page (`DeveloperKey`), pushed from the last row of Settings, with its own top bar. It keeps all its tools. `DebugViewModel` is scoped to its entry.
- A finished import's result stays on the Inbox until it is dismissed. Dismissing prunes finished work.
- An import stopped by the translation service shows its reason in the result card, with a button that opens the Services page on top of the Settings tab.
- The Inbox list provides the theme's large shape as 24 dp. Its rows take their outer, selected and pressed corners from it, and 6 dp inner corners from `segmentedShapes`' default shapes.
- The Inbox background is `surfaceContainer` with `surfaceContainerLowest` rows when the surface is light, and `surface` with `surfaceContainer` rows when it is dark.
- Asking for AnkiDroid's permission opens the app's system settings only when the request is denied, `ankiAsked` was already true before it, and the system showed no rationale either before or after it (it answered without a dialog). `ankiAsked` is set when a request starts. Taps are ignored while a request is in flight.
- Opening the Play Store for AnkiDroid tries `market://details?id=com.ichi2.anki`, then `https://play.google.com/store/apps/details?id=com.ichi2.anki`.

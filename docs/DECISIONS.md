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
- `reviewFirst` defaults to `true`, and `deckOverride` defaults to `Moon+::Test`.

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
- `AppContainer` creates one `Mutex`, shared by `CardSender` and `AnkiCheck`. Sends, deletes, undo and the check run under it.
- `CardSender`:
  - a gateway error stops the send and is reported in `SendResult.error`;
  - saving a sent card can't be cancelled;
  - the note type is ensured once per sender and again after a send error;
  - before adding, notes already in AnkiDroid with the card's AppId are adopted;
  - after a send error the import sends nothing more in that run.
- `deleteAllCreated` resets a card only when AnkiDroid deleted its note.
- `undoSend` deletes each sent card's note and returns the card to PENDING. A card whose note AnkiDroid doesn't delete stays SENT and counts as kept.
- A send stopped by a note type with missing fields reports them in `SendResult.missingFields`.
- `retryFailed` deletes FAILED cards, then resumes.

## Inbox
- The Inbox shows PENDING and FAILED cards.
- Discard keeps the card's status in `previousStatus`. Restore puts it back and clears it.
- Translate again deletes the chosen PENDING and FAILED cards that have no note, then starts a resume import without sending. Their marks get new cards.
- Inbox groups are ordered by their newest mark. Cards in a group are ordered by newest mark, then id, both descending.
- Language chips appear only when the Inbox holds two or more languages. A card with no language counts as Other, listed last.
- A filter whose language left the Inbox falls back to All.

## App
- `ImportWorker` runs both import and retry-failed in the foreground, as `dataSync`.
- If `setForeground` is refused, the worker continues without the notification.
- App data is excluded from cloud backup and device transfer.
- UI calls to AnkiDroid, the database or SAF run on `io` and report errors in the screen.
- The debug screen's service fields use `remember`, never `rememberSaveable`.
- The debug screen can turn off the test deck, export cards as JSON (no settings or keys), and forget PENDING and FAILED cards without a note id and all DELETED_IN_ANKI cards.
- The debug screen's Send all pending skips cards with an `elsewhereDeck`.

# Moon+ Vocab: design

- Status: draft, external review applied 2026-10-11, awaiting user review
- Created: 2026-10-11
- Approved design prototype: [Moon+ Vocab Inbox, version 2 "All screens"](https://claude.ai/artifact/53vybzBKgPuDzG8TzTUzQj)
- Earlier references: [reference board](https://claude.ai/artifact/3UaoHwkZNJAcSYcZYfvcrz)

## 1. Purpose

While reading in Moon+ Reader Pro, the user marks words they had to look up with an orange wavy underline. This app turns those marks into AnkiDroid flashcards so the user can grow their vocabulary. The cards train recall of a word's meaning; they are not meant to train reading speed or grammar.

Success means: after a reading session, the user imports the newest Moon+ backup, glances over (or skips) a short list of new cards, and they are in AnkiDroid with a translation and the sentence the word came from. Editing a card later updates it in AnkiDroid without losing its review history.

Out of scope for v1: reading Moon+ cloud-sync `.an` files, MOBI/PDF/TXT sentence extraction, vocabulary statistics, the ColorDict/StarDict lookup history, syncing between devices, other flashcard apps, recovering the app's records after a reinstall (5.8).

## 2. What the real data shows

Verified on the user's backups `2026-10-11.mrpro` and `2026-10-05_AUTO.mrpro`, and their books mirrored in `~/cloud/Books`:

- A `.mrpro` is a zip. Entries are `com.flyersoft.moonreaderp/N.tag`, plus `com.flyersoft.moonreaderp/_names.list`. Line *N* of `_names.list` (1-based) is the original path of `N.tag`. The database `databases/mrbooks.db` was `57.tag`, its rollback journal `databases/mrbooks.db-journal` was `58.tag`, and `_names.list` was the last entry. There was no WAL file.
- The zip may be truncated: `2026-10-05_AUTO.mrpro` has no central directory and ends at entry 27, before the database and before `_names.list`. So the database has to be recognised by its content, not by the name list (5.1).
- Marks live in the `notes` table: `_id, book, filename, lowerFilename, lastChapter, lastSplitIndex, lastPosition, highlightLength, highlightColor, time, bookmark, note, original, underline, strikethrough, bak`.
  - Rows with a non-empty `bookmark` are bookmarks, not marks, and are ignored.
  - Style: `bak = '1'` is wavy underline, `underline = 1` is underline, `strikethrough = 1` is strikethrough, otherwise highlight. The user's 325 orange wavy marks are `highlightColor = -28160` (`#FFFF9200`) with `bak = '1'`.
  - `original` is the marked text: usually one word, sometimes a phrase or a whole sentence (max 179 characters seen).
  - `time` is epoch milliseconds. `filename` is the absolute path on the phone, e.g. `/sdcard/Books/Greg Egan/Diaspora.epub`.
- Combinations in the user's backup: wavy orange 325, underline `#FF555555` 110, underline `#88888888` 83, highlight `#7700AAFF` 69, underline orange 3, wavy `#88888888` 2.
- Book formats behind the wavy marks: EPUB (most), FB2 and `.fb2.epub` (the newest mark is in an FB2), MOBI (15 marks in one book).
- Locating a mark in an EPUB: `lastPosition` is a character offset into the chapter's plain text, accurate to within about 100 characters. `lastChapter` is not reliably the spine index: in *Diaspora* it matched exactly, in *Schild's Ladder* the real spine index was 5–6 higher. The app therefore finds the mark by searching for `original` in the book text, and uses the chapter and position only to choose between several occurrences.

## 3. User flows

The prototype is the source of truth for layout, copy and motion. This section names the behaviour behind it.

### 3.1 First run

Setup is a short sequence of full-screen steps:

1. Welcome.
2. Moon+ backup folder, chosen with the system folder picker (SAF). The app reports how many backups it found and the newest one's date. Books folder is optional on the same step and lets the app read the sentence around each mark. Both folders are kept with `takePersistableUriPermission`.
3. AnkiDroid access. The app requests `com.ichi2.anki.permission.READ_WRITE_DATABASE`. If AnkiDroid isn't installed, the step links to it in the Play Store. If permission is denied, the step explains why it's needed and offers to ask again.
4. Translation service: pick a provider (OpenRouter preselected), paste your own API key, and run a connection test. The app never ships or proxies a key.
5. Ready: offers to import the newest backup now.

After setup the app always opens on the Inbox. There is no Home screen.

### 3.2 Import

Started by the Import button on the Inbox, or by sharing a `.mrpro` file to the app. A shared file is copied into the app's cache before anything else, because access to it ends when the receiving screen closes.

The import sheet shows:

- the backup to use (newest complete one in the folder, or the shared file), with Change;
- every style and color combination found in that backup, with total and new counts, as checkboxes. The selection is remembered for next time. On first use, wavy underlines are preselected;
- After translating: Review first or Send to Anki. The default comes from Settings.

Each mark is turned into a card once. An import processes marks the app has never seen, plus marks it saved earlier that still have no card because an import was interrupted or stopped. A mark whose card was discarded stays discarded.

The import then runs in the background (see 5.7). The Inbox shows progress per book. In Review first mode, cards appear in the Inbox as their batch finishes. In Send to Anki mode, cards are sent when their batch finishes, except cards labelled "Already in Anki" (5.6) and failures, which stay in the Inbox for the user to decide.

When it finishes, the import reports: new cards, marks that joined an existing card ("already a card"), marks skipped because their card was discarded earlier, and failures.

The first import of the user's backup will bring in all 325 wavy marks since 2019. That is intended: the Inbox can discard a whole book at once.

### 3.3 Inbox

Cards waiting for review, grouped by book (newest marks first), with a language filter (chips: All, then each language present).

- Tap opens the card page.
- Long-press selects; keep the finger down and drag to select a range. The list scrolls when the finger nears the top or bottom edge. Each group header has a checkbox for the whole book. The top bar offers select all and clear.
- With a selection, a floating toolbar offers Send N, Discard and Translate again.
- Swipe right sends a card, swipe left discards it, past 35% of the row width.
- Sending and discarding show a snackbar with Undo. Undoing a send deletes the notes it created in AnkiDroid and puts the cards back (5.5).
- A card without a translation can't be sent; sending a selection that contains such cards sends the rest and says how many stayed.
- Small labels on a row: phrase (the model widened the word into a phrase), example written by the model (the sentence wasn't found), translation failed, already in Anki (the word is in a note that didn't come from this app, with that note's deck name; 5.6).
- Empty state invites an import.

### 3.4 Card page

Shows the card's front and back as fields that can be edited in place: word, grammar, excerpt, translation, meaning, full sentence. Actions: Send to Anki and Discard (pending card), Save changes to Anki and Delete from AnkiDroid (sent card), Preview in AnkiDroid, Translate again, Restore (discarded card).

A sent card's page reads the note fresh from AnkiDroid when it opens, so it shows edits made in AnkiDroid or synced from desktop, and saving never overwrites them with older text. Saving updates that note's fields, which keeps its review history. If the note turns out to be gone, the page says it was deleted in AnkiDroid and offers to send it as a new card.

### 3.5 Library and book page

Library lists books with cards in AnkiDroid and pending counts. A book page shows its pending cards, then its latest 50 cards in AnkiDroid ("Showing the latest 50. All N are in AnkiDroid."), then a collapsed "Discarded" section. All open the card page.

### 3.6 Settings

- Translation: saved services and the active one (5.3); Translate into (target language, default Russian; changing it affects only new cards).
- Moon+ Reader: backup folder, books folder, Review before sending (default for the import sheet).
- Cards: longest excerpt (6–15 words, default 15), deck pattern (`Moon+::{language}`), book tag pattern (`book::{title}`), card design preview.
- AnkiDroid: connection state.
- Look: theme (System, Light, Dark), app language (Same as phone, English, Russian).

Every language choice uses one searchable picker: suggested languages first, then all, each shown in the UI language and in its own name; search also matches English and Russian names and the code.

## 4. Cards

One card per meaning. If the user marked the same word in the same sense twice, the second mark links to the existing card instead of creating a new one (see 5.4). The card keeps its first sentence; later marks only link to it. A card deleted in AnkiDroid counts as discarded: the user removed it on purpose, so a later mark in the same sense doesn't bring it back.

### 4.1 Note type

The app creates its own note type "Moon+ Vocab" once and stores its id (`mid`) in settings. On each start it checks the id still exists with `getFieldList(mid)`; only if it doesn't does it look for the name, and then create a new note type.

| Field | Content |
|---|---|
| Word | Dictionary form, or the phrase if widened, e.g. `die Schnalle`, `get the upper hand` |
| Grammar | Short grammar note in the source language's dictionary style, e.g. `f., -n`, `v.`, `phr.` |
| Excerpt | 6 to N words from the book around the focus, with the focus wrapped in `<span class="mark">` |
| Translation | Translation into the target language, comma-separated senses, the one used here first |
| Meaning | One line, in the target language, explaining the meaning in this sentence; may be empty |
| Sentence | The full sentence from the book, focus marked the same way |
| Book | `Title, Author` |
| Source | Empty, or a short note in the target language when the example was written by the model |
| AppId | The card's UUID, so a note can always be traced back |

The app always addresses fields by name. Before every write it reads the note type's current field list and the note's current values, replaces only the fields above, and passes any other fields through unchanged. This keeps working if the user adds or reorders fields on desktop. If one of the fields above has been removed or renamed, the write stops with "the Moon+ Vocab note type was changed" (section 8).

Word is the first field, so two notes with the same lemma and different meanings will carry AnkiDroid's duplicate marker in its editor. That is expected and harmless.

Front template: Word, Grammar, Excerpt. Back: front, then Translation, Meaning, Sentence, Book, Source. Styling follows the prototype's AnkiDroid preview, except the font: book text uses the system serif, because AnkiDroid's media API accepts only images and audio. The focus gets the orange wavy underline (`#D9480F` light, `#FF8A50` in AnkiDroid's night mode). The layout is centered.

The templates and CSS carry a version number. When the app ships a newer version, it updates the note type's templates and CSS through the provider; notes are not touched.

### 4.2 Deck and tags

Deck: the deck pattern with `{language}` replaced by the source language's English name (`Moon+::German`). Created if missing. Tags: the book tag pattern with `{title}` replaced by the book title, every character other than letters, digits and `-` replaced by `_`, plus `moon-vocab`.

## 5. Architecture

Kotlin, Jetpack Compose, single Gradle module `:app`. Material 3 Expressive components (`MaterialExpressiveTheme`, floating toolbar, wavy progress) are only in the `androidx.compose.material3:material3` 1.5.0 line; the 1.4.0 stable release dropped them. The project pins the newest 1.5.0 beta or later explicitly, outside the Compose BOM if the BOM lags. minSdk 31 (dynamic color is then always available), target the current SDK. Manual dependency wiring through one `AppContainer`; no DI framework.

```
ui/          Compose screens, ViewModels, theme
domain/      pure Kotlin: models, selection logic, excerpt cutting, text normalization, prompts
data/
  backup/    BackupReader: .mrpro → marks
  books/     BookLocator, EpubText, Fb2Text, SentenceFinder
  llm/       LlmClient (OpenAI-compatible), TranslationService
  anki/      AnkiGateway (AnkiDroid API)
  db/        Room
  settings/  DataStore
work/        ImportWorker
```

Each unit below has one job and an interface that can be faked in tests.

### 5.1 BackupReader

`fun read(file: File): BackupContents` where `BackupContents` is `Complete(marks, bookmarksSkipped)` or `Incomplete(reason)`. The caller first copies the backup (from the folder or a share) into the cache.

Streams the zip with `ZipInputStream`, which reads entries in order without needing the central directory. The database is the first entry whose content starts with the SQLite header `SQLite format 3\0` and has a `notes` table; `_names.list` is not needed. The entry is copied to the cache and opened read-only with `SQLiteDatabase`. The rollback journal is ignored, so the database file as written is the source of truth. Any exception while reading or copying the database entry gives `Incomplete`. The temporary copy is deleted afterwards.

`notes` rows become `RawMark(key, bookPath, bookTitle, chapter, position, text, style, color, markedAt)`.

`key` identifies a mark across backups: a hash of the lowercased file name without directories and `time`. Creation time doesn't change when the user extends or recolours a mark. If two marks in one book have the same `time`, `original` is added to the key. Moon+ restores rewrite `_id`, so it is not used.

Backup listing: the newest `*.mrpro` in the folder by modification time; if it is incomplete, the next one, and the sheet says which backup was skipped and why.

### 5.2 Books and sentences

`BookLocator` maps a mark's `filename` to a document in the books folder: it indexes the folder tree once per import (relative path → document URI) and matches the longest common path suffix. `/sdcard/Books/Greg Egan/Diaspora.epub` matches `Greg Egan/Diaspora.epub` under the chosen folder.

`EpubText` and `Fb2Text` turn a book into chapters of plain text and report the book's language (`dc:language` for EPUB, `title-info/lang` for FB2, normalized to an ISO 639-1 code). `.fb2.epub` is an EPUB; `.fb2.zip` is an FB2 inside a zip. Other formats yield nothing.

Text normalization is applied to the book text and to `original` before searching. It:

- removes soft hyphens (U+00AD);
- turns non-breaking and other Unicode spaces into spaces and collapses runs of whitespace;
- applies Unicode NFC;
- makes curly and straight apostrophes and quotes equal.

The normalized text keeps an offset map back to the original, so the sentence shown on the card is the book's own text.

`SentenceFinder.find(book, mark): Found(sentence, markSpan) | NotFound`:

1. Search every chapter for the normalized `original`, ignoring case. A match starts a word but may end inside one: Moon+ saves "behoove" for a selection in "behooves", and "hose" must not match inside "those".
2. Use only the occurrence that is certainly the marked one, otherwise no sentence (the card then gets a model-written example). Moon+ chapter numbers follow the EPUB spine in some books and not in others, so the evidence is, in order:
   - the text occurs once in the book;
   - exactly one occurrence lies between the book's neighbouring anchors, in Moon+ order. Anchors are the book's other notes in the same backup, of any style, whose text occurs once in the book; anchors out of reading order are dropped;
   - an anchor in the same Moon+ chapter puts exactly one occurrence within tolerance of the mark;
   - when the chapter numbers are proven, exactly one occurrence in the saved chapter sits at the saved position, or else within tolerance of it. FB2 chapter numbers are proven unless an anchor contradicts them; EPUB chapter numbers are proven when every anchor sits in its saved chapter near its saved position.

   Tolerance is 40 characters plus a tenth of the distance, since Moon+ counts whitespace its own way.
3. Take the enclosing sentence with `BreakIterator.getSentenceInstance(locale)`. If `original` is itself longer than one sentence, use it as the sentence. `BreakIterator` sometimes splits at abbreviations such as "z. B." or inside dialogue; if the found sentence is shorter than 6 words, the neighbouring sentence on each side is added.

Language falls back to the model's detection (5.3) when the book is missing or has no language metadata. A book's language, once known, is stored on the book.

### 5.3 Translation

`LlmClient` speaks the OpenAI chat-completions protocol over OkHttp with kotlinx.serialization. Every request uses the active service's base URL, key and model, sets `max_tokens`, and has a 60-second timeout.

The app is bring-your-own-key only. The user keeps a list of saved services, and exactly one is active. A saved service has:
- a name;
- a base URL;
- its own API key;
- a model.

The user can add, edit, duplicate, delete and switch services. The list is never empty. New services start from a built-in template, which only fills in the name and base URL:
- OpenRouter (`https://openrouter.ai/api/v1`): the first-run default. Its default model is picked during implementation from OpenRouter's catalog: a small, cheap model that supports structured outputs.
- A few other OpenAI-compatible providers. The exact list and URLs are checked against each provider's docs when milestone 5 is planned.
- Custom: an empty base URL.

Two saved services may share a base URL with different models, for example a cheap model and a stronger one. Where the service lists models at `GET {baseUrl}/models`, the model field offers them as suggestions; free text is always allowed. The connection test sends one tiny request with the service's own settings. Switching the active service affects only translations made after the switch. Providers without structured outputs still work, through the fallback described below.

`TranslationService.translate(batch, targetLanguage, maxExcerptWords)` sends up to 10 items per request. Each item is `{id, marked, sentence?, bookTitle, sourceLanguage?}`. The request asks for JSON matching a schema (`response_format: json_schema`, with a fallback to extracting the first JSON object from the reply when the provider doesn't support it):

```json
{"items": [{
  "id": "…",
  "sourceLanguage": "de",
  "focus": "Schnallen",
  "headword": "die Schnalle",
  "lemma": "Schnalle",
  "widened": false,
  "grammar": "f., -n",
  "translation": "пряжка",
  "meaning": "…",
  "excerpt": "…mit verschiedenen Falten, Taschen, [Schnallen]…",
  "inventedExample": null
}]}
```

Rules the prompt states and the app checks:

- `focus` is the word or phrase to learn, exactly as it appears in the sentence. Usually that is the marked text. When the user marked a long phrase or a whole sentence, the model picks the word or phrase inside it that is worth learning. The app accepts `focus` only if it occurs in the sentence; the occurrence nearest the mark is used.
- `headword` is the dictionary form shown on the card, with an article where the language uses one. `lemma` is the same without article or particle; it is used for searching (5.4, 5.6).
- The model may widen the focus into the phrase it belongs to (`upper hand` → `get the upper hand`); then `widened` is true and `focus` covers the phrase as it appears in the text.
- `excerpt` is needed only when the sentence is longer than `maxExcerptWords`; a shorter sentence is the excerpt as is. The brackets in `excerpt` mark the focus. The app accepts it only if, after removing brackets and ellipses, it is a contiguous part of the sentence, contains the focus, and is within the word limit. Otherwise the app cuts the excerpt itself: a window of words centered on the focus, with ellipses. If the focus alone is longer than the limit, the excerpt is the focus.
- When there is no sentence, the model writes a short example as `inventedExample`; the card gets the Source note and the "example written by the model" label.

Items missing from the reply, or failing validation, become cards with the failed label and can be translated again.

Errors:

- Network errors, 429 and 5xx responses are retried twice with backoff, honouring `Retry-After`; then the batch fails.
- 401 and 403 (key rejected) and 402 (no credits) stop the import, because every later batch would fail the same way. The marks keep no card, so the next import picks them up (3.2).

### 5.4 One card per meaning

After a batch is translated, for each item whose `lemma` and source language match existing cards (pending, sent, discarded or deleted in Anki), the app makes one more small request: given the new sentence and the existing cards' translation and meaning, is this the same sense as one of them? Items in the same batch are compared with each other the same way.

- If the matching card is pending or sent, the mark links to it and no new card is made. This counts as "already a card".
- If the matching card is discarded or deleted in Anki, the mark links to it and nothing is created. This counts as "skipped, discarded earlier".

### 5.5 AnkiDroid

`AnkiGateway` wraps the AnkiDroid `AddContentApi` (JitPack `com.github.ankidroid:Anki-Android:api-v1.1.0`) and, where the wrapper falls short, AnkiDroid's content provider (`FlashCardsContract`) directly:

- `ensureNoteType()`: by stored `mid` (4.1). It creates the note type with `addNewCustomModel`, sort field 0 and no default deck, and updates templates and CSS when their version changes.
- `ensureDeck(name)`.
- `add(card): noteId`.
- `update(noteId, card)`: reads the field list and the note's current fields, maps the fields by name (4.1), then calls `updateNoteFields` with the full array and `updateNoteTags`. The provider requires the full array in the note type's current order.
- `delete(noteId)`: provider delete on the note's URI.
- `read(noteIds): Map<noteId, NoteSnapshot>`: a raw provider notes query with the search `nid:id1,id2,…`, in chunks of 200, with `mod` in the projection; `NoteInfo` from the wrapper has no modification time. A snapshot has fields, tags and modification time; missing ids are absent from the map.
- `findElsewhere(terms): String?`: the deck name of the first hit, see 5.6.

Provider facts the design relies on, checked against AnkiDroid's source:

- The notes query takes Anki browser search syntax over the whole collection, and returns a null cursor, not an empty one, when nothing matches.
- `nid:` accepts a comma-separated list of ids.
- Delete handles a single note.
- Note `mod` is in seconds.

Each call is checked again on the device in milestone 1 against the user's AnkiDroid version.

The manifest declares `<queries><package android:name="com.ichi2.anki"/></queries>` and the permission.

Sending is immediate. Undo deletes the notes that send created and returns the cards to the Inbox. Discard is local and is undone by restoring the cards' status.

### 5.6 Keeping in step with AnkiDroid

AnkiDroid holds cards from many sources: shared decks, cards added by hand, other apps. The app follows one rule: **it reads, changes and deletes only notes it created**, identified by the note ids stored in `card.ankiNoteId`. Every sync query is `nid:` with those ids. A note of the "Moon+ Vocab" type that the app has no record of, such as one made by hand on desktop, is left alone.

For the app's own cards, AnkiDroid is the source of truth once a card is sent. Room keeps the app's copy for lists and for pending cards.

- **Check.** After every import, and on app start if the last check was more than 10 minutes ago, `read()` all sent cards' note ids. Because `mod` has one-second resolution, the app compares fields as well as `mod`, and copies changed fields into Room. A missing note sets the card's status to `deleted_in_anki`, which drops it from the Library counts. The check runs in the background and doesn't block the Inbox.
- **Before editing.** The card page reads its note fresh (3.4).

The only wider query is the check for words the user already learns from another source. For each new card, after translation, `findElsewhere` searches `-note:"Moon+ Vocab"` together with `w:` for each of `lemma`, and `focus` when it differs. A term with spaces is quoted as `"w:get the upper hand"`. Anki's special characters (`*`, `_`, `\`, `"`, `:`, `(`, `)`, and a leading `-`) are escaped as the Anki manual describes. A null cursor means no hit. For the first hit the app reads the note's first card to get its deck name. Other note types' fields are unknown, so the app doesn't try to judge whether the sense is the same. A hit sets the card's "Already in Anki" label with that deck name, and the card waits in the Inbox even in Send to Anki mode. The search reads only note ids and deck names and never changes those notes.

### 5.7 Background import

`ImportWorker` is a unique, non-expedited `CoroutineWorker`. It calls `setForeground` with a `dataSync` foreground service type and a progress notification, because an import of hundreds of marks can take minutes and must survive the user leaving the app. The manifest declares:

- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC`;
- WorkManager's `SystemForegroundService`, with `foregroundServiceType="dataSync"`.

Pipeline:

1. Read the backup and store new marks.
2. Collect the marks to process: the new ones, plus earlier marks that still have no card (3.2).
3. Locate sentences.
4. For each batch of 10:
   1. Translate.
   2. Merge same-sense marks (5.4).
   3. Search other sources (5.6).
   4. Write the cards and link their marks in one transaction.
   5. In Send to Anki mode, send the eligible cards.
5. Check the app's notes in AnkiDroid (5.6).

Committing per batch means an interrupted import loses at most the batch in flight. The next import resumes with the marks that have no card. Progress is written to Room, and the Inbox observes it. Starting an import while one runs is not possible; the Import button shows progress instead.

### 5.8 Storage

Room tables:

- `book`: id, file name key, title, author, language, document URI.
- `mark`: id, key (unique), book id, chapter, position, text, style, color, marked at, card id (nullable: no card yet).
- `card`: id, uuid, book id, source language, target language, word, lemma, grammar, excerpt, translation, meaning, sentence, source note, flags (widened, invented), elsewhere deck (nullable, the "Already in Anki" label), status, anki note id, anki modification time, sent at, error.

Card status:

| From | Event | To |
|---|---|---|
| (new) | translated | `pending` |
| (new) | translation failed or invalid | `failed` |
| `failed` | Translate again succeeds | `pending` |
| `pending` | Send | `sent`, with anki note id |
| `sent` | Undo send | `pending`, anki note id cleared |
| `pending`, `failed` | Discard | `discarded` |
| `discarded` | Undo, or Restore on the card page | previous status |
| `sent` | Delete from AnkiDroid | `discarded`, anki note id cleared |
| `sent` | check finds the note missing | `deleted_in_anki` |
| `deleted_in_anki` | Send as new card | `sent`, with the new note id |
| `pending`, `sent` | Translate again | same status; for `sent`, the new fields are saved to the note only when the user saves |

Settings, including the saved services and their API keys, live in DataStore in app-private storage. The services are stored as one JSON list plus the active service's id. App data is excluded from cloud and device-transfer backup.

Limitation: after a reinstall the app has no record of the notes it created, and importing would create duplicates of them. Re-adopting notes by their AppId field is left for later.

## 6. UI implementation

Material 3 Expressive in Compose (`MaterialExpressiveTheme`, expressive motion scheme, material3 1.5.0 line, see 5), matching the prototype:

- Color: dynamic tonal-spot scheme from the wallpaper. Light, dark, or system.
- The one fixed color is the mark: `#D9480F` light, `#FF8A50` dark, drawn as a wavy underline under the focus wherever an excerpt appears.
- Type: Roboto Flex for the UI and Literata for book text, both bundled as variable fonts.
- Inbox: segmented grouped list (24dp outer, 6dp inner radius, 2dp gaps), selected rows morph to full radius, floating toolbar with a "Send N" FAB, extended Import FAB that shrinks on scroll, wavy progress indicator, navigation bar with a badge on Inbox.
- Range selection: long-press (about 360ms), then drag. Rows under the finger come from `LazyListState.layoutInfo`. Selection is a pure reducer over (snapshot at long-press, anchor, current row), so it is unit-tested without UI. Auto-scroll within 72dp of either edge.
- Swipe with `SwipeToDismissBox`, threshold 35%, haptic tick when crossing it.
- Navigation with Navigation 3: three top-level destinations (Inbox, Library, Settings) and pushed pages (card, book, language picker, service, cards, AnkiDroid preview, setup).
- Predictive back throughout. Back closes a sheet, then a page, then leaves selection mode.
- Edge-to-edge.

## 7. Localization

English and Russian at launch, built so more languages are only new resource files: all copy in string resources, counts through `plurals`, `locales_config.xml`.

The per-app language is set with `AppCompatDelegate.setApplicationLocales`. `LocaleManager` only exists from API 33. The activity is an `AppCompatActivity`, and the manifest enables `AppLocalesMetadataHolderService` with `autoStoreLocales` for API 31–32. Language names come from `Locale.getDisplayLanguage` and are sorted with `Collator` in the UI language.

## 8. Errors

Every failure tells the user what happened and what to do, in the interface's voice:

| Situation | Behaviour |
|---|---|
| Backup folder unreadable (permission revoked) | Settings row and import sheet ask to choose the folder again |
| Newest backup incomplete | Use the next newest; the sheet names the skipped file |
| No complete backup | Sheet says so and offers Change |
| Book not found or unsupported format | Card gets a model-written example and its label |
| Translation request fails after retries | Batch's cards get the failed label; Translate again retries them |
| Key rejected (401/403) or no credits (402) | Import stops; unprocessed marks wait for the next import; snackbar opens Translation service |
| Import interrupted (process killed, reboot) | Next import resumes with marks that have no card |
| AnkiDroid missing or permission revoked | Cards stay pending; a banner explains and offers to fix it; the check (5.6) is skipped |
| Moon+ Vocab note type changed so a field is missing | Writes stop; a banner names the missing field |
| Note deleted in AnkiDroid | Card becomes `deleted_in_anki`; its page offers to send it as a new card |
| Undo after a send, but AnkiDroid refuses the delete | Snackbar says the cards stay in AnkiDroid; cards keep `sent` |

## 9. Testing

JVM unit tests:

- `BackupReader` on a fixture `.mrpro` built in the test from a small SQLite database with the real schema. Cases: a complete zip, a zip truncated before the database, a zip truncated inside the database, and no `_names.list`.
- Mark key stability when `original` changes.
- Text normalization and its offset map.
- `SentenceFinder` on small EPUB and FB2 fixtures, covering:
  - a shifted chapter number;
  - repeated words, placed by anchors, by the saved position, or not at all;
  - a mark inside a longer word;
  - soft hyphens;
  - an abbreviation split;
  - a mark longer than one sentence.
- Excerpt validation and cutting, including a focus longer than the limit.
- Selection reducer.
- Deck and tag pattern expansion, including tag sanitizing.
- Anki search term escaping.
- Field mapping by name, with extra and reordered fields.
- The card status transitions.

Other tests:

- `LlmClient` and `TranslationService` against MockWebServer, including:
  - malformed or truncated JSON;
  - missing items;
  - 429 with `Retry-After`;
  - 402;
  - a provider without structured-output support.
- `ImportWorker` resumption: interrupt after the first batch, run again, expect no lost and no duplicated marks.
- The AnkiDroid check as a pure function over (cards in Room, snapshots from AnkiDroid) → updates: changed note, missing note, untouched note.
- `AnkiGateway` behind an interface with a fake for ViewModel tests.
- One instrumented test on the device against real AnkiDroid, run only after checking in with the user. It uses a dedicated test deck and cleans up after itself. It covers:
  - add, update, delete and the `nid:` read;
  - the other-source search;
  - a note from another deck is never changed.

## 10. Milestones

Each milestone gets its own implementation plan, written when the previous one is done. Dependency choices that shape the scaffold (material3 1.5.0 line, minSdk 31, AppCompat for per-app language) are fixed at the start of milestone 1.

1. **Pipeline.** Project scaffold, Room, BackupReader, BookLocator, EPUB and FB2 text, normalization, SentenceFinder, LlmClient and TranslationService, AnkiGateway (note type, deck, add, update, delete, read, check), resumable ImportWorker, and a plain debug screen to pick folders, enter a key, import one book and send.
   - While developing, sends go to `Moon+::Test`, and the debug screen has "Delete every note this build created".
   - Done when one real book from the user's backup produces sensible cards in AnkiDroid.
2. **Merge and other sources.** Same-sense merge and the "Already in Anki" search, tuned on the real headwords from milestone 1. The full real backup is imported into the real decks at the end of this milestone.
3. **Inbox.** Theme, fonts and the mark underline, Inbox list with groups, filter and labels, selection with drag and select all, swipe, floating toolbar, send with undo, import sheet, progress.
4. **Cards and library.** Card page with fresh read, editing, update in place, delete from AnkiDroid and restore, AnkiDroid preview, Library, book page.
5. **Settings and first run.** Settings pages, language picker, translation service page with connection test, first-run setup, Russian strings, share target, predictive back polish.

## 11. Open questions

- Is the truncated `2026-10-05_AUTO.mrpro` how Moon+ wrote it, or was it cut in transfer? If Moon+ writes incomplete auto backups often, the sheet should prefer manual backups.
- Mark `time` is assumed unchanged across backups. Comparing two complete backups confirms it; the next complete backup the user exports is enough.
- Which MIME type Moon+ itself uses when sharing a `.mrpro`. Android's Files app sends `application/octet-stream` (checked on the emulator in milestone 5). The app accepts `*/*` and checks the content, so this only matters if the filter is ever narrowed.

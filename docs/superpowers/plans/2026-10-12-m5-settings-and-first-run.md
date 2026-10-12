# Moon+ Vocab milestone 5 (Settings and first run) implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A new install walks the user through a short setup: backup folder, AnkiDroid access and a translation key. The Settings tab replaces the debug screen with real pages:
- translation services, with a connection test and model suggestions;
- one searchable language picker;
- the card settings, with a sample card preview;
- theme and app language.

The app speaks Russian as well as English. A `.mrpro` shared from a file manager opens the import sheet. Pages slide in and out with predictive back.

**Architecture:**
- Logic that can break lives in unit-tested code:
  - `domain/settings/Services.kt` (pure): service templates and the list operations (add, duplicate, delete), which keep the list non-empty and one service active;
  - `domain/settings/Languages.kt` (pure): the language list, the suggested languages and the search;
  - `data/llm/ModelCatalog.kt`: `GET {baseUrl}/models`;
  - `data/llm/ConnectionTest.kt`: one real translation of a sample word with a service's own settings;
  - `Settings` gains `theme`, `setupDone` and `ankiAsked`.
- The Compose layer stays thin and has no unit tests.
- The debug screen stays, reachable as "Developer tools" at the bottom of Settings, until the user has finished the phone checks.

**Tech Stack:** Unchanged from milestone 4. No new dependencies.

**Spec:** [`docs/superpowers/specs/2026-10-11-moon-vocab-design.md`](../specs/2026-10-11-moon-vocab-design.md). Section numbers (§n) refer to it:
- §3.1 First run;
- §3.2 Import (shared file);
- §3.6 Settings;
- §5.3 Translation (saved services, templates, connection test, model list);
- §6 UI implementation (Navigation 3, predictive back);
- §7 Localization;
- §11 Open questions (share MIME type).

Current decisions are in [`docs/DECISIONS.md`](../../DECISIONS.md).

**Layout and copy reference:** [`docs/design/prototype.html`](../../design/prototype.html).
- Settings is `renderSettings`; the picker is `pickerList` and `PAGES.picker`; the service page is `PAGES.service`; the card settings `PAGES.cards`; first run `PAGES.setup`.
- English copy is in `T.en` and `Object.assign(T.en, { x: {…} })`. Russian copy is in `T.ru` and `Object.assign(T.ru, { x: {…} })`.

Read only the parts your task names.

## Decisions made for this milestone

The user said "go" without reviewing choices one by one. These are mine, and each is cheap to change:

1. **The debug screen becomes "Developer tools"**: a page pushed from the last row of Settings. It keeps every tool it has, including the test deck switch and the cards JSON export, because the milestone 2 phone checks still need them. `deckOverride` still defaults to `Moon+::Test`. When the test deck is on, the Cards page says so under the deck field. Removing the page is a ticket.
2. **Translation services follow the spec, not the prototype's single form.**
   - The Services page lists the saved services, with a radio for the active one. Tapping a row opens its edit page. "Add service" opens a menu of templates.
   - The edit page has name, address, API key (with show/hide), model (with suggestions), Test connection, Duplicate and Delete. Delete asks first and is disabled for the last service.
   - Edits are saved as you type, as on the debug screen.
3. **Templates:** each fills only the name and the base URL (§5.3). The URLs were checked against each provider's docs on 2026-10-12:
   - OpenRouter, `https://openrouter.ai/api/v1`;
   - OpenAI, `https://api.openai.com/v1`;
   - Google Gemini, `https://generativelanguage.googleapis.com/v1beta/openai`;
   - Mistral, `https://api.mistral.ai/v1`;
   - DeepSeek, `https://api.deepseek.com`;
   - Groq, `https://api.groq.com/openai/v1`;
   - Custom, with an empty address.
4. **Test connection** translates one fixed sample word with the edit page's current values. The result says how long it took and what came back, for example: Connected. "Schnalle" came back as "пряжка" in 1.4 s. A failure names its cause: key rejected, no credits, check the address and model, or the server's message.
5. **Model suggestions** come from `GET {baseUrl}/models` with the service's key. They are fetched when the edit page opens and again 600 ms after the address or the key stops changing. The model field filters them as you type and shows at most 50. A failed fetch shows no suggestions and no error.
6. **Language picker.**
   - Translate into: about 80 languages (the prototype's list), searchable.
     - Suggested comes first: the current choice, then the phone's languages, then English, with no repeats.
     - Then all languages, sorted by name in the UI language.
     - Each row shows the name in the UI language and, below it, the language's own name when that differs.
     - Search matches the UI-language name, the own name, the English and Russian names, and the code.
   - A line under the search box says that a change affects only new cards. This replaces the prototype's snackbar, so no message has to pass between pages.
   - App language: Same as phone, English and Русский, without search. It is set with `AppCompatDelegate.setApplicationLocales`.
7. **Theme** is System, Light or Dark, chosen with a segmented button on the Settings page. It is applied with `AppCompatDelegate.setDefaultNightMode`:
   - in `App.onCreate`, from a blocking read of the settings;
   - again whenever it changes.

   Compose's `isSystemInDarkTheme()` then follows the app's choice, so the preview follows it too (ticket mv-8wl5).
8. **Cards page.**
   - "Longest excerpt" is a slider from 6 to 15, as in the spec. The prototype's slider goes to 30 and has an "Excerpt on the front" switch; both are dropped. This goes in `DEVIATIONS.md`.
   - An example front updates live as the slider moves. It is cut with the app's own `cutExcerpt`.
   - The deck pattern and the book tag pattern are text fields with the prototype's help lines. A field left blank saves its default.
   - "Card design" opens the preview with a built-in sample card.
9. **First run.**
   - Setup shows while `setupDone` is false. An install that already has a backup folder counts as set up, so the user's phone skips it.
   - The steps follow the prototype:
     1. Welcome.
     2. Folders. The backup folder is required, and the step says how many backups it found and the newest one's date. The books folder is optional.
     3. AnkiDroid. Continue waits for access. If AnkiDroid isn't installed, the step offers "Get AnkiDroid", which opens the Play Store.
     4. Translation. The step has the active service's key with Test connection, and the Translate into row. "Use another service" opens the Services page. Continue needs a key.
     5. Ready. It counts the new marks in the newest backup for the default mark styles, then offers Import now (the Inbox with the import sheet open) or Later.
   - Back goes to the previous step. On the first step, Back leaves the app.
10. **Share target.**
    - `MainActivity` accepts `ACTION_SEND` with `EXTRA_STREAM` for any MIME type (`*/*`), as §11 plans. It becomes `launchMode="singleTask"`, so a share reaches the running app through `onNewIntent`.
    - The shared file is copied at once by `ImportSheetViewModel.change(uri)`. The app then shows the Inbox with the sheet open. A file that isn't a complete backup gets the existing "incomplete" note.
    - A share that arrives while setup is unfinished is ignored.
11. **AnkiDroid permission (ticket mv-6uj1).** A denial opens the system app settings only when the app has asked before (`ankiAsked`) and Android shows no rationale. The first refusal no longer jumps to settings.
12. **Predictive back and motion.**
    - The manifest opts in with `android:enableOnBackInvokedCallback="true"`.
    - Pushed pages slide in from the end edge over the page below. Pop and predictive pop reverse it.
    - Tab switches keep their cross-fade.
    - The back order stays: sheet, then selection mode, then page.
13. **The Inbox's "Open Settings" button** (import stopped by the translation service) opens the Services page.

## Global Constraints

Paths:
- Main code is under `app/src/main/java/org/lattenwald/moonvocab/`.
- Resources are under `app/src/main/res/`.
- Unit tests are under `app/src/test/java/org/lattenwald/moonvocab/`.

Repository:
- This milestone runs on `master` directly.
- Each task ends with one commit on `master`. The controller stages and commits; subagents do not run `git add` or `git commit`. One-line subject, no attribution of any kind.

Unchanged from milestones 1–4:
- Room stays at schema version 1. No entity changes; a task that finds it must change one stops and asks.
- `deckOverride` still defaults to `Moon+::Test`.
- No DI framework. IO dispatchers are passed in. `MainActivity` stays an `AppCompatActivity` with the AppCompat theme parent.
- Settings store only values that differ from their defaults; writing a default removes its key.
- The API key is never logged and never put in an error message (`LlmClient` redacts it).

Strings:
- Tasks 1–9 add English strings to `values/strings.xml` only, with counts in `plurals`.
- Task 10 adds `values-ru/strings.xml` with every string and plural, and adds `ru` to `locales_config.xml`.

Fixed values:
- Excerpt slider: 6 to 15 words, default 15 (§3.6).
- Model suggestions: refetched 600 ms after the address or key stops changing; at most 50 shown.
- Default target language: `ru`.

Repo rules (`AGENTS.md`):
- No test theater. Compose code has no unit tests. Its logic lives in the files listed under Architecture.
- Deferred work becomes a `tk` ticket.
- The emulator is allowed in this session (the user said so). The phone is not touched.
- Edit files with the file-editing tools. Never use shell redirection, `sed -i` or scripts. Exception: `curl -o` icon downloads.

`docs/DECISIONS.md` and `docs/DEVIATIONS.md`:
- Every task edits the DECISIONS lines its change makes untrue and adds a line for each decision above that it implements.
- Task 5 adds the DEVIATIONS entry for decision 8.

Commands:
- `timeout --signal=KILL 540 make check` runs the unit tests and lint.
- `timeout --signal=KILL 540 ./gradlew :app:testDebugUnitTest --tests '<pattern>'` runs one class.
- `timeout --signal=KILL 540 make debug` checks that the app builds.

Icons:
- Material Symbols Rounded, fetched with `curl -sSfL -o app/src/main/res/drawable/ic_<name>.xml https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/<name>/materialsymbolsrounded/<name>_24px.xml`.
- Then remove the `android:tint` line with the Edit tool.

Out of scope:
- Re-adopting notes after a reinstall (§5.8).
- Removing the Developer tools page.
- Languages other than English and Russian.

## Review Focus

1. **Deleting the active service, or the only one.** The list never becomes empty, and exactly one service stays active. Tested in Task 1 (`deletingTheActiveServiceActivatesItsNeighbour`, `theLastServiceCannotBeDeleted`).
2. **A connection test against a wrong key, address or model.** The page names the cause, and the key never appears in the message. Tested in Task 2 (`testReportsARejectedKey`, `testReportsAWrongAddress`, `testNeverEchoesTheKey`).
3. **A search typed in another script or as a code.** "нем", "deu", "de" and "Deutsch" all find German; a search with no match shows the empty line. Tested in Task 1 (`searchMatchesRussianNativeEnglishNamesAndCode`).
4. **An existing install upgrading to this build.** It must not be sent through setup, and its services, folders and target language stay as they were. Tested in Task 1 (`anInstallWithABackupFolderCountsAsSetUp`, `oldSettingsReadUnchanged`).
5. **A shared file that isn't a Moon+ backup** (a photo, a PDF, a truncated zip). The sheet says the backup is incomplete and nothing is imported. Checked on the emulator in Task 11.

---

### Task 1: Settings fields, services and languages

**Files:**
- Create: `domain/settings/Services.kt`, `domain/settings/Languages.kt`
- Modify: `data/settings/Settings.kt`
- Test: `domain/settings/ServicesTest.kt`, `domain/settings/LanguagesTest.kt`, `data/settings/SettingsTest.kt`

**Interfaces:**
- Produces:
  - `enum class ThemeMode { SYSTEM, LIGHT, DARK }`, in `Settings.kt`.
  - `SettingsSnapshot` gains:
    - `theme: ThemeMode` (default SYSTEM);
    - `setupDone: Boolean` (default: true when `backupTree != null`, else false);
    - `ankiAsked: Boolean` (default false).
  - `data class ServiceTemplate(val key: String, val name: String, val baseUrl: String)` and `val SERVICE_TEMPLATES: List<ServiceTemplate>`, in decision 3's order. Keys: `openrouter`, `openai`, `gemini`, `mistral`, `deepseek`, `groq`, `custom`. Custom's name comes from the UI, so the template holds `""`.
  - `data class ServiceList(val services: List<LlmService>, val activeId: String)`.
  - `fun ServiceList.add(template: ServiceTemplate, name: String, newId: String): ServiceList`. The new service gets the template's base URL, an empty key and model, and becomes active.
  - `fun ServiceList.duplicate(id: String, name: String, newId: String): ServiceList`. The copy keeps the base URL, key and model, sits right after the original, and becomes active.
  - `fun ServiceList.delete(id: String): ServiceList`. With one service, it returns the list unchanged. Deleting the active service activates the next one, or the previous one when it was last.
  - `fun ServiceList.select(id: String): ServiceList`. Ignores an unknown id.
  - `val LANGUAGE_CODES: List<String>`, the prototype's `LANG_CODES` (77 codes).
  - `fun suggestedLanguages(current: String, device: List<String>): List<String>`. Order: current, then the device languages, then `en`. Only codes in `LANGUAGE_CODES`, with no repeats.
  - `fun searchLanguages(codes: List<String>, query: String, ui: Locale): List<String>`. A blank query returns every code. Otherwise it matches, ignoring case, any of: the name in `ui`, the own name, the English name, the Russian name, or the code itself. The result is sorted by the `ui` name with `Collator.getInstance(ui)`.
  - `fun sortedLanguages(codes: List<String>, ui: Locale): List<String>`, sorted the same way.

- [ ] **Step 1: Write the failing tests**
  - `ServicesTest`:
    - `addActivatesTheNewServiceWithTheTemplateUrl`;
    - `duplicateKeepsKeyAndModelAndSitsAfterTheOriginal`;
    - `deletingTheActiveServiceActivatesItsNeighbour`: for [a, b, c] with active b, deleting b leaves active c; with active c, deleting c leaves active b;
    - `deletingAnInactiveServiceKeepsTheActiveOne`;
    - `theLastServiceCannotBeDeleted`;
    - `selectIgnoresAnUnknownId`.
  - `LanguagesTest`:
    - `suggestedPutsCurrentFirstThenDeviceThenEnglishWithoutRepeats`: `suggestedLanguages("ru", listOf("bg", "ru", "xx"))` is `["ru", "bg", "en"]`;
    - `searchMatchesRussianNativeEnglishNamesAndCode`: with `ui = Locale.ENGLISH`, each of "нем", "Deutsch", "germ" and "de" contains `"de"`;
    - `searchWithNoMatchIsEmpty`: "qqq";
    - `sortFollowsTheUiLocale`: with `Locale("ru")`, "en" (Английский) sorts before "de" (Немецкий).
  - `SettingsTest`:
    - `anInstallWithABackupFolderCountsAsSetUp`: a store with only `backupTree` set reads `setupDone == true`; an empty store reads false;
    - `themeRoundTrips`, and `writingSystemThemeRemovesTheKey`;
    - `oldSettingsReadUnchanged`: a store written with milestone 4's keys (services JSON, `activeServiceId`, `targetLanguage`, `backupTree`) reads the same values as before.

- [ ] **Step 2: Run them, expect failures.** `timeout --signal=KILL 540 ./gradlew :app:testDebugUnitTest --tests '*ServicesTest' --tests '*LanguagesTest' --tests '*SettingsTest'`

- [ ] **Step 3: Implement.**
  - The settings keys are `theme` (stored by enum name), `setupDone` and `ankiAsked`.
  - `setupDone`'s default depends on `backupTree`. Store the key only when the value differs from what that default gives.
  - Every function in `Services.kt` and `Languages.kt` is pure. `LlmService` stays where it is.

- [ ] **Step 4: Run the tests; expect them to pass.** Then run `make check`.

- [ ] **Step 5: DECISIONS.** Add lines for:
  - the three new settings and `setupDone`'s default;
  - the templates (decision 3);
  - the delete and duplicate rules;
  - the suggested order and what search matches.

Commit: `feat: settings for theme and setup, service list operations and language search`

---

### Task 2: Model catalog and connection test

**Files:**
- Create: `data/llm/ModelCatalog.kt`, `data/llm/ConnectionTest.kt`
- Modify: `AppContainer.kt` (expose `modelCatalog` and `connectionTest`, built on the existing `http` client)
- Test: `data/llm/ModelCatalogTest.kt`, `data/llm/ConnectionTestTest.kt` (MockWebServer, `RobolectricTestRunner`, as in `LlmClientTest`)

**Interfaces:**
- Produces:
  - `class ModelCatalog(http: OkHttpClient)` with `suspend fun models(baseUrl: String, apiKey: String): List<String>`.
    - It sends `GET {baseUrl without trailing /}/models`, with `Authorization: Bearer <key>` when the key isn't blank.
    - It returns `data[].id`, sorted, with no repeats.
    - Any failure returns an empty list: a bad URL, a key with invalid characters, a network error, a non-2xx status, or JSON that can't be read. It never throws except on cancellation.
  - `sealed interface TestResult`:
    - `data class Ok(val millis: Long, val word: String, val translation: String)`;
    - `data class Failed(val kind: LlmException.Stop.Kind?, val message: String)`, where `kind` is null for `LlmException.Failed` or an empty reply.
  - `class ConnectionTest(http: OkHttpClient, clock: () -> Long)` with `suspend fun run(service: LlmService, targetLanguage: String): TestResult`.
    - It builds an `LlmClient` with the given service's base URL, key and model. A `sleep` that returns at once means a 429 or 5xx is not waited out during a test.
    - It translates one item through `TranslationService`:
      - id `test`, marked `Schnallen`;
      - sentence `Der Mantel hatte viele Taschen und Schnallen.`;
      - book title `Test`, source language `de`;
      - `maxExcerptWords = 15`.
    - Ok carries the headword and the translation.

- [ ] **Step 1: Write the failing tests**
  - `ModelCatalogTest`:
    - `parsesIdsSortedWithoutRepeats`;
    - `sendsTheKeyAsBearer`;
    - `failureGivesAnEmptyList`: cases 401, `not json`, and a base URL `"nonsense"`.
  - `ConnectionTestTest`:
    - `okCarriesHeadwordAndTranslation`: a reply with one valid item for id `test`;
    - `testReportsARejectedKey`: 401 gives `Failed(AUTH, …)`;
    - `testReportsAWrongAddress`: 404 gives `Failed(CONFIG, …)`;
    - `anEmptyReplyIsFailed`: a reply whose items list is empty gives `Failed(null, …)`;
    - `testNeverEchoesTheKey`: a 400 whose body contains the key; the message holds `***` and not the key.

- [ ] **Step 2: Run them, expect failures.**

- [ ] **Step 3: Implement.** No changes to `LlmClient`.

- [ ] **Step 4: Run the tests, then `make check`.**

- [ ] **Step 5: DECISIONS.** Add lines for decisions 4 and 5: what the test sends, and that a catalog failure is silent.

Commit: `feat: model list and connection test for translation services`

---

### Task 3: Language picker page

**Files:**
- Create: `ui/settings/LanguagePicker.kt`
- Modify: `ui/Nav.kt` (add `@Serializable data class LanguageKey(val kind: LanguageKind)` and `enum class LanguageKind { TARGET, APP }`), `ui/AppShell.kt` (entry), `ui/common/LanguageName.kt` (add a non-Composable `languageName(code: String, inLocale: Locale): String`; the Composable calls it), `values/strings.xml`

**Interfaces:**
- Consumes: `LANGUAGE_CODES`, `suggestedLanguages`, `searchLanguages` and `sortedLanguages` (Task 1).
- Produces: `@Composable fun LanguagePicker(kind: LanguageKind, onBack: () -> Unit)`, a page with a top app bar and a back arrow.
  - TARGET:
    - a search field;
    - the line "Changing this affects only new cards.";
    - Suggested and All languages groups as radio rows, in the Inbox's segmented row style;
    - while searching, only the matches, in one group, or "No language matches “q”".
    - Picking a language writes `targetLanguage` and pops.
  - APP:
    - three radio rows: "Same as phone (<phone language>)", "English" and "Русский". Each row's own name is shown as written here.
    - Picking calls `AppCompatDelegate.setApplicationLocales`: empty for Same as phone, else `forLanguageTags("en"|"ru")`. Then it pops.
    - The current choice comes from `AppCompatDelegate.getApplicationLocales()`.
  - The device languages for Suggested come from `LocaleListCompat.getAdjustedDefault()`, which lists the phone's languages, not the app's override.
  - The page reads and writes settings through a small `LanguagePickerViewModel` (factory as in the other ViewModels, writes on `io`).

- [ ] **Step 1: Implement.** Copy: `pkTarget`, `pkApp`, `pkSearch`, `pkSug`, `pkAll`, `pkNone`, `samePhone` and `clear` from the prototype. Add `settings_target_note` = "Changing this affects only new cards."
- [ ] **Step 2:** `make debug`, then `make check`.
- [ ] **Step 3: DECISIONS.** Add lines for decision 6's picker rules.

Commit: `feat: searchable language picker`

---

### Task 4: Translation services pages

**Files:**
- Create: `ui/settings/ServicesPage.kt`, `ui/settings/ServicePage.kt`, `ui/settings/ServicesViewModel.kt`
- Modify: `ui/Nav.kt` (`data object ServicesKey`, `data class ServiceKey(val id: String)`), `ui/AppShell.kt`, `values/strings.xml`

**Interfaces:**
- Consumes: `ServiceList` operations and `SERVICE_TEMPLATES` (Task 1), `ModelCatalog` and `ConnectionTest` (Task 2).
- Produces:
  - `ServicesPage(onBack, onOpen: (String) -> Unit)`: a radio list of saved services, active one checked. Each row shows the name and the model, or "No model set". Tapping the radio selects the service; tapping the row opens it. "Add service" opens a menu of templates; the new service opens at once.
  - `ServicePage(id: String, onBack: () -> Unit)`:
    - fields: name, address (URL keyboard), API key (password, with a show/hide icon and the help line "Stored only on this phone."), and model (with an anchored suggestion menu);
    - Test connection, with a wavy progress while it runs, then the result line;
    - an overflow menu with Duplicate and Delete (confirm dialog, disabled when only one service is left).
    - A service that no longer exists pops the page.
  - `@Composable fun ServiceForm(…)`, the fields plus Test connection, which Task 7 reuses for the key step. Give it parameters for which fields to show.
  - One `ServicesViewModel`, created per entry, owns the edits, the debounced catalog fetch and the test.
  - Edits go through `Settings.update` as typed. The text fields keep local state keyed by service id, as `DebugScreen.ServiceField` does, so a write never moves the cursor.

- [ ] **Step 1: Implement.**
  - Copy: `svTitle`, `svAddr`, `svKey`, `svKeyHelp`, `svModel`, `svShow`, `svHide`, `svTest` and `svTesting` from the prototype.
  - Add: "Translation services", "Add service", "Custom", "No model set", "Duplicate", "Delete", "Delete this service?", "%1$s copy".
  - Result lines:
    - `Connected. “%1$s” came back as “%2$s” in %3$s s.`;
    - "The service rejected the key.";
    - "No credits left on this account.";
    - "Check the address and the model.";
    - "The test failed: %1$s".
  - Seconds are formatted with one decimal in the UI locale.
- [ ] **Step 2:** `make debug`, then `make check`.
- [ ] **Step 3: DECISIONS.** Add lines for decision 2 (edits saved as typed; delete confirms) and decision 5 (the 600 ms debounce, 50 suggestions).

Commit: `feat: translation services list and service page with connection test`

---

### Task 5: Cards settings page and sample preview

**Files:**
- Create: `ui/settings/CardsPage.kt`, `domain/card/SampleCard.kt`
- Modify: `ui/Nav.kt` (`data object CardsSettingsKey`; `PreviewKey(val cardId: Long?)`, where null means the sample card), `ui/card/PreviewPage.kt` (a null id previews `sampleCardFields`), `ui/AppShell.kt`, `values/strings.xml`, `docs/DEVIATIONS.md`

**Interfaces:**
- Produces:
  - `fun sampleCardFields(maxExcerptWords: Int): Map<String, String>`, the Anki fields of a fixed English sample card. It is built with `cutExcerpt` and `markHtml`, so it follows the slider. Use the prototype's `SAMPLE_FULL` sentence without brackets, focus "upper hand", headword "get the upper hand", grammar "phr.", translation "взять верх, одолеть", and book "Moby-Dick, Herman Melville".
  - `CardsPage(onBack, onPreview: () -> Unit)`:
    - "Longest excerpt: N words", a slider 6..15 with steps of 1, and the help line;
    - the example front, which updates while dragging and saves `maxExcerptWords` when the drag ends;
    - Deck and Book tag fields with the prototype's help lines. A blank field saves its default, and the field shows the default again after the write;
    - when `deckOverride` is set, the line "Test deck is on: cards go to %1$s. Turn it off in Developer tools.";
    - the "Card design" row, which opens `PreviewKey(null)`.
  - The front is drawn as the card page's front face draws it (`ui/card/CardFaces.kt`).

- [ ] **Step 1: Implement.** Copy: the `cd*` strings from the prototype. "Longest excerpt" uses a plural.
- [ ] **Step 2: DEVIATIONS.** Add an entry for decision 8: the prototype's 30-word slider and its front switch are dropped.
- [ ] **Step 3:** `make debug`, then `make check`.

Commit: `feat: card settings page with sample card preview`

---

### Task 6: Settings tab, theme and developer tools

**Files:**
- Create: `ui/settings/SettingsScreen.kt`, `ui/settings/SettingsViewModel.kt`
- Modify:
  - `ui/Nav.kt` (add `data object DeveloperKey`);
  - `ui/AppShell.kt`: the Settings tab shows `SettingsScreen`. `DeveloperKey` shows `DebugScreen` as a page with a top bar and back arrow, and its ViewModel becomes entry-scoped. The activity-level `DebugViewModel` goes;
  - `ui/debug/DebugScreen.kt`: wrap it in a page Scaffold with the title "Developer tools";
  - `App.kt`: apply the stored theme with `AppCompatDelegate.setDefaultNightMode` from `runBlocking { container.settings.current() }`;
  - `ui/common/AnkiPermission.kt`: decision 11, using `ankiAsked`;
  - `ui/inbox/InboxScreen.kt` and `ui/inbox/InboxRows.kt`: the result card's button opens Services; the parameter is renamed `onOpenServices`;
  - `values/strings.xml`.
- Close tickets mv-8wl5 and mv-6uj1 when done.

**Interfaces:**
- Produces: `SettingsScreen(onOpen: (NavKey) -> Unit, contentPadding)`, laid out like the prototype's `renderSettings`, with a large title.
  - Translation group:
    - "Translation service": the active service's name, opening `ServicesKey`;
    - "Translate into": the language name, opening `LanguageKey(TARGET)`.
  - Look group:
    - "Theme": a segmented button with System, Light and Dark. Selecting writes `theme` and calls `setDefaultNightMode` (`MODE_NIGHT_FOLLOW_SYSTEM`, `MODE_NIGHT_NO` or `MODE_NIGHT_YES`);
    - "App language": the current choice, opening `LanguageKey(APP)`.
  - Moon+ Reader group:
    - "Backups folder" and "Books folder". Each shows the folder's name, or "Not chosen", or "Can't read this folder. Choose it again." when the persisted permission is gone. Tapping opens the system folder picker, takes the persistable permission, and writes the setting;
    - the "Review before sending" switch (`reviewFirst`).
  - Cards group:
    - "Cards", opening `CardsSettingsKey`;
    - "AnkiDroid", with its state. Ready reads "Connected"; no permission reads "Allow access" and asks when tapped; not installed reads "Not installed" and opens the Play Store when tapped (`market://details?id=com.ichi2.anki`, falling back to `https://play.google.com/store/apps/details?id=com.ichi2.anki`). The state is refreshed on resume.
  - The last row, "Developer tools", opens `DeveloperKey`.
  - Whether a folder is readable comes from `contentResolver.persistedUriPermissions`.

- [ ] **Step 1: Implement.** Copy: `set` and `x.st*` strings from the prototype.
- [ ] **Step 2:** `make debug`, then `make check`.
- [ ] **Step 3: DECISIONS.** Edit these lines:
  - "Settings shows the debug screen until milestone 5";
  - "`DebugViewModel` is activity-scoped";
  - "The theme follows the system's light or dark mode until Settings adds the choice";
  - "Asking for AnkiDroid's permission opens the app's system settings when…";
  - "An import stopped by the translation service shows its reason… The Translation service page comes in milestone 5."

  Add lines for decisions 1, 7 and 13.
- [ ] **Step 4:** Create the ticket "Remove the Developer tools page after the phone checks".

Commit: `feat: settings tab with theme, folders and AnkiDroid; debug screen moves to developer tools`

---

### Task 7: First-run setup

**Files:**
- Create: `ui/setup/SetupFlow.kt`, `ui/setup/SetupViewModel.kt`
- Modify: `ui/Nav.kt` (`data object SetupKey`), `ui/AppShell.kt`, `AppContainer.kt` or `App.kt` (the start snapshot read in Task 6 is kept, so the first key is known without a flash), `values/strings.xml`

**Interfaces:**
- Consumes:
  - `ServiceForm` (Task 4), restricted to the key field and Test connection;
  - `rememberAnkiPermissionRequest` (Task 6);
  - `BackupFinder.newest`, `SafBackupFolder.list` and `defaultStyles`.
- Produces:
  - The back stack starts with `SetupKey` when `setupDone` is false, else with `InboxKey`.
  - Finishing writes `setupDone = true` and replaces the whole back stack with `[InboxKey]`. "Import now" also asks the Inbox to open its sheet; add a one-shot `openSheet` request to `InboxViewModel`, which `InboxScreen` consumes.
  - Steps follow decision 9 and the prototype's `PAGES.setup`:
    - three progress dots for steps 2–4;
    - Back and Continue at the bottom.
  - The step index is saveable state.
  - The folders step shows the found count and the newest date, formatted with `DateUtils.getRelativeTimeSpanString` or the locale's medium date.
  - The Ready step counts `marks.count { it.combo in defaultStyles(...) && it.key !in knownKeys }`, with a progress indicator while it reads.
- A share that arrives before setup is done is dropped (Task 8 reads `setupDone`).

- [ ] **Step 1: Implement.**
  - Copy: the `su*` strings from the prototype.
  - Replace "Found 3 backups, newest today at 08:14" with a plural, "Found %1$d backup(s), newest %2$s". "No backups in this folder yet" is shown when there are none, and Continue still works.
  - Add "Get AnkiDroid" and "Use another service".
- [ ] **Step 2:** `make debug`, then `make check`.
- [ ] **Step 3: DECISIONS.** Add lines for decision 9.

Commit: `feat: first-run setup`

---

### Task 8: Share target

**Files:**
- Modify:
  - `AndroidManifest.xml`: `launchMode="singleTask"` on `MainActivity`, and an intent filter with `ACTION_SEND`, category `DEFAULT` and `mimeType="*/*"`;
  - `MainActivity.kt`: read `EXTRA_STREAM` with `IntentCompat.getParcelableExtra` in `onCreate` (only when `savedInstanceState == null`) and in `onNewIntent`, and pass it to a `MutableStateFlow<Uri?>` that `AppShell` collects;
  - `ui/AppShell.kt`: when a shared Uri arrives and setup is done, select the Inbox tab, open the sheet and call `sheetVm.change(uri)`, then clear the Uri. The sheet ViewModel must be the one `InboxScreen` uses, so hoist `ImportSheetViewModel` to `AppShell` and pass it in;
  - `ui/inbox/InboxScreen.kt`.

- [ ] **Step 1: Implement.** Nothing else reads the shared Uri: `BackupFinder.open` copies it into the cache at once.
- [ ] **Step 2:** `make debug`, then `make check`.
- [ ] **Step 3: DECISIONS.** Add lines for decision 10. Update §11's open question in the spec only after Task 11 confirms the MIME type a file manager sends.

Commit: `feat: open a shared Moon+ backup in the import sheet`

---

### Task 9: Predictive back and page motion

**Files:**
- Modify: `AndroidManifest.xml` (`android:enableOnBackInvokedCallback="true"` on `<application>`), `ui/AppShell.kt`

**Interfaces:**
- `NavDisplay` gets default `transitionSpec`, `popTransitionSpec` and `predictivePopTransitionSpec`:
  - push: the new page slides in from the end edge, full width, while the page below slides a third of the way out toward the start edge;
  - pop: the reverse;
  - predictive pop: the same as pop, driven by the gesture.
  - Use `MaterialTheme.motionScheme.defaultSpatialSpec()`.
  - Tab entries keep their cross-fade metadata.
- Pages keep their own background, so nothing shows through during the slide.

- [ ] **Step 1: Implement.**
- [ ] **Step 2:** `make debug`, then `make check`.
- [ ] **Step 3: DECISIONS.** Replace "Tab switches cross-fade" with the full motion rule, and add the back order: sheet, then selection, then page.

Commit: `feat: page slide transitions with predictive back`

---

### Task 10: Russian

**Files:**
- Create: `values-ru/strings.xml`
- Modify: `res/xml/locales_config.xml` (add `<locale android:name="ru" />`)

- [ ] **Step 1: Translate every string and plural in `values/strings.xml`, including the developer tools.**
  - Use the prototype's `T.ru` copy wherever the English matches the prototype's `T.en`.
  - Plurals give `one`, `few`, `many` and `other`.
  - Keep the format arguments and their positions.
  - Quote and escape as in the English file: apostrophes are `\'`, and edge spaces are quoted.
  - Tone: plain, sentence case, "вы" lowercase.
  - "Inbox" is «Входящие», "Library" is «Библиотека», "Discard" is «Убрать». "Send to Anki" stays «Отправить в Anki».
- [ ] **Step 2:** `make check`. Lint's `MissingTranslation` and `ExtraTranslation` must be clean.
- [ ] **Step 3: DECISIONS.** Add a line for the fixed Russian terms.

Commit: `feat: Russian translation`

---

### Task 11: Emulator checks and the phone checklist

Run by the controller on the emulator (`medium_phone`, API 36), not on the phone. Install AnkiDroid's latest stable APK from its GitHub releases, push the user's backup `2026-10-11.mrpro` to `Download/`, and install `make debug`'s APK.

- [ ] First run: setup shows; the folders step finds the backup; AnkiDroid access works; the key step's Test connection reports a rejected key for a dummy key; Ready counts the new marks; Later lands on the Inbox.
- [ ] Settings: theme switches and survives a restart; app language switches to Russian and back; Translate into search ("нем", "de"); services add, duplicate and delete (the last can't be deleted); a template URL fills; Cards slider moves the example; Card design opens the sample; Developer tools opens.
- [ ] Share: share the `.mrpro` from the Files app; the import sheet opens with that file. Share a PNG; the sheet says the backup is incomplete. Record the MIME type the Files app sent (`adb logcat`) and resolve §11's open question in the spec.
- [ ] Predictive back: the back gesture previews the page below on the card page, a settings page and the picker; back closes the sheet first and then leaves selection mode.
- [ ] Russian: walk every tab and page in Russian and look for clipped or untranslated text.
- [ ] A real import, if the user provides a key: run one book in Review first mode and send two cards to AnkiDroid on the emulator.
- Fix each defect found in its own commit, with `make check` green.
- [ ] Write `docs/phone-checks.md`, the one checklist for the user's phone across milestones 2–5. It replaces the scattered Task 10 lists in the M2, M3 and M4 plans, which it links. Steps the emulator already covered appear once, as quick confirmations.
- [ ] `make release` and report the APK path.

Commit: `docs: phone checklist for milestones 2 to 5`

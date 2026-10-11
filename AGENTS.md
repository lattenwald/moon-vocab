# AGENTS.md

Guidance for AI coding agents in this repo. `CLAUDE.md` is a symlink to this file.

Android app, Kotlin + Compose, package `org.lattenwald.moonvocab`. While reading in Moon+ Reader Pro, the user marks words they had to look up with an orange wavy underline; this app turns those marks into AnkiDroid flashcards that train recall of a word's meaning, with a translation and the sentence the word came from. After a reading session the user imports the newest Moon+ backup, glances over (or skips) a short list of new cards, and sends them to AnkiDroid; editing a card later updates the note without losing its review history.

Design source of truth: `docs/superpowers/specs/2026-10-11-moon-vocab-design.md`. Live departures from the spec or a plan go in `docs/DEVIATIONS.md`. Per-milestone plans live in `docs/superpowers/plans/`.

## Non-negotiables

- **No test theater.** A test exists only to catch a defect in this app's own logic. Never test Room, Compose, OkHttp, AnkiDroid, Kotlin, or any library behaving as documented. If a test's value is doubtful, don't write it; if you can't decide, ask instead of writing it. False confidence is worse than no test.
- **Commits.** During development, stage and commit your changes yourself, one logical unit per commit; while implementing a plan, one commit per task. Never push unasked. Always a one-line subject and no attribution.
- **Work is tracked in tickets.** `tk` in `.tickets/` (`tk help`). Anything deferred, discovered, or out of scope becomes a ticket, not a TODO comment or a note in chat.
- **Ask before touching the phone.** Never install, launch, or run anything on a device or emulator, and never run `adb`, without the user's go-ahead in this session.
- **Scope is the deliverable.** Build what was asked, make routine judgment calls yourself, and voice disagreement in a sentence rather than acting on it.
- **`docs/DECISIONS.md` lists current decisions only.** One line per decision, no reasons, nothing superseded: when a decision changes, edit or delete its line. Add a line whenever you make or are given a decision that later work must follow.
- **`docs/DEVIATIONS.md` holds deviations only.** An entry exists when a plan or the spec said "do A" and the implementing agent did B. Format: dated, newest on top, `Plan said:` / `Actual:` / `Reason:`. Delete an entry when it stops being true. Progress notes, changelogs, and "done" lists never go there.

## Conventions

- Latest stable dependency versions. Latest unstable only when no stable exists; if neither fits, ask. `androidx.compose.material3:material3` is pinned outside the Compose BOM to the 1.5.0 line for the Expressive APIs.
- No DI framework. One `AppContainer`, built in `App.onCreate()`, wires everything by hand.
- Every user-facing string in `values/strings.xml` and in every `values-<tag>` folder listed in `res/xml/locales_config.xml`. Counts go through `plurals`.
- Room: schema JSON exported to `app/schemas` and committed; explicit `Migration` for every bump; never destructive fallback.
- Built with the Makefile, not Android Studio. `make help` lists targets; `make check` runs unit tests and lint; `make release` builds the minified release APK, signed with the debug keystore.
- Edit files with the built-in file-editing tools, never shell redirection or scripts.

## Gotchas

- **DataStore** must come from the `Context.preferencesDataStore(name = "settings")` delegate, not `PreferenceDataStoreFactory.create(…)`. The factory builds a fresh store per call, which then throws `There are multiple DataStores active for the same file`.
- **`MainActivity` is an `AppCompatActivity`** and the theme parent must be `Theme.AppCompat.DayNight.NoActionBar`, for `AppCompatDelegate.setApplicationLocales`. A non-AppCompat theme causes a silent process-attach timeout.
- **IO dispatchers are passed in** through constructors (from `AppContainer`), never `Dispatchers.IO` inline; a hardcoded dispatcher escapes the test scheduler and leaks coroutines across tests.
- **Notification channels are created in `App.onCreate()`**, not lazily; some OEMs drop notifications posted to a channel created in the same call.
- **Wrap Gradle and `adb` commands in `timeout --signal=KILL <secs>`** (540 s for Gradle runs, 120 s for `adb`) and set the Bash tool timeout slightly higher. Backgrounded Gradle: `--console=plain`, no `tail`.
- **Robolectric on the JDK 25 toolchain needs `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED`** as a test jvmArg (already in `app/build.gradle.kts` `testOptions`). Without it every Robolectric test fails with "Failed to interact with raw FileDescriptor internals".
- **A string resource whose value starts or ends with a space must be double-quoted** (`<string name="unit_suffix">" ml"</string>`); AAPT trims unquoted edge whitespace silently and no lint check catches it. Prefer positional format strings (`%1$s and %2$s`) over space-carrying values.

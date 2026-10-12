# Moon+ Vocab

An Android app that turns the words you mark in [Moon+ Reader Pro](https://play.google.com/store/apps/details?id=com.flyersoft.moonreaderp) into [AnkiDroid](https://github.com/ankidroid/Anki-Android) flashcards. Underline a word you had to look up, import the newest Moon+ backup, and each mark becomes a card with the word, a translation, its meaning and the sentence it came from in the book. Editing a card later updates its note in AnkiDroid without losing its review history.

Translations come from an OpenAI-compatible service with your own API key (OpenRouter by default). The key stays on the phone. The app is in English and Russian.

## Install

Download the APK from [Releases](https://github.com/lattenwald/moon-vocab/releases) and open it on the phone. It needs Android 12 or newer, Moon+ Reader Pro backups (`.mrpro`), AnkiDroid, and your books folder for finding each mark's sentence.

## Build

Needs the Android SDK with `ANDROID_HOME` set. `make help` lists the targets; `make check` runs the tests and lint, `make release` builds the APK.

## Project

- [The design spec](docs/superpowers/specs/2026-10-11-moon-vocab-design.md) is the design authority; [docs/DECISIONS.md](docs/DECISIONS.md) lists current decisions.
- [AGENTS.md](AGENTS.md) holds the working conventions.
- Work is tracked as tickets in `.tickets/` with the `tk` CLI.

## Licence

MIT, see [LICENSE](LICENSE). Reused code stays under its original licence and copyright: the parts that reproduce Moon+ Reader's book handling belong to Moon+ Reader's author, and bundled fonts and libraries keep their own licences.

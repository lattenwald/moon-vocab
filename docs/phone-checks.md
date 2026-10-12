# Phone checks, milestones 1–5

- Status: ready for the user
- Created: 2026-10-12
- Replaces: the device steps in [M1 Task 14](superpowers/plans/2026-10-11-m1-pipeline.md), [M2 Task 10](superpowers/plans/2026-10-11-m2-merge-and-other-sources.md), [M3 Task 10](superpowers/plans/2026-10-12-m3-inbox.md) and [M4 Task 10](superpowers/plans/2026-10-12-m4-cards-and-library.md)

One session on your phone, in this order. Each part says what to do and what you should see. Write down anything that looks or feels wrong, with the part number, and send me the list. I fix each item in its own commit and send a new APK.

The emulator already covered first-run setup, the Settings pages, the language picker, the services pages, the Cards page, the theme, Russian, the share target and predictive back. Those appear here only as quick confirmations.

## 0. Install

1. Install the new release APK over the current build. Don't uninstall first, because that would lose your cards.
2. Open the app. You should land on the Inbox, not on first-run setup, because your install already has a backup folder.

## 1. Settings (quick)

1. Settings tab. You should see Translation, Look, Moon+ Reader, Cards, and Developer tools last.
2. Translation service → OpenRouter. Your key is still there, behind the eye icon. Tap Test connection. You should see "Connected. “Schnalle” came back as “…” in N s."
3. Theme → Light, then Dark, then System. Each switch applies at once and stays on Settings.
4. App language → Русский. The whole app is in Russian. Walk every tab and page and note any clipped or English text. Switch back if you like.
5. Translate into stays Russian. Its search finds German from "нем", "deu" or "Deutsch".

## 2. One book end to end (from milestone 1)

Everything here is in Settings → Developer tools. Keep "Send to test deck" on, so cards go to `Moon+::Test`. Stay on the Developer tools page while one of its buttons is working: leaving the page forgets the found backup and the result line, though the work itself still finishes.

1. Tap Find newest backup. It names your newest complete backup.
2. Pick *Diaspora* in the book list. Turn "Send to Anki after translating" off, then tap Import.
3. Both cards appear in the Inbox with a sentence and a translation.
4. Developer tools → Send all pending. In AnkiDroid, open `Moon+::Test`. Look at both cards in light and in night mode: the word, the grammar, the excerpt with an orange wavy underline, and on the back the translation, meaning, sentence and book.
5. In AnkiDroid, change one card's Translation. Back in the app, open that card from Library → Diaspora. It shows AnkiDroid's text.
6. In AnkiDroid, delete the other note. Open its card in the app. It says the note was deleted in AnkiDroid and offers Send as new card.
7. Repeat steps 2–4 with *The Great Book of Amber* (FB2) and *Neuromancer* (MOBI). Neuromancer's cards show the "example written by the model" label.

## 3. Inbox (from milestone 3)

For a full Inbox, import 3–4 of your biggest books with Send off: Developer tools, pick each book, Import. Include Fire & Blood, and note how long it takes. You need these pending cards for part 6 anyway.

1. Cards are grouped by book, newest first, with the wavy underline under the focus. Check light and dark.
2. Long-press a card and drag down across a book header. The range follows your finger, headers are never selected, and dragging near the bottom edge scrolls the list.
3. Swipe a card right: it's sent. Undo brings it back and removes the note from AnkiDroid. Swipe one left, then Undo.
4. Select several cards and tap Send. The snackbar counts them. A failed card in the selection stays and is counted.
5. Translate again on a failed card, if you have one. Progress shows, and the card comes back translated.
6. Tap Import (the FAB). The sheet shows the backup, the mark styles with counts, and Review first. Close it without importing.

## 4. Card page and Library (from milestone 4)

1. Tap an Inbox card. Both faces show with the wave. Edit the translation, tap Done, go back and reopen: the change stayed.
2. Send from the card page, then Undo. The note leaves AnkiDroid and the card is pending again.
3. Edit a sent card's translation and save. In AnkiDroid the note shows the new text and keeps its review history. Then edit the same field in AnkiDroid, and save a different edit in the app without reopening the page. The app reports a conflict and shows AnkiDroid's text.
4. Delete from AnkiDroid on a sent card's page and confirm, then Restore. The card is back in the Inbox.
5. Translate again on a sent card. New values fill the fields, and Save writes them to AnkiDroid.
6. Library → a book → the Discarded section → a card. Back returns one step at a time. Back on Library or Settings returns to the Inbox. The Inbox keeps its scroll position across tab switches.
7. Open a card page, swipe the app away from Recents, and come back. The app returns to the same page.
8. Preview a card in light and dark mode, and use Show answer.

## 5. Milestone 5 on the phone (quick)

1. From your file manager, share a `.mrpro` to Moon+ Vocab. The app opens on the Inbox with the import sheet showing that file. Close it without importing.
2. Share any non-backup file, such as a photo. The sheet says the backup is incomplete.
3. Back gesture: on a card page, a settings page and the language picker, swipe slowly from the edge. The page below shows through and follows your finger. Release and it goes back; swipe back the other way and it stays.
4. Back order on the Inbox: with the import sheet open, Back closes the sheet. In selection mode, Back clears the selection.
5. Settings → Cards. Moving the slider changes the example front. Card design opens a sample card.

## 6. Tuning export (from milestone 2)

1. With the books from part 3 imported, go to Developer tools → Export cards. Save the JSON to a folder that syncs to the laptop.
2. Send me the file path. I'll check same-word merges, lemma variants, "Already in Anki" labels, card languages and decks, and repeated words that picked the wrong sentence. Then I'll suggest fixes. We'll repeat for up to 3 rounds, until you're happy.

## 7. The real import (last, after tuning)

Do this only when you're happy with part 6.

1. Developer tools → Delete every note this build created. This empties `Moon+::Test`.
2. Developer tools → Forget unsent cards. The card list at the bottom of Developer tools is now empty. If it isn't, export the cards and send them to me before going on.
3. Turn "Send to test deck" off.
4. Inbox → Import. Select the wavy marks only, and choose Review first or Send to Anki as you like.
5. Check:
   - the result card adds up: new + already a card + skipped + failed = your wavy marks;
   - the decks are `Moon+::<Language>`, with no `Other` unless that's expected;
   - in AnkiDroid, searching `note:"Moon+ Vocab"` gives the same count as the cards sent;
   - cards labelled "Already in Anki" stayed in the Inbox.
6. The empty `Moon+::Test` deck is yours to delete in AnkiDroid.

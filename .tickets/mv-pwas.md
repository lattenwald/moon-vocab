---
id: mv-pwas
status: open
deps: []
links: []
created: 2026-10-11T18:34:16Z
type: bug
priority: 2
assignee: Alexander Q
---
# AnkiCheck refreshes forever when a plain field has formatting in Anki

Refresh stores ankiToPlain(field); the next check compares escapeHtml(plain) with Anki's HTML, so any user formatting (e.g. <b>) triggers a Refresh and a card write on every check, inflating the changed count. Compare mod first, or compare normalized plain text (AnkiCheck.kt, Reconcile.kt).


## Notes

**2026-10-11T19:37:28Z**

Also triggered by the Book field: notes sent before the book's author was stored have Book='Title', AnkiCheck now builds 'Title, Author' -> Refresh every check. Same fix (compare mod first).

**2026-10-11T19:37:28Z**

AnkiCheck.reconcileSent runs outside CardSender's mutex: a concurrent deleteAllCreated can be overwritten by a stale Missing action (debug screen only).

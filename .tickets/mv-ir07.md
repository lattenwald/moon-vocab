---
id: mv-ir07
status: closed
deps: []
links: []
created: 2026-10-12T08:26:11Z
type: bug
priority: 3
assignee: Alexander Q
tags: [anki, card-page]
---
# Save after a Room failure that followed a successful AnkiDroid update shows a confusing Conflict

domain/cardpage/CardEditor.kt:118-121: when anki.update succeeds but db.cards().update(saved) throws, save returns Failed. The card keeps the old baseline, so a retry reads the note that now holds the new text and reports a Conflict on the user's own edit. Nothing is lost, but the message is wrong.


## Notes

**2026-10-13T14:48:45Z**

Fixed: planSave doesn't count a changed field the note already holds as a conflict, and doesn't write it again, so the retry saves.

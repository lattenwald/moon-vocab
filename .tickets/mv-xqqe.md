---
id: mv-xqqe
status: closed
deps: []
links: []
created: 2026-10-12T08:26:11Z
type: task
priority: 3
assignee: Alexander Q
tags: [anki]
---
# deleteFromAnki can't tell 'not a sent card' from 'AnkiDroid refused'

app/src/main/java/org/lattenwald/moonvocab/domain/import/CardSender.kt:127 returns DeleteResult(false, null, null) for a card that isn't SENT, the same result as AnkiDroid refusing the delete (:143), so the card page shows card_snack_delete_refused for both. Also the checkNotNull(card.ankiNoteId) at CardSender.kt:129 is redundant after the status/note filter above it.


## Notes

**2026-10-13T14:18:45Z**

Fixed in 905961e: DeleteResult is sealed (Deleted, NotSent, Refused, Unavailable, Failed); NotSent reloads the card page without a message.

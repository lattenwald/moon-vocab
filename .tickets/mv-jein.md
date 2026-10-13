---
id: mv-jein
status: closed
deps: []
links: []
created: 2026-10-12T08:26:11Z
type: task
priority: 3
assignee: Alexander Q
tags: [test, anki]
---
# Test gaps in the AnkiDroid domain code (deleteFromAnki, planSave, CardEditor)

- CardSender.deleteFromAnki (app/src/main/java/org/lattenwald/moonvocab/domain/import/CardSender.kt:122-143): the gateway-exception path (:133) is untested.
- planSave (domain/card/CardEdit.kt:113): an HTML field written unescaped, a field missing from the note, and a conflict on an HTML field are untested.
- CardEditor (domain/cardpage/CardEditor.kt): open when the gateway read throws (:69-75); save on DELETED_IN_ANKI (takes priority, :94), on a non-SENT card (:100) and when AnkiDroid is not READY (:102).


## Notes

**2026-10-13T09:22:51Z**

Dropped: test-only gap, no behaviour change.

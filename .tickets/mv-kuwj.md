---
id: mv-kuwj
status: closed
deps: []
links: []
created: 2026-10-12T08:26:11Z
type: chore
priority: 4
assignee: Alexander Q
tags: [refactor]
---
# Share the plain-vs-raw AnkiDroid field rule between CardEdit and Reconcile

app/src/main/java/org/lattenwald/moonvocab/domain/card/CardEdit.kt:116 (planSave: if (f.isHtml) value else ankiToPlain(value)) repeats the rule in domain/card/Reconcile.kt:18 and :23-29. Extract one helper both use.


## Notes

**2026-10-13T14:48:45Z**

Fixed: NoteField in data/anki/NoteFields.kt owns the field list and the HTML-vs-plain rule; CardEdit, the sync, sending and the sample card use it.

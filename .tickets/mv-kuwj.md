---
id: mv-kuwj
status: open
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


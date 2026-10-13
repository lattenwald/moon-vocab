---
id: mv-0tuz
status: closed
deps: []
links: []
created: 2026-10-12T08:26:11Z
type: task
priority: 4
assignee: Alexander Q
tags: [test, preview]
---
# CardPreview: test literal {{Word}} inside a field value and unknown field names

domain/card/CardPreview.kt has no test that a field value containing '{{Word}}' stays literal (no second substitution), nor that an unknown field name renders empty. Add the cases to app/src/test/java/org/lattenwald/moonvocab/domain/card/CardPreviewTest.kt.


## Notes

**2026-10-13T09:22:51Z**

Dropped: test-only gap, no behaviour change.

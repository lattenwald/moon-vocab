---
id: mv-d65m
status: closed
deps: []
links: []
created: 2026-10-12T08:26:11Z
type: task
priority: 4
assignee: Alexander Q
tags: [test]
---
# LibraryTest doesn't pin the Other-last ruling or the language-code tie-break

domain/library/Library.kt:27 orders groups by language == null (Other last), then book count descending, then language code. app/src/test/java/org/lattenwald/moonvocab/domain/library/LibraryTest.kt has no case for Other last or for the language-code tie-break.


## Notes

**2026-10-13T09:22:51Z**

Dropped: test-only gap, no behaviour change.

---
id: mv-6z5t
status: open
deps: []
links: []
created: 2026-10-12T08:26:11Z
type: bug
priority: 3
assignee: Alexander Q
tags: [ui, library]
---
# Book page: a failed card shows 'No translation yet' instead of inbox_label_failed

ui/library/BookPage.kt:166 shows card_no_translation for a card without a translation, including FAILED cards. The Inbox shows inbox_label_failed for those; match it.


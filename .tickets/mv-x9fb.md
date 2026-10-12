---
id: mv-x9fb
status: open
deps: []
links: []
created: 2026-10-12T08:26:11Z
type: bug
priority: 3
assignee: Alexander Q
tags: [ui, library, a11y]
---
# Book page: Discarded header lacks Role.Button and expanded state for TalkBack

ui/library/BookPage.kt:174 DiscardedHeader is clickable but has no Role.Button and doesn't announce expanded/collapsed state (stateDescription or expand/collapse semantics).


---
id: mv-muyc
status: closed
deps: []
links: []
created: 2026-10-12T08:26:11Z
type: chore
priority: 4
assignee: Alexander Q
tags: [ui, refactor]
---
# Move EmptyState and segmentedRowShapes out of ui/inbox; annotate EmptyState's resource parameters

ui/inbox/InboxRows.kt:112 segmentedRowShapes and :512 EmptyState are used by the Library too; move them to a shared ui package. EmptyState(icon: Int, title: Int, text: Int) lacks @DrawableRes / @StringRes.


---
id: mv-ty9u
status: closed
deps: []
links: []
created: 2026-10-11T23:05:51Z
type: bug
priority: 2
assignee: Alexander Q
---
# Long-press on a non-card Inbox item blocks scrolling until lift

detectDragGesturesAfterLongPress consumes moves after a long-press on a header, chips or blank space; onDragStart returns early, so the list can't scroll until the finger lifts.


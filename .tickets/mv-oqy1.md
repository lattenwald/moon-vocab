---
id: mv-oqy1
status: open
deps: []
links: []
created: 2026-10-11T23:05:51Z
type: bug
priority: 2
assignee: Alexander Q
---
# Inbox drag selects rows hidden under the top bar

InboxGestures doesn't clamp the finger's y to the visible viewport (beforeContentPadding .. viewport - afterContentPadding), so a drag over the top bar resolves rows drawn under it.


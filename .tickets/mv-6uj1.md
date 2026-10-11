---
id: mv-6uj1
status: open
deps: []
links: []
created: 2026-10-11T23:05:51Z
type: bug
priority: 2
assignee: Alexander Q
---
# AnkiPermission sends users to app settings on a first-dialog dismiss

ui/common/AnkiPermission.kt opens app settings whenever the result is denied with no rationale, which also happens when the user just dismisses the first dialog or has just denied twice. Compare rationale before and after launching; open settings only when the system answered without a dialog.


---
id: mv-6dxb
status: closed
deps: []
links: []
created: 2026-10-13T18:29:52Z
type: bug
priority: 3
assignee: Alexander Q
---
# Debug delete-all reports 0 deleted while AnkiDroid is unavailable

CardSender.deleteAllCreated returns 0 when AnkiDroid is unavailable and DebugViewModel shows Outcome.Deleted(0) (DebugViewModel.kt:92); it should say AnkiDroid is unavailable.


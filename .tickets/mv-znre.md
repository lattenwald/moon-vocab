---
id: mv-znre
status: closed
deps: []
links: []
created: 2026-10-11T20:41:28Z
type: bug
priority: 2
assignee: Alexander Q
---
# AnkiCheck runs outside CardSender's mutex; a concurrent deleteAllCreated can be overwritten by a stale Missing action (debug screen only)


## Notes

**2026-10-11T21:02:17Z**

AnkiCheck.reconcileSent runs outside CardSender's mutex; a concurrent deleteAllCreated can be overwritten by a stale Missing action. Debug screen only today (Check and Delete are gated by idle); matters once the Inbox (M3) can trigger both.

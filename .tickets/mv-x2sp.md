---
id: mv-x2sp
status: closed
deps: []
links: []
created: 2026-10-11T18:34:16Z
type: task
priority: 2
assignee: Alexander Q
---
# Idempotent send: process death between anki.add and card save sends the note twice

CardSender saves SENT after AddContentApi.addNote; NonCancellable covers coroutine cancel but not process death. Before add, look up an existing note by AppId (card uuid) and adopt it.


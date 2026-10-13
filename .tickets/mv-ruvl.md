---
id: mv-ruvl
status: open
deps: []
links: []
created: 2026-10-13T12:12:57Z
type: bug
priority: 2
assignee: Alexander Q
---
# Card deletes that skip the shared AnkiDroid lock

ImportPipeline.retryFailed (domain/import/ImportPipeline.kt, deletes FAILED cards) and DebugViewModel.forgetUnsent (db.cards().deleteUnsent(), gated only on a running import) write cards outside the Mutex that CardSender, AnkiCheck, InboxActions and CardEditor share. forgetUnsent can delete a PENDING card while a send has it in flight and orphan its note. InboxActions.forgetForRetranslation already does the retry delete under the lock and in a transaction. Found in the 2026-10-13 architecture review.


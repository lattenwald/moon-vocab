---
id: mv-bokt
status: closed
deps: []
links: []
created: 2026-10-11T22:13:56Z
type: bug
priority: 2
assignee: Alexander Q
---
# Retry-failed import reports no progress

ImportPipeline.retryFailed passes an empty progress callback, so the Inbox panel stays on 'Reading the backup' for a debug-screen retry.


## Notes

**2026-10-13T09:28:45Z**

Fixed: retryFailed takes the worker's progress callback; run() reports 0 of N before the first batch.

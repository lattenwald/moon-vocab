---
id: mv-tdu0
status: closed
deps: []
links: []
created: 2026-10-11T18:34:16Z
type: task
priority: 2
assignee: Alexander Q
---
# ImportPipeline: stop sending after a send error and ensureNoteType once per run

After SendResult.error, later batches still call send (ensureNoteType + add). Spec §8 says writes stop on note-type change. Also ensureNoteType runs per batch (~33 model-list reads for 325 marks).


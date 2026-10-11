---
id: mv-cu5t
status: open
deps: []
links: []
created: 2026-10-11T19:37:28Z
type: task
priority: 2
assignee: Alexander Q
---
# Bound the per-run normalized-chapter cache

ImportPipeline Run.normalized keeps NormalizedText (~6 B/char) for every book in the run; marks interleave books (markedAt DESC) so a current-book-only cache won't work. Watch heap on a large first import; consider grouping by book or an LRU cap.


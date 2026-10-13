---
id: mv-p8ne
status: closed
deps: []
links: []
created: 2026-10-11T22:55:22Z
type: bug
priority: 2
assignee: Alexander Q
---
# BackupFinder: one lock across all callers

The import sheet serializes its own newest/open calls, but the debug screen's search can overlap and rewrite files in cacheDir/backups, including the chosen file between enqueue and the worker reading it. Move the Mutex into BackupFinder.


## Notes

**2026-10-13T09:22:51Z**

Done: BackupFinder holds the Mutex for newest() and open().

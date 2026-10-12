---
id: mv-ng7t
status: open
deps: []
links: []
created: 2026-10-12T08:56:00Z
type: bug
priority: 3
assignee: Alexander Q
---
# setupDone reads false again if backupTree is ever cleared

Settings.setupDone defaults to backupTree != null and the key is removed when it equals that default. A finished setup with a backup folder stores no key; if a later feature clears backupTree, the app sends the user back through first-run. No code clears backupTree today. Fix by storing setupDone=true explicitly when setup finishes, or never clearing backupTree.


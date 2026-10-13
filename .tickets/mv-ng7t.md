---
id: mv-ng7t
status: closed
deps: []
links: []
created: 2026-10-12T08:56:00Z
type: bug
priority: 3
assignee: Alexander Q
---
# setupDone reads false again if backupTree is ever cleared

Settings.setupDone defaults to backupTree != null and the key is removed when it equals that default. A finished setup with a backup folder stores no key; if a later feature clears backupTree, the app sends the user back through first-run. No code clears backupTree today. Fix by storing setupDone=true explicitly when setup finishes, or never clearing backupTree.


## Notes

**2026-10-13T19:40:02Z**

Not a bug: Settings.update carries the derived setupDone=true into the new snapshot, and write() stores it because the new fallback (backupTree == null) is false. A test that sets backupTree+setupDone, then clears backupTree, passes on unchanged code.

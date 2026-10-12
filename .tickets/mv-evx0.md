---
id: mv-evx0
status: closed
deps: []
links: []
created: 2026-10-12T07:35:42Z
type: task
priority: 3
assignee: Alexander Q
---
# Debug screen's ViewModel is now scoped to the Settings entry

Since the Navigation 3 shell (M4 Task 6), DebugViewModel comes from the Settings entry's ViewModelStore. Leaving Settings clears it: a running whileBusy action (send all pending, delete all created, export, forget unsent) is cancelled, and the chosen book and Send-to-AnkiDroid toggle reset. Before, it was activity-scoped and survived tab switches. Decide whether to accept this (debug screen is replaced in M5) or hoist the ViewModel to AppShell.


## Notes

**2026-10-12T07:36:58Z**

Fixed in M4 Task 6: AppShell creates DebugViewModel at activity scope, outside NavDisplay, and passes it to DebugScreen. It stays activity-scoped until M5 replaces the debug screen.

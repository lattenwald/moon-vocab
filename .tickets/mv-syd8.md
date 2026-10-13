---
id: mv-syd8
status: closed
deps: []
links: []
created: 2026-10-12T10:18:38Z
type: bug
priority: 3
assignee: Alexander Q
---
# A share during an unsaved card edit drops the edit without asking

AppShell's share path calls selectTab(InboxKey), which pops CardPage without its dirty-edit BackHandler/ConfirmLeave, so unsaved pending-card edits are lost when the user shares a .mrpro from another app mid-edit. Found in the Task 8 fix review 2026-10-12.


## Notes

**2026-10-13T12:12:57Z**

The card edit session now decides Back (leave, save then leave, or ask). The share path in AppShell can route through CardIntent.Back on the open card page before selectTab.

**2026-10-13T14:29:33Z**

Fixed: while a share waits, AppShell asks an open card page to leave (CardIntent.Back): pending edits are saved, otherwise the leave dialog asks; the share waits until the page is gone. Verified on the emulator (pending edit saved; Keep editing holds the share until Discard).

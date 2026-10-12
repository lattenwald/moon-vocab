---
id: mv-syd8
status: open
deps: []
links: []
created: 2026-10-12T10:18:38Z
type: bug
priority: 3
assignee: Alexander Q
---
# A share during an unsaved card edit drops the edit without asking

AppShell's share path calls selectTab(InboxKey), which pops CardPage without its dirty-edit BackHandler/ConfirmLeave, so unsaved pending-card edits are lost when the user shares a .mrpro from another app mid-edit. Found in the Task 8 fix review 2026-10-12.


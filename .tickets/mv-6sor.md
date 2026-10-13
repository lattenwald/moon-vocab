---
id: mv-6sor
status: closed
deps: []
links: []
created: 2026-10-12T09:48:26Z
type: chore
priority: 3
assignee: Alexander Q
tags: [ui, m5]
---
# Remove the Developer tools page after the phone checks

The debug screen (ui/debug, DeveloperKey, the last Settings row) stays only for the milestone 2 phone checks: test deck switch, cards JSON export, forget unsent. Once those checks pass, remove the page, DebugViewModel, CardsExport, the debug_* strings and the Settings row. deckOverride's Moon+::Test default and the Cards page's test-deck note go with it.


## Notes

**2026-10-13T09:22:51Z**

Superseded: the page was reworked into Advanced (6c98fd0) and kept.

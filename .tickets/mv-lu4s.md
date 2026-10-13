---
id: mv-lu4s
status: open
deps: []
links: []
created: 2026-10-13T14:16:11Z
type: bug
priority: 4
assignee: Alexander Q
---
# Import summary counts a mark whose merge target was deleted mid-import as already a card

ImportPipeline adds merged.alreadyCard / skippedDiscarded for an IntoKnown merge before CardStore.add runs. When the known card was deleted during the sense check (translate again, retry, Forget unsent), add() leaves that mark unlinked, but the summary still counts it as already a card or skipped. Race-only; the mark is picked up by the next import. Found in the 2026-10-13 review of the card store commit.


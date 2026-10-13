---
id: mv-3hov
status: closed
deps: []
links: []
created: 2026-10-12T08:30:48Z
type: task
priority: 2
assignee: Alexander Q
---
# DECISIONS line on card-page local save misstates the non-SENT case

docs/DECISIONS.md:165 says the page keeps the edits 'else with card_snack_not_pending'. Actually, when the card is no longer pending and not SENT, keepIfSent() reloads the stored card, drops the edits and shows card_snack_not_pending ('Your changes weren't saved'). Reword the line. Also note: line 124 says card-page open runs under the Mutex, but CardEditor.open locks only for SENT cards.


## Notes

**2026-10-13T12:12:57Z**

Fixed by the card edit session (domain/cardpage/CardEditSession.kt), commits d8c0ed5..80bf4b0; see DECISIONS Card page.

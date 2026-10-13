---
id: mv-kr4u
status: closed
deps: []
links: []
created: 2026-10-12T08:26:11Z
type: bug
priority: 3
assignee: Alexander Q
tags: [ui, card-page]
---
# Card page: Discard on a pending card drops unsaved edits

ui/card/CardPageViewModel.kt discard() (:153-157) discards the card and calls load(), which replaces the form; unsaved edits on a pending card are lost without a word. Save them locally first, or ask. Button: ui/card/CardPage.kt:215.


## Notes

**2026-10-13T12:12:57Z**

Fixed by the card edit session (domain/cardpage/CardEditSession.kt), commits d8c0ed5..80bf4b0; see DECISIONS Card page.

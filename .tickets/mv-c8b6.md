---
id: mv-c8b6
status: closed
deps: []
links: [mv-qur1]
created: 2026-10-12T08:26:11Z
type: bug
priority: 2
assignee: Alexander Q
tags: [ui, card-page]
---
# Card page: make the fields read-only while Done, Save or Translate again runs

ui/card/CardPageViewModel.kt edit() (:120) accepts keystrokes while busy; when Done (saveLocal -> show, :294-298), Save (show/takeFresh, :175-179) or Translate again (retranslate, :345-357) returns, it replaces the form and the keystrokes are lost. Make FrontForm/BackForm (ui/card/CardFaces.kt:94, :103) read-only while state.busy. Related: mv-qur1 (no progress shown while busy).


## Notes

**2026-10-13T12:12:57Z**

Fixed by the card edit session (domain/cardpage/CardEditSession.kt), commits d8c0ed5..80bf4b0; see DECISIONS Card page.

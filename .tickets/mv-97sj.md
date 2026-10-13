---
id: mv-97sj
status: closed
deps: []
links: []
created: 2026-10-12T07:52:25Z
type: bug
priority: 3
assignee: Alexander Q
tags: [ui, card-page]
---
# Card page drops Undo taps while another action is busy

CardPageViewModel.undo goes through act(), which ignores calls while busy. An Undo tapped on the snackbar while another action runs on the page is lost silently. Rare: actions are short and the buttons are off while busy.


## Notes

**2026-10-13T12:12:57Z**

Fixed by the card edit session (domain/cardpage/CardEditSession.kt), commits d8c0ed5..80bf4b0; see DECISIONS Card page.

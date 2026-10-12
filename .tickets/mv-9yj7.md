---
id: mv-9yj7
status: open
deps: []
links: []
created: 2026-10-12T08:26:11Z
type: bug
priority: 2
assignee: Alexander Q
tags: [ui, card-page, anki]
---
# Card page: no recovery after opening while AnkiDroid was unavailable; read error shows the wrong note

- A sent card's page opened while AnkiDroid was unavailable (CardSource.Local(unavailable), ui/card/CardPageViewModel.kt load :258-273) never checks again on resume. Save stays disabled (ui/card/CardPage.kt BottomActions, Save enabled only when source !is Local) even after the user grants the permission, and the page offers no fix action (spec §8).
- A Local open with a read error (OpenResult.Local(error != null)) shows card_sent_note ('Saving updates it there') in Notes (ui/card/CardPage.kt:264-276) while Save is disabled.


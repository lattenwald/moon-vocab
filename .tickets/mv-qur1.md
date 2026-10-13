---
id: mv-qur1
status: closed
deps: []
links: [mv-c8b6]
created: 2026-10-12T07:52:25Z
type: feature
priority: 3
assignee: Alexander Q
tags: [ui, card-page]
---
# Card page shows no progress while Translate again or Save runs

CardPageViewModel's busy flag only disables the buttons. Translate again waits on the translation service (up to 60 s plus LlmClient retries) with no visible progress. Consider a progress indicator under the top bar while busy, at least for Translate again.


## Notes

**2026-10-13T12:12:57Z**

Fixed by the card edit session (domain/cardpage/CardEditSession.kt), commits d8c0ed5..80bf4b0; see DECISIONS Card page.

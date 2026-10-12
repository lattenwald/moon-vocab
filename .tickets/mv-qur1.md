---
id: mv-qur1
status: open
deps: []
links: []
created: 2026-10-12T07:52:25Z
type: feature
priority: 3
assignee: Alexander Q
tags: [ui, card-page]
---
# Card page shows no progress while Translate again or Save runs

CardPageViewModel's busy flag only disables the buttons. Translate again waits on the translation service (up to 60 s plus LlmClient retries) with no visible progress. Consider a progress indicator under the top bar while busy, at least for Translate again.


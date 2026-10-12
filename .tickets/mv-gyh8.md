---
id: mv-gyh8
status: open
deps: []
links: []
created: 2026-10-12T08:26:11Z
type: bug
priority: 3
assignee: Alexander Q
tags: [ui, card-page]
---
# Card page: unsaved edits are lost on process death

ui/card/CardPageViewModel.kt keeps form, extras and editing in a Compose snapshot state (:104) only; nothing is in SavedStateHandle, so after process death the page reopens with the stored card and the unsaved edits are gone.


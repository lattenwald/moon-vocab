---
id: mv-4cgz
status: open
deps: []
links: []
created: 2026-10-13T14:55:11Z
type: bug
priority: 4
assignee: Alexander Q
---
# A Transcription edit on a note type without Transcription is dropped while Save reports Saved

planSave puts a changed Transcription in the write map; mergeFields leaves out an optional field the note's type lacks, so AnkiDroid never gets it while the card stores the new value and the page says Saved. Later checks keep the card's value (the note has no key), so the two stay apart silently. Pre-dates the note-sync refactor; found in its 2026-10-13 review.


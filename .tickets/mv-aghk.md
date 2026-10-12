---
id: mv-aghk
status: open
deps: []
links: []
created: 2026-10-12T08:26:11Z
type: bug
priority: 3
assignee: Alexander Q
tags: [anki, check]
---
# AnkiCheck.runIfDue skips the check while the device clock is behind lastCheckAt

app/src/main/java/org/lattenwald/moonvocab/domain/import/AnkiCheck.kt:33 runIfDue compares clock() - lastCheckAt < START_INTERVAL_MS. When the clock moved back (elapsed is negative), the check is skipped until the clock passes lastCheckAt again. Treat negative elapsed as due.


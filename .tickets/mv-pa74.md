---
id: mv-pa74
status: closed
deps: []
links: []
created: 2026-10-12T08:26:11Z
type: bug
priority: 3
assignee: Alexander Q
tags: [ui, card-page]
---
# Editing a stored excerpt or sentence with a literal bracket makes it invalid without saying why

A stored excerpt or sentence containing a literal bracket (e.g. '[sic]') shows two bracket pairs in the edit form (domain/card/CardEdit.kt toForm/bracketed). Once the user edits that field, parseForm (CardEdit.kt:72) marks it invalid because singleBracketPair (domain/card/Excerpt.kt:56) needs exactly one pair. The field is only marked red; nothing tells the user the literal bracket is the problem.


---
id: mv-g96m
status: open
deps: []
links: []
created: 2026-10-13T15:52:12Z
type: bug
priority: 2
assignee: Alexander Q
---
# Moon+ text artifacts in sentences: page labels cut them, image placeholders stay

The Moon+ text a sentence is taken from inserts page labels and U+FFFC image placeholders. (1) In the labelled EPUB text (marks made at or after 1 700 000 000 000 ms), each page break inserts 'Page N ' between line breaks (MoonText.pageNumber), and a sentence never crosses a line break, so a sentence straddling a page break is cut at the label. Fix idea: locate in the labelled part, then map the position to the plain part and take the sentence there. (2) The anchor fallback searches the plain parts, so a labelled mark's position drifts by the label lengths before it; the tolerance may absorb it or not. (3) Image placeholders U+FFFC (MoonText OBJ) reach the sentence, and normalize() doesn't drop them.


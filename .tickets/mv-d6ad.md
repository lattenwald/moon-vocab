---
id: mv-d6ad
status: closed
deps: []
links: []
created: 2026-10-12T10:08:22Z
type: bug
priority: 3
assignee: Alexander Q
---
# Import sheet: two mark combos can share one name

The user's backup has underline #FF555555 and underline #88888888; both show as 'Underline, gray' in the import sheet, so they can't be told apart. Seen on the emulator 2026-10-12. Options: add a lightness word (dark gray / light gray), or show the hex in a subtitle when two names collide.


## Notes

**2026-10-13T09:36:29Z**

Fixed: dark/light qualifier when two combos of a style share a name, hex as last resort.

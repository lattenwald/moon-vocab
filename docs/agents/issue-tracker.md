# Issue tracker: tk

Issues live as markdown files in `.tickets/` (`mv-xxxx.md`, YAML frontmatter plus a body), managed with the `tk` CLI (`tk help`). GitHub Issues are not used.

## Conventions

- Create: `tk create "<title>" -t bug|feature|task|epic|chore -p 0-4 -d "<description>" [--tags a,b] [--parent <id>]`. It prints the new ID.
- Read: `tk show <id>`. Partial IDs work (`tk show o8ln`).
- List: `tk ready` (open, deps resolved), `tk blocked`, `tk ls --status=open`, `tk closed`. Filter by tag with `-T <tag>`, or use `tk query '<jq filter>'` for JSON.
- Dependencies: `tk dep <id> <dep-id>` (id depends on dep-id). `tk link` for related tickets.
- Comments and history: `tk add-note <id> "<text>"` appends a timestamped note. Never rewrite earlier notes.
- Tags and other frontmatter fields with no tk command: edit the ticket file's frontmatter with the file editor. `tk edit` opens `$EDITOR` and does not work non-interactively.
- Status: `tk start` (in_progress), `tk close`, `tk reopen`.

## When a skill says "publish to the issue tracker"

`tk create` one ticket per issue. A spec or a multi-ticket feature gets an `epic` ticket holding the spec in its body, with each issue created under it via `--parent <epic-id>`.

## When a skill says "fetch the relevant ticket"

`tk show <id>`. The user normally passes the ID.

## Wayfinding operations

Used by `/wayfinder`.

- **Map**: an `epic` ticket whose body holds Notes / Decisions so far / Fog.
- **Child ticket**: `tk create --parent <map-id> --tags <research|prototype|grilling|task>`, with the question in the description.
- **Blocking**: `tk dep <child> <blocker>`.
- **Frontier**: `tk query 'select(.parent == "<map-id>" and .status == "open")'`, keeping those whose deps are all closed (`tk ready` shows the same set across the repo). The oldest wins.
- **Claim**: `tk start <id>` before any work.
- **Resolve**: `tk add-note <id>` with the answer, then `tk close <id>`, then add a one-line gist plus the ticket ID to the map's Decisions so far.

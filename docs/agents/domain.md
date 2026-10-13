# Domain Docs

How the engineering skills should consume this repo's domain documentation when exploring the codebase.

## Before exploring, read these

- **`CONTEXT.md`** at the repo root, if it exists.
- **`docs/DECISIONS.md`**: the current decisions, one line each. This repo uses it instead of `docs/adr/`.
- **`docs/superpowers/specs/2026-10-11-moon-vocab-design.md`**: the design source of truth.

If `CONTEXT.md` doesn't exist, **proceed silently**. Don't flag its absence; don't suggest creating it upfront. The `/domain-modeling` skill (reached via `/grill-with-docs` and `/improve-codebase-architecture`) creates it lazily when terms actually get resolved.

## File structure

Single-context repo:

```
/
├── CONTEXT.md
├── docs/
│   ├── DECISIONS.md
│   └── superpowers/specs/
└── app/
```

## Recording decisions

Record a decision by adding or editing its line in `docs/DECISIONS.md`: one line, no reasons, current decisions only. When a decision changes, edit or delete its line. Never create ADR files or a `docs/adr/` directory.

## Use the glossary's vocabulary

When your output names a domain concept (in an issue title, a refactor proposal, a hypothesis, a test name), use the term as defined in `CONTEXT.md`. Don't drift to synonyms the glossary explicitly avoids.

If the concept you need isn't in the glossary yet, that's a signal: either you're inventing language the project doesn't use (reconsider) or there's a real gap (note it for `/domain-modeling`).

## Flag DECISIONS.md conflicts

If your output contradicts a line in `docs/DECISIONS.md`, surface it explicitly rather than silently overriding:

> _Contradicts the DECISIONS.md line on shared-backup dates, but worth reopening because…_

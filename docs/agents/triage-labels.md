# Triage Labels

The skills speak in terms of five canonical triage roles. This file maps those roles to the tk tags used in this repo's local tracker, paired with the tk status each role implies.

| Label in mattpocock/skills | tk tag            | tk status                                                   | Meaning                                  |
| -------------------------- | ----------------- | ----------------------------------------------------------- | ---------------------------------------- |
| `needs-triage`             | `needs-triage`    | `open`                                                      | Maintainer needs to evaluate this issue  |
| `needs-info`               | `needs-info`      | `open`                                                      | Waiting on reporter for more information |
| `ready-for-agent`          | `ready-for-agent` | `open` (`tk ready -T ready-for-agent` lists them once deps close) | Fully specified, ready for an AFK agent  |
| `ready-for-human`          | `ready-for-human` | `open`                                                      | Requires human implementation            |
| `wontfix`                  | `wontfix`         | `closed` (after `tk add-note` with the reason)              | Will not be actioned                     |

When a skill mentions a role (e.g. "apply the AFK-ready triage label"), use the corresponding tk tag and status from this table. Work in progress is `in_progress` (`tk start`) regardless of tag.

A ticket carries exactly one state tag. To change roles, replace the old tag in the `tags:` frontmatter line, since tk has no tag command. Category roles use tk's `type` field: `bug` maps to `bug`, `enhancement` to `feature`.

Edit the tag column to match whatever vocabulary you actually use.

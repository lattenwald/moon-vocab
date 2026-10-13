---
id: mv-nwd1
status: closed
deps: []
links: []
created: 2026-10-12T11:58:04Z
type: task
priority: 3
assignee: Alexander Q
---
# A share with no file opens the app with no feedback

The */* SEND filter puts the app in text/link share sheets; a share without EXTRA_STREAM just brings the app forward. Show a short 'Only Moon+ backups can be shared here' message. From the M5 final review.


## Notes

**2026-10-13T09:36:37Z**

Fixed: share filter narrowed to application/*; a share without a file shows a toast.

---
id: mv-nnrc
status: closed
deps: []
links: []
created: 2026-10-11T19:15:30Z
type: task
priority: 2
assignee: Alexander Q
---
# Large FB2 parsed whole; OutOfMemoryError not caught

FB2 with base64 <binary> images is parsed into one jsoup DOM. Watch during the FB2 phone step; consider skipping <binary> while parsing.


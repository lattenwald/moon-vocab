---
id: mv-7i9y
status: closed
deps: []
links: []
created: 2026-10-12T11:58:04Z
type: task
priority: 3
assignee: Alexander Q
---
# Model suggestions send the stored key to every half-typed address

ServicesViewModel fetches GET {baseUrl}/models 600 ms after the address stops changing, with the stored key. After Duplicate (key kept), editing the address sends that key over TLS to each resolvable half-typed host. Fix: fetch only when the address field loses focus, or skip the key when the address differs from the one the key was entered with. From the M5 final review.


## Notes

**2026-10-13T09:53:44Z**

Fixed: suggestions load only when the Model field gains focus, with the stored address and key; no fetch while editing. Dropdown opens when the list arrives while focused. Checked on the emulator.

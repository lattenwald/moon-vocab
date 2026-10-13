---
id: mv-tpie
status: open
deps: []
links: []
created: 2026-10-12T11:42:43Z
type: bug
priority: 2
assignee: Alexander Q
---
# Fresh AnkiDroid with no collection reads as Connected

On the emulator AnkiDroid 2.25.1 was installed but never opened (no collection, onboarding asks for All files access). The setup step and Settings showed 'Connected to AnkiDroid' because the permission was granted, but every provider call fails: ensureNoteType throws 'AnkiDroid note types unavailable' (getModelList null). availability() should report this as its own state ('Open AnkiDroid once to finish its setup') and setup should not let Continue through. Found 2026-10-12 running AnkiDroidGatewayTest on the emulator.


## Notes

**2026-10-13T09:46:02Z**

Verified on the emulator 2026-10-13 (AnkiDroid 2.25.1): before first launch AddContentApi.modelList and deckList are null; after one launch with no notes they list 6 stock note types and the Default deck. A null model list is the 'not set up' signal.

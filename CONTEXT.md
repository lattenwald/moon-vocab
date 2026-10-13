# Moon+ Vocab

Turns words marked in Moon+ Reader into AnkiDroid cards that train recall of a word's meaning.

## Card page

**Card edit session**:
One open card page: the card as stored, the user's unsaved edits against it, and the one action in flight.
_Avoid_: draft, form state

**Unsaved edits**:
Changes made on a card page that are stored neither in the app nor in AnkiDroid yet.
_Avoid_: draft

## AnkiDroid

**Note**:
The AnkiDroid note a card was sent as. A sent card and its note can each change without the other.
_Avoid_: Anki card, which is AnkiDroid's own review card made from a note

**Note sync**:
Bringing a sent card and its note back in line: reading the note's changes into the card, writing the user's edits to the note, or finding the note gone.
_Avoid_: reconcile, refresh on its own

## Import

**Import run**:
One import, or one retry of failed cards, working in the background. At most one runs at a time.
_Avoid_: import work, job

**Import result**:
What the last finished import run reported, kept until the user dismisses it.
_Avoid_: summary on its own

**Sentence lookup**:
Finding the sentence of the book a mark was made in, or deciding no sentence is certainly it.
_Avoid_: sentence finding, locating, on their own

**Card draft**:
A card the import builds from one translation, before it is stored.
_Avoid_: draft on its own, which reads as unsaved edits

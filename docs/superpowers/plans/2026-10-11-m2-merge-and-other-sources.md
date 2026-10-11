# Moon+ Vocab milestone 2 (merge and other sources) implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A word marked again in the same sense joins its existing card, a word the user already learns from another deck is labelled and held back, and the user's whole real backup lands in the real decks without duplicates.

**Architecture:**
- Same-sense merge is split in two. The pure planning and resolving step lives in `domain/card/SameSense.kt`, and the one LLM request per batch lives in `data/llm/LlmSenseJudge.kt`.
- The "Already in Anki" search is one gateway call per new card, built from pure, tested query strings.
- `ImportPipeline` gains the two steps between translating and writing (spec §5.7 steps 4.2 and 4.3).
- The rest hardens what the full import touches: idempotent sends, a quiet AnkiDroid check, bounded book memory, valid language codes.
- Debug-screen tools support tuning on real data and the final real import.

**Tech Stack:** Unchanged from milestone 1: Kotlin, Compose, Room, DataStore, WorkManager, OkHttp, kotlinx.serialization, jsoup, the AnkiDroid API, JUnit 4, Robolectric and MockWebServer.

**Spec:** [`docs/superpowers/specs/2026-10-11-moon-vocab-design.md`](../specs/2026-10-11-moon-vocab-design.md). Section numbers (§n) refer to it. Current decisions are in [`docs/DECISIONS.md`](../../DECISIONS.md). Milestone 1 is commit `5bb31fb`.

## Global Constraints

Paths:
- Main code paths are relative to `app/src/main/java/org/lattenwald/moonvocab/`.
- Unit test paths are relative to `app/src/test/java/org/lattenwald/moonvocab/`.

Unchanged from milestone 1:
- Room stays at schema version 1. No task here changes an entity. A task that finds it must change one stops and asks.
- Batch size is 10, translation and sense requests use the active service, and the 60 s timeout and 2 retries come from `LlmClient`.
- `deckOverride` defaults to `Moon+::Test`. Task 9 adds the switch that clears it for the real import.
- Strings go in `values/strings.xml` only, with counts in `plurals`. Russian comes in milestone 5.

New fixed values:
- Sense candidates per question: at most 8.
- Sense reply token budget: `60 * questions.size + 100`.
- Book texts kept in memory per run: 3.
- AppId lookups: 50 uuids per query.

Repo rules (`AGENTS.md`):
- Each task ends with one commit on the `m2` branch: `git add` the files it changed, `git commit -m "<subject>"`. One-line subject, no attribution of any kind.
- No test theater.
- Deferred work becomes a `tk` ticket.
- Ask before touching the phone.
- Edit files with the file-editing tools, never shell redirection, `sed -i` or scripts.

`docs/DECISIONS.md`:
- Every task edits the lines its change makes untrue, and adds a line for each decision below that later work must follow.
- Each task's **Decisions** block lists the lines.

Commands:
- Run `timeout --signal=KILL 540 make check` for unit tests and lint, and `--tests '<pattern>'` through `./gradlew :app:testDebugUnitTest` for one class.

## Review Focus

1. The model returns the same lemma with different case, spacing, Unicode form or apostrophe in different batches. The marks must still be compared, not silently become two cards. Test in Task 1.
2. The judge answers with a ref that is not one of that question's candidates (`"k999"`, or another question's ref). The ref is ignored and the mark gets its own card. Test in Task 2.
3. A word is marked twice in one batch, and the first mark itself joins an existing discarded card. The second mark must follow it to that card and count as skipped, not join a card that was never written. Test in Task 3.
4. A search term contains Anki syntax (`_`, `*`, `:`, `"`, parentheses, a leading `-`). It must match only that literal word: never a wildcard, never a negated search. Test in Task 4, unit and instrumented.
5. AnkiDroid is missing, or the other-source search throws, during an import. The import completes, and the cards have no label. Test in Task 5.

---

### Task 1: Valid language codes and sense keys

Fixes ticket mv-mjpt (`isoLanguage` passes free text through, which would make decks like `Moon+::xx`).

**Files:**
- Modify: `data/books/BookText.kt` (`isoLanguage`)
- Create: `domain/card/SameSense.kt` (this task adds `senseKey` only; Task 3 adds the rest)
- Test: `data/books/BookTextTest.kt`, `domain/card/SameSenseTest.kt`

**Interfaces:**
- Produces: `fun senseKey(lemma: String): String`, and `isoLanguage(tag: String?): String?`, which now returns only codes in `Locale.getISOLanguages()`.

- [ ] **Step 1: Write the failing tests**

```kotlin
// BookTextTest
@Test fun languageCodesMustBeIso6391() {
    assertNull(isoLanguage("russian")); assertNull(isoLanguage("xx")); assertNull(isoLanguage("haw"))
    assertEquals("ru", isoLanguage("RU")); assertEquals("en", isoLanguage("en_US")); assertEquals("de", isoLanguage("ger"))
}
// SameSenseTest
@Test fun senseKeyIgnoresCaseSpacingUnicodeFormAndQuotes() {
    assertEquals(senseKey("schnalle"), senseKey(" Schnalle "))
    assertEquals(senseKey("get the upper hand"), senseKey("get  the\u00A0upper hand"))
    assertEquals(senseKey("na\u00EFve"), senseKey("nai\u0308ve"))
    assertEquals(senseKey("don't"), senseKey("don\u2019t"))
    assertNotEquals(senseKey("Schnalle"), senseKey("Schnallen"))
}
```

Write the `\u` escapes as `Char(0x…)` concatenations: the file tools turn `\uXXXX` into literal characters.

- [ ] **Step 2: Run them and see them fail**

Run: `timeout --signal=KILL 540 ./gradlew :app:testDebugUnitTest --tests '*BookTextTest' --tests '*SameSenseTest'`
Expected: FAIL.

- [ ] **Step 3: Implement**

`isoLanguage` keeps its mapping and returns the result only if it is in `Locale.getISOLanguages()`, otherwise null. `senseKey` is `normalizeForMatch(lemma)`, trimmed, with whitespace runs collapsed to one space, then `lowercase(Locale.ROOT)`.

- [ ] **Step 4: Run them and see them pass**, then `make check`

- [ ] **Step 5: Decisions, ticket, commit subject**

Decisions:
- Replace the line "`isoLanguage` maps ISO 639-2/B codes to two-letter codes. A code it can't resolve is skipped." with: "`isoLanguage` maps ISO 639-2/B codes to two-letter codes and returns only codes in `Locale.getISOLanguages()`; anything else is null."
- Add under Sentences, excerpts, cards: "Two cards are compared for sense when their source languages are equal (null equals null) and their `senseKey(lemma)` values are equal."

Then `tk close mv-mjpt`. Commit subject: `fix: accept only ISO 639-1 language codes; add sense keys`.

---

### Task 2: The sense judge

**Files:**
- Create: `data/llm/ReplyJson.kt`. Move `parseReply`, `parseObject`, `firstObject` and `JsonObject.text` out of `TranslationService` into it, as `internal fun replyItems(content: String): JsonArray?` and `internal fun JsonObject.text(key: String): String?`.
- Create: `data/llm/LlmSenseJudge.kt`, `data/llm/SensePrompt.kt`
- Modify: `data/llm/TranslationService.kt` (uses `ReplyJson.kt`), `domain/card/SameSense.kt`
- Test: `data/llm/LlmSenseJudgeTest.kt`. `TranslationServiceTest` must pass unchanged.

**Interfaces:**
- Produces, in `domain/card/SameSense.kt`:

```kotlin
data class SenseCandidate(val ref: String, val word: String, val translation: String, val meaning: String)
data class SenseQuestion(
    val id: String, val word: String, val focus: String, val sentence: String?,
    val translation: String, val meaning: String, val candidates: List<SenseCandidate>,
)
/** Returns question id → the ref of the candidate with the same sense. No entry means no match. */
fun interface SenseJudge { suspend fun judge(questions: List<SenseQuestion>): Map<String, String> }
```

- Produces, in `data/llm`: `class LlmSenseJudge(private val llm: LlmClient) : SenseJudge`.

Behaviour:
- Empty `questions`: no request, empty map.
- Otherwise one request. The user message is the questions as a JSON array, using the field names of `SenseQuestion` and `SenseCandidate`. `maxTokens` is `60 * questions.size + 100`.
- Parse the reply with `replyItems`, as `{"items":[{"id":…,"match":…}]}`.
- Keep a result only when:
  - its `id` is a question id (when an id repeats, the first result wins);
  - its `match` is a string equal to one of that question's own candidate refs.
- Malformed JSON gives an empty map. `LlmException` propagates.

`SensePrompt.SYSTEM`, fixed text:

```
A reader learns vocabulary from the books they read and keeps one flashcard per meaning of a word. Each question has a word the reader just marked: how it appears in the text ("focus"), the sentence ("sentence", or null), its dictionary form ("word"), translation and meaning. It also has flashcards the reader already has for the same dictionary form ("candidates"), each with a ref. Reply with JSON matching the schema: one result per question, with the same id.

For each question, set match to the ref of the candidate that teaches the meaning the word has in this sentence. Set match to null when no candidate has that meaning: a different sense, a different part of speech, or a fixed phrase whose meaning differs from the single word. If several candidates fit, pick the first of them.
```

`SensePrompt.schema` is an object `{items: array of object}`:
- each item has the required properties `id` (string) and `match` (string or null);
- every object has `additionalProperties: false`.

- [ ] **Step 1: Write the failing tests (MockWebServer behind a real `LlmClient`, as in `TranslationServiceTest`)**

```kotlin
@Test fun noQuestionsNoRequest() = runTest { assertTrue(judge().judge(emptyList()).isEmpty()); assertEquals(0, server.requestCount) }
@Test fun keepsOnlyOwnCandidateRefs() = runTest {
    reply("""{"items":[{"id":"0","match":"k7"},{"id":"1","match":"k7"},{"id":"2","match":"k999"},{"id":"zz","match":"k7"}]}""")
    // question 0 has candidate k7; question 1 has only b0; question 2 has k8
    assertEquals(mapOf("0" to "k7"), judge().judge(listOf(q("0", "k7"), q("1", "b0"), q("2", "k8"))))
}
@Test fun nullMatchAndFirstDuplicateWins() = runTest {
    reply("""{"items":[{"id":"0","match":null},{"id":"0","match":"k7"},{"id":"1","match":"b0"},{"id":"1","match":null}]}""")
    assertEquals(mapOf("1" to "b0"), judge().judge(listOf(q("0", "k7"), q("1", "b0"))))
}
@Test fun malformedReplyIsNoMatch() = runTest { reply("{\"items\":[{\"id\":\"0\""); assertTrue(judge().judge(listOf(q("0", "k7"))).isEmpty()) }
@Test fun requestCarriesCandidatesAndBudget() = runTest {
    reply("""{"items":[]}"""); judge().judge(listOf(q("0", "k7"), q("1", "b0")))
    val body = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
    assertEquals(220, body["max_tokens"]!!.jsonPrimitive.int)
    assertTrue("\"ref\":\"k7\"" in body["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonPrimitive.content)
}
```

`q(id, vararg refs)` builds a `SenseQuestion` with one `SenseCandidate` per ref.

- [ ] **Step 2: Run them and see them fail**

Run: `timeout --signal=KILL 540 ./gradlew :app:testDebugUnitTest --tests '*LlmSenseJudgeTest' --tests '*TranslationServiceTest'`
Expected: `LlmSenseJudgeTest` fails to compile.

- [ ] **Step 3: Extract `ReplyJson.kt`, then implement `LlmSenseJudge` and `SensePrompt`**

- [ ] **Step 4: Run both classes and see them pass**, then `make check`

- [ ] **Step 5: Decisions and commit subject**

Decisions (under Translation):
- "Same-sense questions for a batch go in one request to the active service, at most 8 candidates each."
- "A judge's match is kept only when it is one of that question's own candidate refs."

Commit subject: `feat: LLM judge for same-sense marks`.

---

### Task 3: Planning and resolving merges

**Files:**
- Modify: `domain/card/SameSense.kt`
- Test: `domain/card/SameSenseTest.kt`

**Interfaces:**
- Consumes: `senseKey` (Task 1), `SenseQuestion`, `SenseCandidate` (Task 2), `CardStatus`.
- Produces:

```kotlin
/** A pending card of the current batch, at [index] in the batch's card list. */
data class NewCard(
    val index: Int, val language: String?, val lemma: String, val word: String, val focus: String,
    val sentence: String?, val translation: String, val meaning: String,
)
data class KnownCard(
    val id: Long, val status: CardStatus, val language: String?, val lemma: String,
    val word: String, val translation: String, val meaning: String,
)
sealed interface Merge {
    data object New : Merge
    data class IntoKnown(val cardId: Long, val status: CardStatus) : Merge
    /** Into the batch card at [index], whose own result is New. */
    data class IntoBatch(val index: Int) : Merge
}
fun senseQuestions(batch: List<NewCard>, known: List<KnownCard>): List<SenseQuestion>
fun resolveMerges(batch: List<NewCard>, answers: Map<String, String>, known: List<KnownCard>): Map<Int, Merge>
```

Rules:
- A question's id is the new card's `index` as a string.
- A known candidate's ref is `"k<id>"`, and a batch candidate's ref is `"b<index>"`.
- Candidates for a new card are the cards with the same language and `senseKey` as it (Task 1), in this order:
  1. known cards, SENT and PENDING before DISCARDED and DELETED_IN_ANKI, then by id;
  2. batch cards with a smaller index, by index.
- Known cards with status FAILED or a blank lemma are never candidates. At most 8 candidates.
- A new card with no candidates gets no question.
- `resolveMerges` walks the batch in index order:
  - no answer: `New`;
  - `"k<id>"`: `IntoKnown(id, that card's status)`;
  - `"b<j>"`: whatever `j` resolved to, if that is `IntoKnown`; otherwise `IntoBatch(j)`.
- Every batch index appears in the result.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun noCandidatesNoQuestion() = assertTrue(senseQuestions(listOf(new(0, "Schnalle")), listOf(known(1, "Falte"))).isEmpty())
@Test fun candidatesMatchLanguageAndSenseKeyAndSkipFailed() {
    val qs = senseQuestions(
        listOf(new(0, "Schnalle")),
        listOf(known(1, "schnalle"), known(2, "Schnalle", language = "nl"), known(3, "Schnalle", status = FAILED)),
    )
    assertEquals(listOf("k1"), qs.single().candidates.map { it.ref })
}
@Test fun liveKnownCardsComeFirstThenEarlierBatchCards() {
    val qs = senseQuestions(
        listOf(new(0, "Schnalle"), new(1, "Schnalle")),
        listOf(known(5, "Schnalle", status = DISCARDED), known(9, "Schnalle", status = SENT)),
    )
    assertEquals(listOf("k9", "k5", "b0"), qs.single { it.id == "1" }.candidates.map { it.ref })
}
@Test fun atMostEightCandidates() =
    assertEquals(8, senseQuestions(listOf(new(0, "a")), (1L..12L).map { known(it, "a") }).single().candidates.size)
@Test fun secondMarkFollowsFirstIntoDiscardedCard() {
    val batch = listOf(new(0, "Schnalle"), new(1, "Schnalle"))
    val known = listOf(known(5, "Schnalle", status = DISCARDED))
    assertEquals(
        mapOf(0 to Merge.IntoKnown(5, DISCARDED), 1 to Merge.IntoKnown(5, DISCARDED)),
        resolveMerges(batch, mapOf("0" to "k5", "1" to "b0"), known),
    )
}
@Test fun sameSenseInsideBatchJoinsTheFirst() = assertEquals(
    mapOf(0 to Merge.New, 1 to Merge.IntoBatch(0), 2 to Merge.IntoBatch(0)),
    resolveMerges(listOf(new(0, "a"), new(1, "a"), new(2, "a")), mapOf("1" to "b0", "2" to "b1"), emptyList()),
)
```

`new` and `known` are test builders with language `"de"` and status `PENDING` by default.

- [ ] **Step 2: Run them and see them fail**

Run: `timeout --signal=KILL 540 ./gradlew :app:testDebugUnitTest --tests '*SameSenseTest'`

- [ ] **Step 3: Implement both functions**

- [ ] **Step 4: Run them and see them pass**

- [ ] **Step 5: Decisions and commit subject**

Decisions (under Sentences, excerpts, cards):
- "Sense candidates are known cards (SENT and PENDING first, then DISCARDED and DELETED_IN_ANKI, then by id) followed by earlier cards of the same batch. FAILED cards are never candidates."
- "A mark that matches an earlier mark of its batch goes wherever that mark went."

Commit subject: `feat: plan and resolve same-sense merges`.

---

### Task 4: AnkiDroid: other-source search and lookup by AppId

**Files:**
- Create: `data/anki/AnkiSearch.kt`
- Modify:
  - `data/anki/AnkiGateway.kt`
  - `data/anki/AnkiDroidGateway.kt`
  - unit test `domain/import/FakeAnki.kt`
  - `app/src/androidTest/java/org/lattenwald/moonvocab/data/anki/AnkiDroidGatewayTest.kt`
  - spec §5.5, the `findElsewhere` line only
- Test: `data/anki/AnkiSearchTest.kt`

**Interfaces:**
- Produces, in `AnkiSearch.kt`:

```kotlin
fun wordSearch(term: String): String              // "w:<escaped>" in double quotes
fun elsewhereQuery(terms: List<String>): String   // -note:"Moon+ Vocab" (<wordSearch> OR …)
fun appIdQuery(mid: Long, uuids: List<String>): String // mid:<mid> (AppId:<u1> OR …)
```

- Produces, on `AnkiGateway`:

```kotlin
/** Deck name of the first note outside the Moon+ Vocab type matching any term; "" if its deck can't be read; null for no hit. */
fun findElsewhere(terms: List<String>): String?
/** uuid → note id, for notes of note type [mid] whose AppId is one of [uuids]. */
fun findByAppIds(mid: Long, uuids: Collection<String>): Map<String, Long>
```

Escaping (§5.6):
- `wordSearch` puts a backslash before each `\`, `"`, `*`, `_`, `:`, `(` and `)`.
- It also puts a backslash before a leading `-`.
- It wraps `w:` and the escaped term in double quotes.
- `elsewhereQuery` drops duplicate terms, ignoring case. It uses `NoteTypeSpec.NAME`.

`AnkiDroidGateway`:
- `findElsewhere` runs the query on `Note.CONTENT_URI` with a null projection. A null cursor means no hit.
- On a hit, it takes the first row's `_id`, queries `<note uri>/cards`, and takes the row with the smallest `ord`. Its `deck_id` goes to `AddContentApi.getDeckName`, and a null name becomes `""`.
- `findByAppIds` queries in chunks of 50. It reads `flds` and `mid`, finds the `AppId` index by name with `getFieldList(mid)`, and keeps the rows whose AppId is in `uuids`.
- Both are read-only. Nothing they find is ever written.

`FakeAnki` gets:
- `val elsewhere = HashMap<String, String>()`, mapping a lowercase term to a deck;
- `val searches = ArrayList<List<String>>()`;
- `var failElsewhere = false`, which makes `findElsewhere` throw `NullPointerException`;
- `var ensureCalls = 0`, incremented in `ensureNoteType`;
- `findByAppIds`, which scans `notes` for the mid and `fields["AppId"]`.

- [ ] **Step 1: Write the failing unit tests**

```kotlin
@Test fun plainAndPhraseTerms() {
    assertEquals("\"w:Schnalle\"", wordSearch("Schnalle"))
    assertEquals("\"w:get the upper hand\"", wordSearch("get the upper hand"))
}
@Test fun specialCharactersAreEscaped() = assertEquals("\"w:a\\*b\\_c\\\\d\\\"e\\:f\\(g\\)\"", wordSearch("a*b_c\\d\"e:f(g)"))
@Test fun leadingDashIsEscaped() = assertEquals("\"w:\\-ish\"", wordSearch("-ish"))
@Test fun elsewhereQueryExcludesOurNoteTypeAndDedupes() = assertEquals(
    "-note:\"Moon+ Vocab\" (\"w:Schnalle\" OR \"w:Schnallen\")",
    elsewhereQuery(listOf("Schnalle", "Schnallen", "schnalle")),
)
@Test fun appIdQueryByMid() = assertEquals("mid:42 (AppId:a-1 OR AppId:b-2)", appIdQuery(42, listOf("a-1", "b-2")))
```

- [ ] **Step 2: Run them and see them fail**, then implement `AnkiSearch.kt`, the gateway methods and `FakeAnki`. Run them and see them pass. Then `make check`.

- [ ] **Step 3: Extend the instrumented test (it runs in Task 10, on the phone)**

Add to `AnkiDroidGatewayTest`:
1. A non-Moon+ note type. Look up the name "Moon+ Vocab InstrumentedTest Basic" in `getModelList()`, and create it with `addNewBasicModel` only if it is missing.
2. Add a note of that type with front `Zwiebelkuchenmoonvocab` to `Moon+::InstrumentedTest`. Check:
   - `findElsewhere(listOf("zwiebelkuchenmoonvocab"))` is `"Moon+::InstrumentedTest"`;
   - `findElsewhere(listOf("zwiebelkuchen_oonvocab"))` is null (the escaped `_` is not a wildcard);
   - `findElsewhere(listOf("-zwiebelkuchenmoonvocab"))` is null.
3. Add a Moon+ Vocab note with Word `Zwiebelkuchenmoonvocab2` and AppId `instrumented-uuid-1`. Check:
   - `findElsewhere(listOf("zwiebelkuchenmoonvocab2"))` is null;
   - `findByAppIds(mid, listOf("instrumented-uuid-1", "nope"))` maps only the first uuid, to that note.
4. After the searches, `getNote` on the Basic note shows its fields and tags unchanged.

`@After` deletes every note this test added. The note type and deck stay, and Task 10 tells the user so.

- [ ] **Step 4: Edit spec §5.5**

The line becomes: "`findElsewhere(terms): String?`: the deck name of the first hit, see 5.6."

- [ ] **Step 5: Decisions and commit subject**

Decisions (under AnkiDroid):
- "`findElsewhere` returns the first hit's deck name, `\"\"` when the deck can't be read, null for no hit. It searches `-note:\"Moon+ Vocab\"` with a quoted `w:` term for the lemma and for the focus when it differs, ignoring case."
- "Search terms are always quoted, and `\\ \" * _ : ( )` and a leading `-` are backslash-escaped."
- "Notes are found by AppId with `mid:<mid> (AppId:<uuid> OR …)`, 50 uuids per query."

Commit subject: `feat: AnkiDroid other-source search and AppId lookup`.

---

### Task 5: Merge and search in the import

**Files:**
- Modify:
  - `domain/import/ImportPipeline.kt`
  - `data/db/AppDatabase.kt` (`writeBatch`) and `data/db/Daos.kt` (`CardDao.senseCandidates`)
  - `work/ImportWork.kt`
  - `AppContainer.kt`
  - `ui/debug/DebugScreen.kt`
  - `res/values/strings.xml`
  - unit test `domain/import/ImportTestBase.kt`
- Test: `domain/import/ImportMergeTest.kt` (new, extends `ImportTestBase`), `work/ImportWorkTest.kt`

**Interfaces:**
- Consumes: `SenseJudge` (2), `senseQuestions`, `resolveMerges`, `NewCard`, `KnownCard`, `Merge` (3), `AnkiGateway.findElsewhere` (4).
- Produces:
  - `ImportPipeline(db, reader, books, translator, judge: SenseJudge, anki: AnkiGateway, sender, check, settings, io, draft)`.
  - `ImportSummary(newCards, failed, alreadyCard: Int, skippedDiscarded: Int, sent, incompleteBackup, stopped, sendError)`.
  - `AppDatabase.writeBatch(batch: List<Pair<CardEntity, List<Long>>>, links: List<Pair<Long, Long>> = emptyList()): List<Long>`, where `links` are `(markId, existingCardId)` pairs, linked in the same transaction.
  - `@Query("SELECT * FROM card WHERE status != 'FAILED' AND sourceLanguage IS :language") suspend fun senseCandidates(language: String?): List<CardEntity>`.

Per batch, after the cards are built as today (§5.7 step 4):
1. **Merge.**
   - The batch's pending cards become `NewCard`s:
     - `index` is the card's position in the batch's card list;
     - `language` and `lemma` come from the card;
     - `focus` comes from that item's `Translation`;
     - `sentence` comes from the prepared item.
   - The known cards are `senseCandidates(language)` for each distinct language, mapped to `KnownCard`.
   - If `senseQuestions` is not empty, call `judge.judge` once:
     - `LlmException.Stop` ends the import like a translation stop, with nothing written for this batch;
     - `LlmException.Failed` means no matches.
   - Apply `resolveMerges`:
     - `IntoKnown` drops the card and adds `(markId, cardId)` to `links`;
     - `IntoBatch(j)` drops the card and appends its mark id to card `j`'s mark list.
2. **Search.**
   - Look up `anki.availability()` once per run, on the first batch that has new pending cards.
   - When it is `READY`, each pending card still to be written gets `elsewhereDeck = anki.findElsewhere(terms)`. `terms` is the lemma, plus the focus when it differs from the lemma ignoring case.
   - Any exception other than `CancellationException` is logged and means no label.
3. **Write** with `writeBatch(cards, links)`.
4. **Count.**
   - `alreadyCard` counts `IntoBatch` results, and `IntoKnown` results with PENDING or SENT.
   - `skippedDiscarded` counts `IntoKnown` results with DISCARDED or DELETED_IN_ANKI.
5. **Send** as today: written pending cards with no `elsewhereDeck`.

Elsewhere:
- `ImportWork` carries the two new counts under the keys `alreadyCard` and `skippedDiscarded`.
- The debug screen's summary shows them with plurals `import_already_card` ("%d already a card") and `import_skipped_discarded` ("%d skipped, discarded earlier").
- A card row with `elsewhereDeck` shows "Already in Anki: %s", string `debug_elsewhere`.
- `AppContainer` passes `LlmSenseJudge(llm)` and `anki`.
- `ImportTestBase.pipeline(...)` takes `judge: SenseJudge = SenseJudge { emptyMap() }`.

- [ ] **Step 1: Write the failing tests**

All tests are in `ImportMergeTest`. A judge stub that answers every question with its first candidate is `firstCandidate`. Two marks of the same word are two `NoteRow`s with the same `original` and different `time`.

```kotlin
@Test fun sameSenseMarkJoinsExistingCard()            // import word A; then a second mark of A with firstCandidate → 1 card, both marks link it, alreadyCard == 1, newCards == 0
@Test fun markMatchingDiscardedCardIsSkipped()        // set the first card DISCARDED; second import → skippedDiscarded == 1, no new card, mark links the discarded card
@Test fun sameSenseWithinBatchMakesOneCard()          // two marks of A in one backup → 1 card linked by both marks, alreadyCard == 1
@Test fun differentSenseMakesTwoCards()               // judge answers nothing → 2 cards
@Test fun judgeIsNotAskedWithoutCandidates()          // 3 different words → judge never called
@Test fun senseStopWritesNothingForTheBatch()         // judge throws Stop(AUTH) → stopped == AUTH, no cards, all marks without card
@Test fun senseFailureMakesNewCards()                 // judge throws Failed → 2 cards
@Test fun everyMarkIsAccountedFor()                   // 12 marks, 3 of them repeats: newCards + alreadyCard + skippedDiscarded + failed == 12
@Test fun elsewhereHitLabelsCardAndHoldsItBack()      // fakeAnki.elsewhere["w0x"] = "German::Core", send mode → card PENDING with elsewhereDeck "German::Core", fakeAnki.adds == 0
@Test fun elsewhereSearchesLemmaAndDifferentFocus()   // translator gives lemma "Schnalle", focus "Schnallen" → fakeAnki.searches == [["Schnalle", "Schnallen"]]
@Test fun elsewhereSkippedWhenAnkiUnavailable()       // availability NO_PERMISSION → no searches, elsewhereDeck null
@Test fun elsewhereFailureMeansNoLabel()              // failElsewhere → import completes, elsewhereDeck null
```

In `ImportWorkTest`, the summary round trip carries `alreadyCard` and `skippedDiscarded`.

- [ ] **Step 2: Run them and see them fail**

Run: `timeout --signal=KILL 540 ./gradlew :app:testDebugUnitTest --tests '*ImportMergeTest' --tests '*ImportWorkTest'`

- [ ] **Step 3: Implement**

Keep `run` readable. Put the merge step and the search step in private functions of `ImportPipeline`, each taking the batch's cards and returning what the write needs.

- [ ] **Step 4: Run all tests with `make check` and see them pass**

- [ ] **Step 5: Decisions and commit subject**

Decisions:
- Under Import pipeline, replace "Each card links exactly one mark." with: "A card links one or more marks. The mark that made it is the first; later same-sense marks only link to it."
- Under Import pipeline, add:
  - "Per batch: translate, merge same-sense marks, search other sources, then write and link in one transaction."
  - "A sense `Stop` ends the import with nothing written for that batch. A sense `Failed` means no merges."
  - "AnkiDroid availability is read once per run for the other-source search. A search error means no label."

Commit subject: `feat: same-sense merge and "Already in Anki" label in the import`.

---

### Task 6: Sending that survives errors and process death

Fixes tickets mv-tdu0 and mv-x2sp.

**Files:**
- Modify: `domain/import/CardSender.kt`, `domain/import/ImportPipeline.kt`
- Test: `domain/import/CardSenderTest.kt`, `domain/import/ImportPipelineTest.kt`

**Interfaces:**
- Consumes: `AnkiGateway.findByAppIds` (4), `FakeAnki.ensureCalls` (4).

Behaviour:
- **Note type once.**
  - `CardSender` remembers the `(mid, version)` from its last `ensureNoteType` and does not call it again while it has one.
  - Any send error forgets it.
- **Adopt before adding.**
  - Before adding, `send` calls `findByAppIds(mid, pending uuids)` once.
  - A card whose uuid is found is saved as SENT with that note id, without `add`, and counts as sent.
- **Stop after an error.**
  - Once a send in a run returns an error, `ImportPipeline.run` sends nothing more in that run.
  - The remaining cards stay pending, and `sendError` reports the first error.

- [ ] **Step 1: Write the failing tests**

```kotlin
// CardSenderTest
@Test fun noteAlreadyInAnkiIsAdoptedNotAddedAgain()  // pending card; fakeAnki.notes has a note with AppId = its uuid → SENT with that note id, fakeAnki.adds == 0
@Test fun noteTypeIsEnsuredOncePerSender()            // two send calls → fakeAnki.ensureCalls == 1
@Test fun sendErrorForgetsTheNoteType()               // failOnNthAdd = 1; send, then send again → ensureCalls == 2
// ImportPipelineTest
@Test fun sendErrorStopsLaterSendsInTheRun()          // 25 marks, send mode, failOnNthAdd = 1 → fakeAnki.adds == 1, sendError set, 25 PENDING
```

- [ ] **Step 2: Run them and see them fail**, then implement. Run them and see them pass. Then `make check`.

- [ ] **Step 3: Decisions, tickets, commit subject**

Decisions (under Import pipeline, `CardSender`):
- "the note type is ensured once per sender and again after a send error;"
- "before adding, notes already in AnkiDroid with the card's AppId are adopted;"
- "after a send error the import sends nothing more in that run."

Then `tk close mv-tdu0 mv-x2sp`. Commit subject: `fix: adopt notes by AppId and stop sending after an error`.

---

### Task 7: The AnkiDroid check compares what the card stores

Fixes ticket mv-pwas. Today, formatting added in Anki, or a Book line that gained an author, refreshes the card on every check.

**Files:**
- Modify:
  - `data/anki/AnkiText.kt`: add `fun CardEntity.storedFields(): Map<String, String>`;
  - `data/anki/NoteTypeSpec.kt`: add `STORED_FIELDS` and `HTML_FIELDS`;
  - `domain/card/Reconcile.kt`;
  - `domain/import/AnkiCheck.kt`.
- Test: `domain/card/ReconcileTest.kt`

**Interfaces:**
- `NoteTypeSpec.STORED_FIELDS` is `Word, Grammar, Excerpt, Translation, Meaning, Sentence, Source`. `HTML_FIELDS` is `Excerpt, Sentence`.
- `storedFields()` maps the stored fields to the card's values: plain text for the plain fields, and the HTML for `Excerpt` and `Sentence`.
- `SentCard.fields` now holds `storedFields()`.

`reconcile` changes:
- It compares only the stored fields, and only those present in the snapshot.
- A plain field compares `ankiToPlain(theirs)` with ours. An HTML field compares raw.
- `Refresh` still carries the snapshot's stored fields.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun formattingOnlyChangeIsNotARefresh()   // theirs Translation "<b>пряжка</b>", ours "пряжка" → no action
@Test fun escapedTextComparesEqual()            // ours Translation "a & b", theirs "a &amp; b" → no action
@Test fun bookAndAppIdAreNotCompared()          // theirs Book "Diaspora", AppId "x"; ours differ → no action
@Test fun fieldRemovedInAnkiIsNotARefresh()     // snapshot without Meaning → no action
@Test fun plainTextEditIsRefreshed()            // theirs Translation "застёжка" → Refresh
@Test fun excerptHtmlEditIsRefreshed()          // theirs Excerpt differs in markup → Refresh
```

Update the existing tests that build `SentCard` from `ankiFields`. `fieldOnlyOnOneSideCounts` (a field missing from the note counted as a change) is replaced by `fieldRemovedInAnkiIsNotARefresh`. A field present in the note that differs from ours, including one we left empty, still counts.

- [ ] **Step 2: Run them and see them fail**, then implement. Run them and see them pass. Then `make check`.

- [ ] **Step 3: Decisions, tickets, commit subject**

Decisions (under AnkiDroid): replace "`reconcile` compares the note type's fields that are present on either side." with: "`reconcile` compares the fields the card stores (Word, Grammar, Excerpt, Translation, Meaning, Sentence, Source) that are present in the note: plain fields as `ankiToPlain` text, Excerpt and Sentence as raw HTML."

Then:
- `tk close mv-pwas`;
- `tk create` a ticket: "AnkiCheck runs outside CardSender's mutex; a concurrent deleteAllCreated can be overwritten by a stale Missing action (debug screen only)".

Commit subject: `fix: AnkiDroid check ignores formatting and derived fields`.

---

### Task 8: Bounded memory for the full import

Fixes tickets mv-cu5t and mv-nnrc. The full backup touches dozens of books, and today every book's text and normalized chapters stay in memory for the whole run.

**Files:**
- Modify:
  - `domain/import/ImportPipeline.kt` (`Run` caches);
  - `data/books/BookSource.kt` (`FolderBookSource` keeps no texts; catches `OutOfMemoryError` for one book);
  - `data/books/Fb2Text.kt`.
- Test: `domain/import/ImportPipelineTest.kt`, `data/books/BookTextTest.kt`, `data/books/FolderBookSourceTest.kt`

Behaviour:
- `Run` keeps the text and normalized chapters of at most 3 books, least recently used first out. A book evicted and needed again is read again.
- `FolderBookSource` no longer caches texts. The run's cache is the only one.
- When one book's read throws `OutOfMemoryError`, `FolderBookSource` logs it and returns null for that book.
- `Fb2Text.read` reads the bytes and parses only those before the first `<binary`. FB2 places binaries after the bodies.

- [ ] **Step 1: Write the failing tests**

```kotlin
// ImportPipelineTest. Marks are processed newest first; build them so the books come up in the order shown.
@Test fun threeBooksStayInMemory()      // books A B C A → fakeBooks.reads[A] == 1
@Test fun fourthBookEvictsTheOldest()   // books A B C D A → fakeBooks.reads[A] == 2
// BookTextTest
@Test fun fb2StopsAtTheFirstBinary()    // FB2 fixture: main body, then a base64 <binary>, then an unnamed <body><section><p>after</p></section></body> → chapters are the main body's only ("after" is not read)
```

`eachBookIsReadOnce` stays and must still pass.

In `FolderBookSourceTest.locatedBookIsCopiedReadOnceAndCleanedUp`, drop the read-once assertion and keep the copy-and-cleanup check. Rename the test to `locatedBookIsCopiedAndCleanedUp`.

- [ ] **Step 2: Run them and see them fail**, then implement. Run them and see them pass. Then `make check`.

- [ ] **Step 3: Decisions, tickets, commit subject**

Decisions:
- Under Sentences, replace "Each run normalizes a book's chapters once." with: "Each run keeps the text and normalized chapters of at most 3 books, least recently used out."
- Under Book text, add:
  - "An FB2 is parsed only up to its first `<binary`."
  - "`FolderBookSource` keeps no texts. An `OutOfMemoryError` while reading one book gives that book no text."

Then `tk close mv-cu5t mv-nnrc`. Commit subject: `fix: bound book memory during long imports`.

---

### Task 9: Debug tools for tuning and the real import

**Files:**
- Modify:
  - `ui/debug/DebugScreen.kt`, `ui/debug/DebugViewModel.kt`;
  - `data/db/Daos.kt`: add `CardDao.deleteUnsent()`, `MarkDao.all()`, `BookDao.all()`;
  - `res/values/strings.xml`.
- Create: `ui/debug/CardsExport.kt`
- Test: `data/db/MarkQueriesTest.kt`

Controls:
- **Send to test deck.** A switch that is on while `deckOverride != null`. Off sets `deckOverride = null`, and on sets it to `"Moon+::Test"`. Cards then go to `Moon+::<Language>`.
- **Export cards.**
  - Opens `ACTION_CREATE_DOCUMENT` (`application/json`, name `moon-vocab-cards-<yyyy-MM-dd>.json`).
  - Writes `cardsExportJson(cards, marks, books)`: a JSON array with one object per card. Each object has `id`, `status`, `book`, `sourceLanguage`, `word`, `lemma`, `grammar`, `translation`, `meaning`, `excerptHtml`, `sentenceHtml`, `elsewhereDeck`, `error`.
  - Each object also has `marks`, an array of `{text, chapter, position, markedAt}` in `markedAt` order.
  - No settings and no keys go in the file.
- **Forget unsent cards.**
  - A confirm dialog runs `deleteUnsent()`: `DELETE FROM card WHERE status IN ('PENDING','FAILED') AND ankiNoteId IS NULL`.
  - Their marks lose their card through the foreign key, so the next Import (no backup needed) translates them again.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test fun forgetUnsentKeepsSentAndDiscardedCards() // cards PENDING, FAILED, SENT(note 7), DISCARDED, each with a mark → after deleteUnsent: SENT and DISCARDED remain, the PENDING and FAILED cards' marks have cardId null
```

No test for the export or the switch: they are debug tools, and their logic is the DAO query above.

- [ ] **Step 2: Run it and see it fail**, then implement. Run it and see it pass. Then `make check`.

- [ ] **Step 3: Decisions and commit subject**

Decisions (under App):
- "The debug screen can turn off the test deck, export cards as JSON (no settings or keys), and forget unsent PENDING and FAILED cards."

Commit subject: `feat: debug tools for tuning and the real import`.

---

### Task 10: On the phone: tuning, then the real import (ask the user first)

This task tunes the merge and the search on the user's real headwords (§10, milestone 2), then imports the full backup into the real decks. It runs in the main session with the user, not in a subagent.

**Files:** whatever the tuning changes, typically:
- `data/llm/SensePrompt.kt`, `data/llm/Prompt.kt` (lemma rules);
- `domain/card/SameSense.kt` (`senseKey`);
- their tests;
- `docs/DECISIONS.md`.

- [ ] **Step 1: Prerequisite**

If milestone 1's Task 14 (instrumented test and manual one-book run) has not been done, do it first. Its instrumented test now includes Task 4's additions. `connectedDebugAndroidTest` uninstalls the app afterwards. It leaves the "Moon+ Vocab InstrumentedTest Basic" note type and the empty `Moon+::InstrumentedTest` deck. Say so before running it.

- [ ] **Step 2: Ask the user**

Say what happens next: a new build on the phone, imports into `Moon+::Test` with Send off, and a cards export written where the user chooses. Ask whether to install with `make install` or to send the release APK. Wait for a yes.

- [ ] **Step 3: First tuning import**

The user imports the 3–4 books with the most wavy marks (Fire & Blood among them) with Send off and the test deck on. Then they export the cards to a folder synced to the laptop and tell me the path. During the import, watch for heap trouble. Watch Fire & Blood's time.

- [ ] **Step 4: Analyze the export**

Report each of these to the user as a short table:
1. Same-lemma groups: correct merges, missed merges (two cards with the same sense), and false merges (one card whose marks mean different things).
2. Lemma variants that should have been compared, such as an article, reflexive `sich`, English `to`, or capitalization. Fix these in the prompt's lemma rule or in `senseKey`, with a test.
3. Other-source labels: plausible, or noise. The user knows which of their decks overlap.
4. `sourceLanguage` values and the decks they would produce.
5. Sentences that came from the wrong occurrence of a repeated word (ticket mv-acff). Fix only if seen.

- [ ] **Step 5: Change, test, repeat**

Apply the fixes the user agrees to, with tests and the Decisions lines. Run `make check`, build, and ask before installing again. On the phone, run Forget unsent cards and import again. Stop after 3 rounds, or sooner when the user is satisfied.

- [ ] **Step 6: The real import (ask first)**

On the phone, in order:
1. "Delete every note this build created", which empties `Moon+::Test`.
2. Forget unsent cards.
3. Switch Send to test deck off.
4. Import all wavy marks. The user chooses Send on or off.

Then check:
- the summary adds up: new + already a card + skipped + failed = wavy marks;
- the decks are `Moon+::<Language>`, with no `Other` unless the user agrees;
- `note:"Moon+ Vocab"` in AnkiDroid counts the same as the SENT cards;
- cards labelled "Already in Anki" stayed pending.

"Send all pending" on the debug screen would send the labelled cards too. Tell the user before they press it. The empty `Moon+::Test` deck stays for the user to delete.

- [ ] **Step 7: Report and close out**

Report the counts and anything odd, and turn follow-ups into tickets. Milestone 2 is done when the user agrees the real decks look right. Commit subject: `chore: tune merge and search on real data`.

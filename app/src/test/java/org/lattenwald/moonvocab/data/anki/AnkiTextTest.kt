package org.lattenwald.moonvocab.data.anki

import org.junit.Assert.assertEquals
import org.junit.Test
import org.lattenwald.moonvocab.data.db.CardEntity
import org.lattenwald.moonvocab.domain.card.CardStatus

class AnkiTextTest {
    private val card = CardEntity(
        uuid = "0f8e-uuid",
        bookId = 1,
        sourceLanguage = "en",
        targetLanguage = "ru",
        word = "R&D <lab>",
        lemma = "lab",
        grammar = "n. > pl.",
        excerptHtml = "the <span class=\"mark\">R&amp;D</span> lab",
        translation = "НИОКР & <лаборатория>",
        meaning = "a < b",
        sentenceHtml = "In the <span class=\"mark\">R&amp;D</span> lab.",
        sourceNote = "example by <model>",
        widened = false,
        invented = false,
        elsewhereDeck = null,
        status = CardStatus.PENDING,
        previousStatus = null,
        ankiNoteId = null,
        ankiMod = null,
        sentAt = null,
        error = null,
    )

    @Test fun escapesPlainFieldsAndPassesHtmlThrough() = assertEquals(
        mapOf(
            "Word" to "R&amp;D &lt;lab&gt;",
            "Grammar" to "n. &gt; pl.",
            "Excerpt" to "the <span class=\"mark\">R&amp;D</span> lab",
            "Translation" to "НИОКР &amp; &lt;лаборатория&gt;",
            "Meaning" to "a &lt; b",
            "Sentence" to "In the <span class=\"mark\">R&amp;D</span> lab.",
            "Book" to "Q&amp;A, &lt;Anon&gt;",
            "Source" to "example by &lt;model&gt;",
            "AppId" to "0f8e-uuid",
        ),
        card.ankiFields("Q&A, <Anon>"),
    )

    @Test fun coversEveryNoteTypeField() = assertEquals(NoteTypeSpec.FIELDS, card.ankiFields("B").keys.toList())

    @Test fun storedFieldsArePlainExceptExcerptAndSentence() = assertEquals(
        mapOf(
            "Word" to card.word,
            "Grammar" to card.grammar,
            "Excerpt" to card.excerptHtml,
            "Translation" to card.translation,
            "Meaning" to card.meaning,
            "Sentence" to card.sentenceHtml,
            "Source" to card.sourceNote,
        ),
        card.storedFields(),
    )

    @Test fun plainFieldsSurviveTheRoundTrip() {
        val fields = card.ankiFields("Q&A, <Anon>")
        assertEquals(
            listOf(card.word, card.grammar, card.translation, card.meaning, "Q&A, <Anon>", card.sourceNote),
            listOf("Word", "Grammar", "Translation", "Meaning", "Book", "Source").map { ankiToPlain(fields.getValue(it)) },
        )
    }

    @Test fun lineBreaksBecomeNewlines() = assertEquals("one\ntwo\nthree", ankiToPlain("<div>one<br>two</div><div>three</div>"))

    @Test fun tagsStrippedAndEntitiesUnescaped() =
        assertEquals("bold & \"quoted\" x", ankiToPlain("<b>bold</b> &amp; &quot;quoted&quot; <i style=\"c\">x</i>"))

    @Test fun trimmed() = assertEquals("word", ankiToPlain("  <div>word</div><br> "))

    @Test fun blankLineDivIsOneNewline() = assertEquals("one\n\ntwo", ankiToPlain("one<div><br></div><div>two</div>"))

    @Test fun nonBreakingSpacesBecomePlainSpaces() =
        assertEquals("get the upper hand", ankiToPlain("get&nbsp;the upper&nbsp;hand&nbsp;"))
}

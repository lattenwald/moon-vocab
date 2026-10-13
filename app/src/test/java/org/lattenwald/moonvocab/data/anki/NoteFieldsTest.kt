package org.lattenwald.moonvocab.data.anki

import org.junit.Assert.assertEquals
import org.junit.Test
import org.lattenwald.moonvocab.data.db.CardEntity
import org.lattenwald.moonvocab.domain.card.CardStatus

class NoteFieldsTest {
    private val card = CardEntity(
        uuid = "0f8e-uuid",
        bookId = 1,
        sourceLanguage = "en",
        targetLanguage = "ru",
        word = "R&D <lab>",
        transcription = "ˌɑːr ən ˈdiː <læb>",
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
            "Transcription" to "ˌɑːr ən ˈdiː &lt;læb&gt;",
            "Grammar" to "n. &gt; pl.",
            "Excerpt" to "the <span class=\"mark\">R&amp;D</span> lab",
            "Translation" to "НИОКР &amp; &lt;лаборатория&gt;",
            "Meaning" to "a &lt; b",
            "Sentence" to "In the <span class=\"mark\">R&amp;D</span> lab.",
            "Book" to "Q&amp;A, &lt;Anon&gt;",
            "Source" to "example by &lt;model&gt;",
            "AppId" to "0f8e-uuid",
        ),
        card.toNote("Q&A, <Anon>"),
    )

    @Test fun coversEveryNoteTypeField() = assertEquals(NoteTypeSpec.FIELDS, card.toNote("B").keys.toList())

    @Test fun everyStoredFieldSurvivesTheRoundTrip() = assertEquals(card, card.readFrom(card.toNote("Q&A, <Anon>")))

    @Test fun anEditInAnkiDroidIsReadBackPlainExceptExcerptAndSentence() = assertEquals(
        card.copy(translation = "neu & mehr", sourceNote = "by me", excerptHtml = "the <b>lab</b>"),
        card.readFrom(
            card.toNote("B") +
                mapOf("Translation" to "<div>neu &amp; mehr</div>", "Source" to "by me", "Excerpt" to "the <b>lab</b>"),
        ),
    )

    @Test fun formattingOnlyChangeReadsAsTheSameCard() =
        assertEquals(card, card.readFrom(card.toNote("B") + ("Translation" to "<b>${card.toNote("B").getValue("Translation")}</b>")))

    @Test fun bookAndAppIdAreNotReadBack() = assertEquals(card, card.readFrom(card.toNote("B") + mapOf("Book" to "Other", "AppId" to "x")))

    @Test fun aFieldMissingFromTheNoteKeepsTheCardsValue() = assertEquals(card, card.readFrom(card.toNote("B") - "Meaning"))

    @Test fun lineBreaksBecomeNewlines() = assertEquals("one\ntwo\nthree", ankiToPlain("<div>one<br>two</div><div>three</div>"))

    @Test fun tagsStrippedAndEntitiesUnescaped() =
        assertEquals("bold & \"quoted\" x", ankiToPlain("<b>bold</b> &amp; &quot;quoted&quot; <i style=\"c\">x</i>"))

    @Test fun trimmed() = assertEquals("word", ankiToPlain("  <div>word</div><br> "))

    @Test fun blankLineDivIsOneNewline() = assertEquals("one\n\ntwo", ankiToPlain("one<div><br></div><div>two</div>"))

    @Test fun nonBreakingSpacesBecomePlainSpaces() =
        assertEquals("get the upper hand", ankiToPlain("get&nbsp;the upper&nbsp;hand&nbsp;"))
}

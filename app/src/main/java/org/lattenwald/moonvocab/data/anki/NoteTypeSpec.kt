package org.lattenwald.moonvocab.data.anki

object NoteTypeSpec {
    const val NAME = "Moon+ Vocab"
    const val VERSION = 2
    const val CARD_NAME = "Recall"

    val FIELDS = listOf("Word", "Transcription", "Grammar", "Excerpt", "Translation", "Meaning", "Sentence", "Book", "Source", "AppId")

    // Appended to older note types; left out of notes whose type lacks them.
    val OPTIONAL_FIELDS = setOf("Transcription")

    val STORED_FIELDS = listOf("Word", "Transcription", "Grammar", "Excerpt", "Translation", "Meaning", "Sentence", "Source")
    val HTML_FIELDS = setOf("Excerpt", "Sentence")

    const val FRONT = """<div class="word">{{Word}}</div>{{#Transcription}}<div class="ipa">/{{Transcription}}/</div>{{/Transcription}}""" +
        """<div class="gram">{{Grammar}}</div><div class="ex">{{Excerpt}}</div>"""

    const val BACK = """{{FrontSide}}<hr id="answer"><div class="tr">{{Translation}}</div>""" +
        """{{#Meaning}}<div class="mean">{{Meaning}}</div>{{/Meaning}}""" +
        """{{#Sentence}}<div class="sent">{{Sentence}}</div>{{/Sentence}}""" +
        """<div class="book">{{Book}}</div>{{#Source}}<div class="src">{{Source}}</div>{{/Source}}"""

    val CSS = """
        .card{font-family:sans-serif;text-align:center;padding:24px 16px}
        .word{font-size:32px;font-weight:500}.ipa{font-size:17px;opacity:.7;margin-top:4px}.gram{font-family:serif;font-style:italic;opacity:.7}
        .ex,.sent{font-family:serif;font-size:19px;line-height:1.55;margin-top:18px}.sent{font-size:16px;text-align:left}
        .tr{font-size:24px;font-weight:500}.mean,.book,.src{font-size:14px;opacity:.7;margin-top:8px}
        hr#answer{width:40%;margin:22px auto 12px;border:0;border-top:1px solid #8888}
        .mark{text-decoration:underline wavy #D9480F;text-decoration-thickness:1.5px;text-underline-offset:4px}
        .nightMode .mark{text-decoration-color:#FF8A50}
    """.trimIndent()
}

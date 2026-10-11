package org.lattenwald.moonvocab.data.db

import androidx.room.Embedded

data class InboxRow(
    @Embedded val card: CardEntity,
    val bookTitle: String,
    val bookAuthor: String?,
    val markedAt: Long?,
)

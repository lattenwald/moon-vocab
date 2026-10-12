package org.lattenwald.moonvocab.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import org.lattenwald.moonvocab.domain.card.CardStatus
import org.lattenwald.moonvocab.domain.mark.MarkStyle

@Entity(tableName = "book", indices = [Index(value = ["fileKey"], unique = true)])
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileKey: String,
    val title: String,
    val author: String?,
    val language: String?,
    val relativePath: String?,
)

@Entity(
    tableName = "mark",
    indices = [Index(value = ["key"], unique = true), Index("cardId"), Index("bookId")],
    foreignKeys = [ForeignKey(entity = CardEntity::class, parentColumns = ["id"], childColumns = ["cardId"], onDelete = ForeignKey.SET_NULL)],
)
data class MarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val bookId: Long,
    val chapter: Int,
    val position: Int,
    val text: String,
    val style: MarkStyle,
    val color: Int,
    val markedAt: Long,
    val cardId: Long?,
    @ColumnInfo(defaultValue = "0") val split: Int = 0,
)

@Entity(tableName = "card", indices = [Index(value = ["uuid"], unique = true), Index("status"), Index("lemma")])
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val bookId: Long,
    val sourceLanguage: String?,
    val targetLanguage: String,
    val word: String,
    val lemma: String,
    val grammar: String,
    val excerptHtml: String,
    val translation: String,
    val meaning: String,
    val sentenceHtml: String,
    val sourceNote: String,
    val widened: Boolean,
    val invented: Boolean,
    val elsewhereDeck: String?,
    val status: CardStatus,
    val previousStatus: CardStatus?,
    val ankiNoteId: Long?,
    val ankiMod: Long?,
    val sentAt: Long?,
    val error: String?,
)

class Converters {
    @TypeConverter fun markStyleToName(style: MarkStyle): String = style.name
    @TypeConverter fun markStyleFromName(name: String): MarkStyle = MarkStyle.valueOf(name)
    @TypeConverter fun cardStatusToName(status: CardStatus?): String? = status?.name
    @TypeConverter fun cardStatusFromName(name: String?): CardStatus? = name?.let(CardStatus::valueOf)
}

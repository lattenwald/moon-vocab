package org.lattenwald.moonvocab.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.lattenwald.moonvocab.domain.card.CardStatus

@Dao
abstract class BookDao {
    @Transaction
    open suspend fun upsertByFileKey(book: BookEntity): Long {
        val inserted = insertIgnoring(book.copy(id = 0))
        if (inserted != -1L) return inserted
        val old = checkNotNull(byFileKey(book.fileKey))
        update(
            old.copy(
                title = book.title,
                author = book.author ?: old.author,
                relativePath = book.relativePath ?: old.relativePath,
                language = old.language ?: book.language,
            ),
        )
        return old.id
    }

    @Query("SELECT * FROM book WHERE id = :id")
    abstract suspend fun byId(id: Long): BookEntity?

    @Query("SELECT * FROM book")
    abstract suspend fun all(): List<BookEntity>

    @Query("UPDATE book SET language = :language WHERE id = :id AND language IS NULL")
    abstract suspend fun setLanguageIfMissing(id: Long, language: String)

    @Query("UPDATE book SET author = :author WHERE id = :id AND author IS NULL")
    abstract suspend fun setAuthorIfMissing(id: Long, author: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertIgnoring(book: BookEntity): Long

    @Query("SELECT * FROM book WHERE fileKey = :fileKey")
    protected abstract suspend fun byFileKey(fileKey: String): BookEntity?

    @Update
    protected abstract suspend fun update(book: BookEntity)
}

@Dao
interface MarkDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringKnown(marks: List<MarkEntity>): List<Long>

    @Query("SELECT * FROM mark WHERE cardId IS NULL AND (:bookId IS NULL OR bookId = :bookId) ORDER BY markedAt DESC, id DESC")
    suspend fun withoutCard(bookId: Long?): List<MarkEntity>

    @Query("UPDATE mark SET cardId = :cardId WHERE id IN (:markIds)")
    suspend fun link(markIds: List<Long>, cardId: Long)

    @Query("UPDATE mark SET cardId = NULL WHERE cardId IN (:cardIds)")
    suspend fun unlink(cardIds: List<Long>)

    @Query("SELECT * FROM mark ORDER BY markedAt, id")
    suspend fun all(): List<MarkEntity>
}

@Dao
interface CardDao {
    @Insert
    suspend fun insert(card: CardEntity): Long

    @Update
    suspend fun update(card: CardEntity)

    @Query("SELECT * FROM card WHERE status = :status ORDER BY id")
    suspend fun byStatus(status: CardStatus): List<CardEntity>

    @Query("SELECT * FROM card ORDER BY id")
    fun observeAll(): Flow<List<CardEntity>>

    @Query("SELECT * FROM card WHERE status != 'FAILED' AND sourceLanguage IS :language")
    suspend fun senseCandidates(language: String?): List<CardEntity>

    @Query("SELECT * FROM card WHERE ankiNoteId IS NOT NULL ORDER BY id")
    suspend fun withNoteIds(): List<CardEntity>

    @Query("DELETE FROM card WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("DELETE FROM card WHERE status IN ('PENDING','FAILED') AND ankiNoteId IS NULL")
    suspend fun deleteUnsent(): Int
}

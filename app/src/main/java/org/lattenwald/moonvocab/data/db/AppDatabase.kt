package org.lattenwald.moonvocab.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [BookEntity::class, MarkEntity::class, CardEntity::class], version = 2, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun books(): BookDao
    abstract fun marks(): MarkDao
    abstract fun cards(): CardDao

    /** [links] are `(markId, existingCardId)` pairs. */
    suspend fun writeBatch(batch: List<Pair<CardEntity, List<Long>>>, links: List<Pair<Long, Long>> = emptyList()): List<Long> =
        withTransaction {
            for ((markId, cardId) in links) marks().link(listOf(markId), cardId)
            batch.map { (card, markIds) ->
                val id = cards().insert(card)
                marks().link(markIds, id)
                id
            }
        }

    companion object {
        const val NAME = "moon-vocab.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE mark ADD COLUMN split INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}

package org.lattenwald.moonvocab.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun createVersion(version: Int, file: File) {
        val schema = File("schemas/${AppDatabase::class.qualifiedName}/$version.json")
        val database = Json.parseToJsonElement(schema.readText()).jsonObject.getValue("database").jsonObject
        val statements = database.getValue("setupQueries").jsonArray.map { it.jsonPrimitive.content } +
            database.getValue("entities").jsonArray.flatMap { e ->
                val table = e.jsonObject.getValue("tableName").jsonPrimitive.content
                val indices = e.jsonObject["indices"]?.jsonArray.orEmpty().map { it.jsonObject.getValue("createSql").jsonPrimitive.content }
                (listOf(e.jsonObject.getValue("createSql").jsonPrimitive.content) + indices).map { it.replace("\${TABLE_NAME}", table) }
            }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            statements.forEach(db::execSQL)
            db.execSQL("INSERT INTO book (id, fileKey, title) VALUES (1, 'dune.epub', 'Dune')")
            db.execSQL(
                "INSERT INTO mark (key, bookId, chapter, position, text, style, color, markedAt) " +
                    "VALUES ('k', 1, 4, 120, 'kanly', 'WAVY', -28160, 1)",
            )
            db.version = version
        }
    }

    @Test fun marksKeepTheirDataAndGetSplitZero() = runTest {
        val file = context.getDatabasePath("migration-test.db").apply { parentFile!!.mkdirs(); delete() }
        createVersion(1, file)
        val db = Room.databaseBuilder(context, AppDatabase::class.java, file.path).addMigrations(AppDatabase.MIGRATION_1_2)
            .allowMainThreadQueries().build()
        try {
            val mark = db.marks().all().single()
            assertEquals(listOf("kanly", "4", "120", "0"), listOf(mark.text, "${mark.chapter}", "${mark.position}", "${mark.split}"))
        } finally {
            db.close()
        }
    }
}

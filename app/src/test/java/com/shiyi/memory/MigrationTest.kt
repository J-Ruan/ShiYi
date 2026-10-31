package com.shiyi.memory

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.shiyi.memory.data.MemoryDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = MemoryApplication::class)
class MigrationTest {
    @Test
    fun versionOneDataSurvivesMigrationWithoutFabricatedCompletionTime() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val path = context.getDatabasePath("migration-test.db")
        context.deleteDatabase(path.name)
        path.parentFile!!.mkdirs()
        val schema =
            Json.parseToJsonElement(
                    javaClass.getResourceAsStream("/schema-v1.json")!!.bufferedReader().use {
                        it.readText()
                    }
                )
                .jsonObject["database"]!!
                .jsonObject
        SQLiteDatabase.openOrCreateDatabase(path, null).use { sqlite ->
            schema["entities"]!!.jsonArray.forEach { element ->
                val entity = element.jsonObject
                val name = entity["tableName"]!!.jsonPrimitive.content
                sqlite.execSQL(
                    entity["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", name)
                )
                entity["indices"]?.jsonArray.orEmpty().forEach { index ->
                    sqlite.execSQL(
                        index.jsonObject["createSql"]!!
                            .jsonPrimitive
                            .content
                            .replace("\${TABLE_NAME}", name)
                    )
                }
            }
            schema["setupQueries"]!!.jsonArray.forEach { sqlite.execSQL(it.jsonPrimitive.content) }
            sqlite.execSQL(
                "INSERT INTO Wish (id,title,description,category,image,plannedDate,status,notes,memoryId,createdAt,updatedAt) VALUES ('old','历史心愿','','其他愿望','','',2,'保留的备注',NULL,1,2)"
            )
            sqlite.version = 1
        }
        val database = MemoryDatabase.open(context, path.name)
        try {
            val wish = database.dao().wishes().single()
            assertEquals("历史心愿", wish.title)
            assertEquals("保留的备注", wish.notes)
            assertEquals(2, wish.status)
            assertNull(wish.completedAt)
            assertEquals(2, database.openHelper.readableDatabase.version)
        } finally {
            database.close()
            context.deleteDatabase(path.name)
        }
    }
}

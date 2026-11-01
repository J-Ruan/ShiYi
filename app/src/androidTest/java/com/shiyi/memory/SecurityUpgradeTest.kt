package com.shiyi.memory

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shiyi.memory.data.*
import com.shiyi.memory.domain.Crypto
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecurityUpgradeTest {
    private lateinit var root: File
    private lateinit var context: Context

    @Before
    fun setup() {
        TestDeviceGuard.requireEmptyInstallation()
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(base.cacheDir, "isolated-security-${newId()}").apply { mkdirs() }
        context =
            object : ContextWrapper(base) {
                override fun getApplicationContext(): Context = this

                override fun getFilesDir() = File(root, "files").apply { mkdirs() }

                override fun getNoBackupFilesDir() = File(root, "no-backup").apply { mkdirs() }

                override fun getCacheDir() = File(root, "cache").apply { mkdirs() }

                override fun getDatabasePath(name: String) =
                    File(root, "databases/$name").apply { parentFile!!.mkdirs() }

                override fun deleteDatabase(name: String) =
                    android.database.sqlite.SQLiteDatabase.deleteDatabase(getDatabasePath(name))
            }
    }

    @After
    fun close() {
        root.deleteRecursively()
    }

    private fun fixture(): File {
        val f = File(context.cacheDir, "synthetic.png")
        val bitmap = Bitmap.createBitmap(90, 60, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.MAGENTA)
        f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return f
    }

    private fun legacy(image: String) {
        val schema =
            Json.parseToJsonElement(
                    InstrumentationRegistry.getInstrumentation()
                        .context
                        .assets
                        .open("legacy-v2.json")
                        .bufferedReader()
                        .readText()
                )
                .jsonObject["database"]!!
                .jsonObject
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(
                context.getDatabasePath("memory.db"),
                null,
            )
            .use { db ->
                schema["entities"]!!.jsonArray.forEach { item ->
                    val e = item.jsonObject
                    val name = e["tableName"]!!.jsonPrimitive.content
                    db.execSQL(
                        e["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", name)
                    )
                    e["indices"]!!.jsonArray.forEach {
                        db.execSQL(
                            it.jsonObject["createSql"]!!
                                .jsonPrimitive
                                .content
                                .replace("\${TABLE_NAME}", name)
                        )
                    }
                }
                schema["setupQueries"]!!.jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
                db.execSQL(
                    "INSERT INTO Memory VALUES ('old','瞬间记录','历史合成记录','保留描述','2020-02-29','','','','','','','','','',0,1,?,1,2)",
                    arrayOf(image),
                )
                db.execSQL(
                    "INSERT INTO MemoryMedia VALUES ('photo','old',?,0,'image/png',1)",
                    arrayOf(image),
                )
                db.execSQL(
                    "INSERT INTO PartnerProfile VALUES (1,'TA','我','','2020-01-01','保留档案',?,1)",
                    arrayOf(image),
                )
                db.execSQL(
                    "INSERT INTO ProfileEntry VALUES ('entry','细节','内容','喜欢的东西',2,'标签',1,1,2)"
                )
                db.execSQL("INSERT INTO ProfileMemoryCrossRef VALUES ('entry','old')")
                db.execSQL("INSERT INTO Tag VALUES ('tag','旧标签')")
                db.execSQL("INSERT INTO MemoryTagCrossRef VALUES ('old','tag')")
                db.execSQL("INSERT INTO MemoryCollection VALUES ('collection','合集','描述')")
                db.execSQL("INSERT INTO CollectionMemoryCrossRef VALUES ('collection','old')")
                db.execSQL(
                    "INSERT INTO Wish VALUES ('wish','心愿','','其他愿望',?,'',2,'备注','old',1,2,2)",
                    arrayOf(image),
                )
                db.execSQL(
                    "INSERT INTO Anniversary VALUES ('anniversary','纪念日','2020-02-29',1,1,'备注','old')"
                )
                db.version = 2
            }
    }

    @Test
    fun legacyMigrationEncryptsDatabaseOriginalsAndPreservesEveryRelationship(): Unit =
        runBlocking {
            val source = fixture()
            val media = File(context.filesDir, "media").apply { mkdirs() }
            source.copyTo(File(media, "old.img"))
            legacy("old.img")
            // Simulates an interrupted pre-commit export.
            context.getDatabasePath("memory-secure.next").writeText("incomplete")
            var db = MemoryDatabase.open(context)
            val repo = MemoryRepository(context, db)
            val snapshot = repo.snapshot()
            assertEquals("历史合成记录", snapshot.memories.single().title)
            assertEquals(1, snapshot.tagRefs.size)
            assertEquals(1, snapshot.profileRefs.size)
            assertEquals(1, snapshot.collectionRefs.size)
            assertEquals("old", snapshot.wishes.single().memoryId)
            assertEquals("old", snapshot.anniversaries.single().memoryId)
            assertEquals(
                Crypto.sha256(source),
                repo.images.withOriginal("old.img") { Crypto.sha256(it) },
            )
            assertFalse(
                repo
                    .file("old.img")
                    .readBytes()
                    .take(8)
                    .toByteArray()
                    .contentEquals(source.readBytes().take(8).toByteArray())
            )
            assertFalse(
                context
                    .getDatabasePath("memory-secure.db")
                    .inputStream()
                    .use { ByteArray(16).also { bytes -> it.read(bytes) } }
                    .toString(Charsets.US_ASCII)
                    .startsWith("SQLite format")
            )
            assertTrue(context.getDatabasePath("memory.db").exists())
            db.close()
            // Simulates process interruption after commit but before legacy cleanup.
            db = MemoryDatabase.open(context)
            assertEquals(snapshot, MemoryRepository(context, db).snapshot())
            LegacyMigration.finish(context)
            assertFalse(context.getDatabasePath("memory.db").exists())
            assertFalse(media.exists())
            db.close()
        }

    @Test
    fun failedMigrationKeepsLegacyDatabaseAndOriginalFiles(): Unit = runBlocking {
        val source = fixture()
        File(context.filesDir, "media").mkdirs()
        source.copyTo(File(context.filesDir, "media/preserved.img"))
        legacy("missing.img")
        assertThrows(Exception::class.java) { MemoryDatabase.open(context) }
        assertTrue(context.getDatabasePath("memory.db").exists())
        assertEquals(
            Crypto.sha256(source),
            Crypto.sha256(File(context.filesDir, "media/preserved.img")),
        )
    }

    @Test
    fun encryptedImagesRejectTamperingAndTrashRetainsSharedReferences(): Unit = runBlocking {
        val db = MemoryDatabase.open(context, "secure-test.db")
        try {
            val repo = MemoryRepository(context, db)
            val source = fixture()
            val path = repo.importImage(Uri.fromFile(source))
            repo.saveMemory(
                Memory(id = "one", title = "一", date = "2020-01-01"),
                listOf(path),
                emptyList(),
                emptySet(),
                emptySet(),
            )
            repo.saveMemory(
                Memory(id = "two", title = "二", date = "2020-01-02"),
                listOf(path),
                emptyList(),
                emptySet(),
                emptySet(),
            )
            repo.delete("memory", "one")
            assertEquals(1, repo.snapshot().activeMemories.size)
            repo.restoreMemory("one")
            assertEquals(2, repo.snapshot().activeMemories.size)
            repo.delete("memory", "one")
            repo.permanentDelete("one")
            assertTrue(repo.file(path).exists())
            assertTrue(repo.storageReport().unused.isEmpty())
            val data = repo.file(path).readBytes()
            data[data.lastIndex] = (data.last().toInt() xor 1).toByte()
            repo.file(path).writeBytes(data)
            assertThrows(Exception::class.java) { repo.images.temporary(path) }
            assertTrue(File(context.cacheDir, "sensitive").listFiles().orEmpty().isEmpty())
        } finally {
            db.close()
        }
    }
}

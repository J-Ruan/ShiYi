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
        context = isolated(base, root)
    }

    private fun isolated(base: Context, folder: File): Context =
        object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this

            override fun getFilesDir() = File(folder, "files").apply { mkdirs() }

            override fun getNoBackupFilesDir() = File(folder, "no-backup").apply { mkdirs() }

            override fun getCacheDir() = File(folder, "cache").apply { mkdirs() }

            override fun getDatabasePath(name: String) =
                File(folder, "databases/$name").apply { parentFile!!.mkdirs() }

            override fun deleteDatabase(name: String) =
                android.database.sqlite.SQLiteDatabase.deleteDatabase(getDatabasePath(name))
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

    @Test
    fun oldBackupRestoresWithNewDeviceKeysAndKeepsLocalSecurity(): Unit = runBlocking {
        val sourceDb = MemoryDatabase.open(context, "source.db")
        val destination = isolated(context, File(root, "new-device"))
        val destinationDb = MemoryDatabase.open(destination, "destination.db")
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = SettingsRepository(base)
        val originalSettings = prefs.read()
        try {
            val source = MemoryRepository(context, sourceDb)
            val original = fixture()
            val path = source.importImage(Uri.fromFile(original))
            source.saveMemory(
                Memory(id = "old-backup", title = "旧版备份", date = "2020-01-01"),
                listOf(path),
                listOf("标签"),
                emptySet(),
                emptySet(),
            )
            val manifest =
                com.shiyi.memory.platform.BackupManifest(
                    format = 1,
                    data = source.snapshot(),
                    settings = Settings(theme = "dark", pinHash = "old-device"),
                    checksums = mapOf(path to Crypto.sha256(original)),
                )
            val encoded =
                kotlinx.serialization.json.Json.encodeToString(
                    com.shiyi.memory.platform.BackupManifest.serializer(),
                    manifest,
                )
            val obj = Json.parseToJsonElement(encoded).jsonObject.toMutableMap()
            val data = obj.getValue("data").jsonObject.toMutableMap()
            data["memories"] =
                JsonArray(
                    data.getValue("memories").jsonArray.map {
                        JsonObject(it.jsonObject.filterKeys { key -> key != "deletedAt" })
                    }
                )
            obj["data"] = JsonObject(data)
            val zip = File(context.cacheDir, "legacy-backup.zip")
            java.util.zip.ZipOutputStream(zip.outputStream()).use { out ->
                out.putNextEntry(java.util.zip.ZipEntry("manifest.json"))
                out.write(JsonObject(obj).toString().toByteArray())
                out.closeEntry()
                out.putNextEntry(java.util.zip.ZipEntry("media/$path"))
                source.images.copyOriginal(path, out)
                out.closeEntry()
            }
            val backup = File(context.cacheDir, "legacy.shiyi")
            backup.outputStream().use { Crypto.encrypt(zip, it, "test-password".toCharArray()) }
            val target = MemoryRepository(destination, destinationDb)
            val local =
                Settings(
                    pinHash = "this-device",
                    biometric = true,
                    notifications = true,
                    lastBackup = 123,
                    backupLocation = "this-location",
                )
            prefs.update { local }
            val service = com.shiyi.memory.platform.BackupService(destination, target, prefs)
            service.restore(service.inspect(Uri.fromFile(backup), "test-password".toCharArray()))
            assertEquals(local.copy(theme = "dark"), prefs.read())
            assertEquals("旧版备份", target.snapshot().memories.single().title)
            assertEquals(
                Crypto.sha256(original),
                target.images.withOriginal(target.snapshot().media.single().path) {
                    Crypto.sha256(it)
                },
            )
            val first = LocalKeys.media(context)
            val second = LocalKeys.media(destination)
            try {
                assertFalse(first.contentEquals(second))
            } finally {
                first.fill(0)
                second.fill(0)
            }
        } finally {
            sourceDb.close()
            destinationDb.close()
            prefs.update { originalSettings }
        }
    }

    @Test
    fun interruptedShareRecoversCopiedImagesWithoutExternalUris(): Unit = runBlocking {
        val db = MemoryDatabase.open(context, "share-recovery.db")
        try {
            val repo = MemoryRepository(context, db)
            val image = repo.importImage(Uri.fromFile(fixture()))
            val folder = File(context.noBackupFilesDir, "share-drafts").apply { mkdirs() }
            File(folder, "interrupted.importing").writeText("[\"$image\"]")
            val service = com.shiyi.memory.platform.ShareImport(context, repo)
            assertTrue(service.recoverInterrupted())
            assertEquals("interrupted", service.pending())
            assertEquals(listOf(image), service.paths("interrupted"))
            assertTrue(repo.storageReport().unused.isEmpty())
            service.finish("interrupted")
            assertNull(service.pending())
        } finally {
            db.close()
        }
    }
}

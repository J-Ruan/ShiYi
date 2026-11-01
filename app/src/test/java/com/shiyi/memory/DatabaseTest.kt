package com.shiyi.memory

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.shiyi.memory.data.*
import com.shiyi.memory.platform.*
import java.io.File
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = MemoryApplication::class)
class DatabaseTest {
    private lateinit var context: Context
    private lateinit var db: MemoryDatabase
    private lateinit var repo: MemoryRepository
    private val name = "test.db"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(name)
        db = testDatabase(context, name)
        repo = testRepository(context, db)
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(name)
    }

    @Test
    fun memoryCrudTagsAndMediaOrder() = runBlocking {
        val m = Memory(id = "m", title = "初见", date = "2026-10-31")
        repo.images.write("a.img", "original".byteInputStream())
        repo.images.write("b.img", "original2".byteInputStream())
        repo.saveMemory(m, listOf("b.img", "a.img"), listOf("初见", "初见"), emptySet(), emptySet())
        var s = repo.snapshot()
        assertEquals(2, s.media.size)
        assertEquals("b.img", s.media[0].path)
        assertEquals(1, s.tags.size)
        assertEquals(1, s.tagRefs.size)
        repo.saveMemory(
            m.copy(title = "初见编辑"),
            listOf("a.img"),
            listOf("约会"),
            emptySet(),
            emptySet(),
        )
        s = repo.snapshot()
        assertEquals("初见编辑", s.memories.single().title)
        assertEquals("约会", s.tagsFor("m").single())
        repo.delete("memory", "m")
        repo.permanentDelete("m")
        s = repo.snapshot()
        assertTrue(s.memories.isEmpty())
        assertTrue(s.media.isEmpty())
        assertTrue(s.tagRefs.isEmpty())
    }

    @Test
    fun profileCrudAndBidirectionalRelation() = runBlocking {
        val m = Memory(id = "m", title = "聊天", date = "2020-01-01")
        db.dao().put(m)
        val p = ProfileEntry(id = "p", title = "喜欢草莓")
        repo.saveEntry(p, setOf("m"))
        assertEquals(listOf(ProfileMemoryCrossRef("p", "m")), repo.snapshot().profileRefs)
        repo.saveEntry(p.copy(content = "不要太甜"), setOf("m"))
        assertEquals("不要太甜", repo.snapshot().entries.single().content)
        repo.saveMemory(m, emptyList(), emptyList(), emptySet(), emptySet())
        assertTrue(repo.snapshot().profileRefs.isEmpty())
        assertEquals(1, repo.snapshot().entries.size)
        repo.saveEntry(p, setOf("m"))
        repo.delete("entry", "p")
        assertTrue(repo.snapshot().profileRefs.isEmpty())
        assertEquals(1, repo.snapshot().memories.size)
    }

    @Test
    fun wishStatusAndDeleteMemorySetNull() = runBlocking {
        db.dao().put(Memory(id = "m", title = "旅行", date = "2026-10-01"))
        val w = Wish(id = "w", title = "去海边", memoryId = "m")
        db.dao().put(w)
        db.dao().put(w.copy(status = 2, completedAt = 100))
        assertEquals(2, repo.snapshot().wishes.single().status)
        repo.delete("memory", "m")
        repo.permanentDelete("m")
        assertNull(repo.snapshot().wishes.single().memoryId)
        assertEquals(1, repo.snapshot().wishes.size)
    }

    @Test
    fun collectionDeletionDoesNotDeleteOriginal() = runBlocking {
        db.dao().put(Memory(id = "m", title = "旅行", date = "2026-10-01"))
        repo.saveCollection(MemoryCollection("c", "旅行"), setOf("m"))
        repo.delete("collection", "c")
        assertEquals(1, repo.snapshot().memories.size)
        assertTrue(repo.snapshot().collectionRefs.isEmpty())
    }

    @Test
    fun databaseSurvivesReopen() = runBlocking {
        db.dao().put(Memory(title = "不会丢失", date = "2026-10-31"))
        db.close()
        db = testDatabase(context, name)
        repo = testRepository(context, db)
        assertEquals("不会丢失", repo.snapshot().memories.single().title)
    }

    @Test
    fun invalidForeignKeyRollsBackEntireRestore() = runBlocking {
        db.dao().put(Memory(id = "original", title = "原数据", date = "2026-10-31"))
        try {
            repo.replace(
                Snapshot(
                    memories = listOf(Memory(id = "new", title = "新数据", date = "2026-10-31")),
                    profileRefs = listOf(ProfileMemoryCrossRef("missing", "new")),
                )
            )
            fail("must reject invalid FK")
        } catch (_: Exception) {}
        assertEquals("original", repo.snapshot().memories.single().id)
    }

    @Test
    fun encryptedBackupRestoresJsonImagesAndLinks() = runBlocking {
        val prefs = SettingsRepository(context)
        prefs.update { it.copy(theme = "dark") }
        repo.images.write("fixture.img", byteArrayOf(1, 2, 3, 4, 5).inputStream())
        val media = repo.file("fixture.img")
        db.dao().put(Memory(id = "m", title = "草莓", date = "2020-01-01", coverPath = media.name))
        db.dao().put(MemoryMedia(memoryId = "m", path = media.name, position = 0))
        repo.saveEntry(ProfileEntry(id = "p", title = "喜欢草莓"), setOf("m"))
        db.dao().put(PartnerProfile(avatar = media.name))
        db.dao().put(Wish(title = "蛋糕", image = media.name, memoryId = "m"))
        val backup = BackupService(context, repo, prefs)
        val file = File(context.cacheDir, "backup.shiyi")
        backup.export(Uri.fromFile(file), "test-password".toCharArray())
        repo.replace(Snapshot())
        prefs.update { it.copy(theme = "light") }
        val prepared = backup.inspect(Uri.fromFile(file), "test-password".toCharArray())
        assertEquals(1, prepared.manifest.data.memories.size)
        backup.restore(prepared)
        val restored = repo.snapshot()
        assertEquals("草莓", restored.memories.single().title)
        assertEquals(1, restored.profileRefs.size)
        val newPath = restored.media.single().path
        assertArrayEquals(
            byteArrayOf(1, 2, 3, 4, 5),
            repo.images.withOriginal(newPath) { it.readBytes() },
        )
        assertEquals(newPath, restored.partner.single().avatar)
        assertEquals(newPath, restored.wishes.single().image)
        assertEquals("dark", prefs.read().theme)
    }

    @Test
    fun wrongBackupPasswordPreservesExistingData() = runBlocking {
        val prefs = SettingsRepository(context)
        db.dao().put(Memory(title = "现有数据", date = "2026-10-31"))
        val backup = BackupService(context, repo, prefs)
        val file = File(context.cacheDir, "wrong.shiyi")
        backup.export(Uri.fromFile(file), "password-1".toCharArray())
        try {
            backup.inspect(Uri.fromFile(file), "password-2".toCharArray())
            fail("must fail")
        } catch (_: java.io.IOException) {}
        assertEquals("现有数据", repo.snapshot().memories.single().title)
    }

    @Test
    fun missingImageBlocksBackup() = runBlocking {
        val prefs = SettingsRepository(context)
        db.dao().put(Memory(id = "m", title = "丢失图片", date = "2026-10-31"))
        db.dao().put(MemoryMedia(memoryId = "m", path = "not-found.img", position = 0))
        try {
            BackupService(context, repo, prefs)
                .export(
                    Uri.fromFile(File(context.cacheDir, "missing.shiyi")),
                    "password-1".toCharArray(),
                )
            fail("must not export incomplete backup")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("原图缺失"))
        }
    }

    @Test
    fun cancellingRestoreDoesNotRemoveCommittedMedia(): Unit = runBlocking {
        val prefs = SettingsRepository(context)
        repo.images.write("cancel-fixture.img", byteArrayOf(1, 2, 3).inputStream())
        db.dao()
            .put(
                Memory(
                    id = "backup",
                    title = "备份内容",
                    date = "2026-10-31",
                    coverPath = "cancel-fixture.img",
                )
            )
        db.dao().put(MemoryMedia(memoryId = "backup", path = "cancel-fixture.img", position = 0))
        val service = BackupService(context, repo, prefs)
        val archive = File(context.cacheDir, "cancel-test.shiyi")
        service.export(Uri.fromFile(archive), "test-password".toCharArray())
        val prepared = service.inspect(Uri.fromFile(archive), "test-password".toCharArray())
        repo.replace(
            Snapshot(
                memories = listOf(Memory(id = "current", title = "恢复前内容", date = "2026-10-31"))
            )
        )
        repo.mutex.lock()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { service.restore(prepared) }
        job.cancel()
        repo.mutex.unlock()
        job.join()
        val restored = repo.snapshot()
        assertEquals("备份内容", restored.memories.single().title)
        assertTrue(restored.images().all { repo.file(it).isFile })
        assertArrayEquals(
            byteArrayOf(1, 2, 3),
            repo.images.withOriginal(restored.media.single().path) { it.readBytes() },
        )
    }

    @Test
    fun trashRestoreAndPermanentDeleteKeepSharedImages(): Unit = runBlocking {
        repo.images.write("shared.img", byteArrayOf(1, 2, 3).inputStream())
        val first = Memory(id = "a", title = "一", date = "2020-01-01")
        repo.saveMemory(first, listOf("shared.img"), listOf("标签"), emptySet(), emptySet())
        repo.saveMemory(
            first.copy(id = "b"),
            listOf("shared.img"),
            emptyList(),
            emptySet(),
            emptySet(),
        )
        repo.delete("memory", "a")
        assertEquals(1, repo.snapshot().activeMemories.size)
        assertEquals(2, repo.snapshot().media.size)
        repo.restoreMemory("a")
        assertEquals(2, repo.snapshot().activeMemories.size)
        repo.delete("memory", "a")
        repo.permanentDelete("a")
        assertEquals(1, repo.snapshot().media.size)
        assertTrue(repo.file("shared.img").exists())
        assertTrue(repo.storageReport().unused.isEmpty())
    }

    @Test
    fun backupIncludesTrashAndPreservesDeviceLocalSettings(): Unit = runBlocking {
        val prefs = SettingsRepository(context)
        prefs.update {
            Settings(theme = "dark", pinHash = "old", lastBackup = 4, backupLocation = "old-device")
        }
        repo.saveMemory(
            Memory(id = "trash", title = "回收站", date = "2020-01-01"),
            emptyList(),
            emptyList(),
            emptySet(),
            emptySet(),
        )
        repo.delete("memory", "trash")
        val service = BackupService(context, repo, prefs)
        val file = File(context.cacheDir, "trash.shiyi")
        service.export(Uri.fromFile(file), "test-password".toCharArray())
        val prepared = service.inspect(Uri.fromFile(file), "test-password".toCharArray())
        assertEquals("", prepared.manifest.settings.pinHash)
        assertEquals(0L, prepared.manifest.settings.lastBackup)
        val local =
            Settings(
                theme = "light",
                pinHash = "new-device",
                biometric = true,
                lastBackup = 99,
                backupLocation = "current-device",
            )
        prefs.update { local }
        repo.replace(Snapshot())
        service.restore(prepared)
        assertNotNull(repo.snapshot().memories.single().deletedAt)
        assertEquals(local.copy(theme = "dark"), prefs.read())
    }

    @Test
    fun explicitCleanupRechecksReferences(): Unit = runBlocking {
        repo.images.write("orphan.img", byteArrayOf(3, 2, 1).inputStream())
        repo.file("orphan.img").setLastModified(System.currentTimeMillis() - 2 * 86400000)
        val review = repo.storageReport().unused
        assertEquals(1, review.size)
        repo.saveMemory(
            Memory(title = "新引用", date = "2020-01-01"),
            listOf("orphan.img"),
            emptyList(),
            emptySet(),
            emptySet(),
        )
        try {
            repo.cleanReviewed(review)
            fail("must recheck references")
        } catch (_: IllegalArgumentException) {}
        assertTrue(repo.file("orphan.img").exists())
    }

    @Test
    fun largeDatasetIndexedWindowAndRelations(): Unit = runBlocking {
        val rows =
            (1..3000).map {
                Memory(
                    id = "bulk-$it",
                    title = "合成记录 $it",
                    date = "2020-01-${(it%28+1).toString().padStart(2,'0')}",
                )
            }
        repo.replace(Snapshot(memories = rows))
        val window = db.dao().memoryPage("2020-01-10", "2020-01-12", 50, 50)
        assertEquals(50, window.size)
        assertTrue(window.all { it.date in "2020-01-10".."2020-01-12" })
        assertEquals(3000, repo.snapshot().memories.size)
    }
}

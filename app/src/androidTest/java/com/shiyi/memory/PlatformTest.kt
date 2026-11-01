package com.shiyi.memory

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shiyi.memory.data.*
import com.shiyi.memory.domain.Crypto
import com.shiyi.memory.platform.*
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlatformTest {
    private lateinit var context: Context
    private lateinit var db: MemoryDatabase
    private lateinit var repo: MemoryRepository
    private var originalSettings: Settings? = null

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        TestDeviceGuard.requireEmptyInstallation()
        originalSettings = runBlocking {
            (context.applicationContext as MemoryApplication).settings.read()
        }
        context.deleteDatabase("platform-test.db")
        db = MemoryDatabase.open(context, "platform-test.db")
        repo =
            MemoryRepository(
                context,
                db,
                ImageStorage(context, File(context.filesDir, "platform-test-images")),
            )
    }

    @After
    fun close() {
        if (::db.isInitialized) {
            repo.mediaDir.deleteRecursively()
            db.close()
            context.deleteDatabase("platform-test.db")
        }
        originalSettings?.let { saved ->
            runBlocking {
                (context.applicationContext as MemoryApplication).settings.update { saved }
            }
        }
    }

    private fun fixture(): File {
        val f = File(context.cacheDir, "generated-fixture.png")
        val b = Bitmap.createBitmap(320, 120, Bitmap.Config.ARGB_8888)
        b.eraseColor(android.graphics.Color.rgb(185, 174, 217))
        f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        b.recycle()
        return f
    }

    @Test
    fun originalImagesImportedWithoutCompression(): Unit = runBlocking {
        val source = fixture()
        val paths = (1..3).map { repo.importImage(Uri.fromFile(source)) }
        assertEquals(3, paths.distinct().size)
        val shareUri =
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.files",
                repo.images.temporary(paths.first(), true),
            )
        assertEquals("image/png", context.contentResolver.getType(shareUri))
        paths.forEach {
            assertEquals(
                Crypto.sha256(source),
                repo.images.withOriginal(it) { f -> Crypto.sha256(f) },
            )
        }
        repo.saveMemory(
            Memory(id = "m", title = "多图聊天", type = "聊天收藏", date = "2026-10-31"),
            paths,
            listOf("截图"),
            emptySet(),
            emptySet(),
        )
        assertEquals(paths, repo.snapshot().media.map { it.path })
        paths.forEach { repo.file(it).delete() }
    }

    @Test
    fun pdfHasChineseLongTextImagesAndMultiplePages(): Unit = runBlocking {
        val image = repo.importImage(Uri.fromFile(fixture()))
        val m =
            Memory(
                id = "pdf",
                title = "中文标题 · 时光的收藏",
                date = "2026-10-31",
                description = "这是手动记录的中文回忆，正文不应被截断。\n".repeat(250),
            )
        repo.saveMemory(m, listOf(image), emptyList(), emptySet(), emptySet())
        val f = File(context.cacheDir, "platform-test.pdf")
        PdfExporter(context, repo)
            .export(Uri.fromFile(f), listOf(m), repo.snapshot(), PdfOptions(cover = image))
        assertTrue(f.length() > 1000)
        PdfRenderer(ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)).use {
            renderer ->
            assertTrue(renderer.pageCount > 3)
            renderer.openPage(0).use { page ->
                val b = Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888)
                page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                assertNotEquals(0, b.getPixel(100, 150))
                b.recycle()
            }
        }
        repo.file(image).delete()
        f.delete()
    }

    @Test
    fun backupRejectsCorruptionBeforeDataChanges(): Unit = runBlocking {
        db.dao().put(Memory(title = "保持原样", date = "2026-10-31"))
        val prefs = SettingsRepository(context)
        val backup = BackupService(context, repo, prefs)
        val f = File(context.cacheDir, "corrupt.shiyi")
        backup.export(Uri.fromFile(f), "test-password".toCharArray())
        val bytes = f.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        f.writeBytes(bytes)
        try {
            backup.inspect(Uri.fromFile(f), "test-password".toCharArray())
            fail("must reject corrupt backup")
        } catch (_: java.io.IOException) {}
        assertEquals("保持原样", repo.snapshot().memories.single().title)
        f.delete()
    }

    @Test
    fun manifestHasNoInternetOrBroadPhotoPermission() {
        @Suppress("DEPRECATION")
        val info =
            context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_PERMISSIONS,
            )
        val permissions = info.requestedPermissions.orEmpty()
        assertFalse("android.permission.INTERNET" in permissions)
        assertFalse("android.permission.READ_MEDIA_IMAGES" in permissions)
        assertFalse("android.permission.READ_EXTERNAL_STORAGE" in permissions)
        assertFalse(
            context.applicationInfo.flags and
                android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP != 0
        )
    }

    @Test
    fun localReminderUsesGenericNotificationContent(): Unit = runBlocking {
        val app = context.applicationContext as MemoryApplication
        val prefs = app.settings.read()
        val event =
            Anniversary(
                title = "不应出现在通知中的私人名称",
                date = java.time.LocalDate.now().toString(),
                annual = false,
            )
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        try {
            app.repository.dao.put(event)
            app.settings.update { it.copy(notifications = true) }
            Reminders.schedule(context)
            Reminders.notifyIfDue(app)
            val notification = manager.activeNotifications.first { it.id == 1 }.notification
            val title =
                notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString()
            val text =
                notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString()
            assertEquals("拾忆 · 温柔提醒", title)
            assertFalse(text.contains(event.title))
            assertFalse(title.contains(event.title))
        } finally {
            manager.cancel(1)
            Reminders.cancel(context)
            app.repository.dao.deleteAnniversary(event.id)
            app.settings.update { prefs }
        }
    }

    @Test
    fun longChatScreenshotIsSplitAcrossReadablePages(): Unit = runBlocking {
        val input = File(context.cacheDir, "tall-generated.png")
        val bitmap = Bitmap.createBitmap(320, 3000, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint()
        listOf(
                android.graphics.Color.RED,
                android.graphics.Color.GREEN,
                android.graphics.Color.BLUE,
            )
            .forEachIndexed { index, color ->
                paint.color = color
                canvas.drawRect(0f, index * 1000f, 320f, (index + 1) * 1000f, paint)
            }
        input.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val image = repo.importImage(Uri.fromFile(input))
        val memory = Memory(id = "tall", type = "聊天收藏", title = "长截图分页", date = "2026-10-31")
        repo.saveMemory(memory, listOf(image), emptyList(), emptySet(), emptySet())
        val output = File(context.cacheDir, "tall-output.pdf")
        try {
            PdfExporter(context, repo)
                .export(Uri.fromFile(output), listOf(memory), repo.snapshot(), PdfOptions())
            PdfRenderer(ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY))
                .use { renderer ->
                    assertTrue(
                        "Long screenshot must span several full-width pages; pages=${renderer.pageCount}, encrypted media",
                        renderer.pageCount >= 5,
                    )
                    fun colorAtCenter(pageIndex: Int): Int {
                        val rendered = Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888)
                        return try {
                            renderer.openPage(pageIndex).use { page ->
                                page.render(
                                    rendered,
                                    null,
                                    null,
                                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                                )
                            }
                            rendered.getPixel(300, 300)
                        } finally {
                            rendered.recycle()
                        }
                    }
                    val colors = (0 until renderer.pageCount).map { colorAtCenter(it) }
                    val first =
                        colors.firstOrNull {
                            android.graphics.Color.red(it) > 200 &&
                                android.graphics.Color.blue(it) < 50
                        } ?: 0
                    val last = colors.last()
                    assertTrue(
                        "First screenshot region must be visible",
                        android.graphics.Color.red(first) > 200 &&
                            android.graphics.Color.blue(first) < 50,
                    )
                    assertTrue(
                        "Final screenshot region must not be truncated",
                        android.graphics.Color.blue(last) > 200 &&
                            android.graphics.Color.red(last) < 50,
                    )
                }
        } finally {
            input.delete()
            output.delete()
            repo.file(image).delete()
        }
    }

    @Test
    fun eraseRemovesDatabaseMediaSettingsAndWal(): Unit = runBlocking {
        val prefs = SettingsRepository(context)
        val secret = "用于删除验证的合成内容-erase-marker"
        val path = repo.importImage(Uri.fromFile(fixture()))
        repo.saveMemory(
            Memory(id = "erase", title = secret, date = "2026-10-31"),
            listOf(path),
            listOf("删除验证"),
            emptySet(),
            emptySet(),
        )
        db.dao().put(PartnerProfile(nickname = secret, avatar = path))
        repo.saveEntry(ProfileEntry(id = "entry", title = secret), setOf("erase"))
        db.dao().put(Wish(title = secret, image = path, memoryId = "erase"))
        db.dao().put(Anniversary(title = secret, date = "2026-10-31", memoryId = "erase"))
        repo.saveCollection(MemoryCollection("collection", secret), setOf("erase"))
        prefs.update { it.copy(theme = "dark", pinSalt = "test-salt", pinHash = "test-hash") }
        repo.eraseAll(prefs)
        val empty = repo.snapshot()
        assertTrue(
            empty.memories.isEmpty() &&
                empty.partner.isEmpty() &&
                empty.media.isEmpty() &&
                empty.entries.isEmpty() &&
                empty.wishes.isEmpty() &&
                empty.anniversaries.isEmpty() &&
                empty.collections.isEmpty() &&
                empty.tags.isEmpty() &&
                empty.profileRefs.isEmpty() &&
                empty.collectionRefs.isEmpty() &&
                empty.tagRefs.isEmpty()
        )
        assertFalse(repo.file(path).exists())
        assertTrue(prefs.read().pinHash.isEmpty())
        assertEquals("system", prefs.read().theme)
        val file = context.getDatabasePath("platform-test.db")
        assertFalse(file.readBytes().toString(Charsets.UTF_8).contains(secret))
        val wal = File(file.path + "-wal")
        if (wal.exists()) assertEquals(0L, wal.length())
    }

    @Test
    fun instrumentationRefusesExistingPersonalRecords(): Unit = runBlocking {
        val app = context.applicationContext as MemoryApplication
        val record = Memory(title = "用于验证测试保护的合成记录", date = "2026-10-31")
        try {
            app.repository.dao.put(record)
            assertThrows(IllegalArgumentException::class.java) {
                TestDeviceGuard.requireEmptyInstallation()
            }
            assertEquals(
                record.title,
                app.repository.snapshot().memories.single { it.id == record.id }.title,
            )
        } finally {
            app.repository.delete("memory", record.id)
            app.repository.permanentDelete(record.id)
        }
    }
}

package com.shiyi.memory

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shiyi.memory.data.*
import com.shiyi.memory.ui.*
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisualTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun syntheticMajorPagesLightAndDark() {
        TestDeviceGuard.requireEmptyInstallation()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as MemoryApplication
        val id = "visual-memory"
        val paths = runBlocking {
            (0..1)
                .map { i ->
                    val f = File(app.cacheDir, "visual-source-$i.png")
                    val b = Bitmap.createBitmap(720, 480, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(b)
                    canvas.drawColor(if (i == 0) 0xFFE7D8CC.toInt() else 0xFFD9D2EA.toInt())
                    val paint =
                        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFF9A697E.toInt()
                        }
                    canvas.drawCircle(360f, 230f, 120f, paint)
                    f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    b.recycle()
                    app.repository.importImage(android.net.Uri.fromFile(f)).also { f.delete() }
                }
                .also {
                    app.repository.saveMemory(
                        Memory(
                            id = id,
                            title = "周末散步",
                            description = "走过安静的街道，记下今天的阳光。",
                            date = java.time.LocalDate.now().toString(),
                        ),
                        it,
                        listOf("日常"),
                        emptySet(),
                        emptySet(),
                    )
                    app.repository.updateProfile(
                        PartnerProfile(
                            nickname = "小忆",
                            myNickname = "拾光",
                            bio = "喜欢散步和晴天",
                            metDate = "2020-01-01",
                            avatar = it.first(),
                        )
                    )
                    app.repository.saveEntry(
                        ProfileEntry(
                            id = "visual-entry",
                            title = "喜欢热可可",
                            content = "少糖",
                            importance = 1,
                        ),
                        setOf(id),
                    )
                    app.repository.updateWish(Wish(title = "看一场日落", image = it.last()))
                    app.repository.updateAnniversary(
                        Anniversary(title = "相识纪念日", date = "2020-11-01")
                    )
                    app.repository.saveCollection(
                        MemoryCollection("visual-collection", "日常片段"),
                        setOf(id),
                    )
                }
        }
        val vm = AppViewModel(app)
        val store = androidx.lifecycle.ViewModelStore().apply { put("visual", vm) }
        var screen by mutableStateOf("home")
        var theme by mutableStateOf("light")
        val folder = File(app.filesDir, "screenshots-110").apply { mkdirs() }
        try {
            compose.setContent {
                ShiYiTheme(theme) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        key(screen) {
                            when (screen) {
                                "home",
                                "dark" -> HomeScreen(vm, {})
                                "memories" -> MemoriesScreen(vm, {})
                                "detail" -> DetailScreen(vm, "memory", id, {}, {})
                                "editor",
                                "dark-editor" -> EditorScreen(vm, "memory", "new", "", {})
                                "profile" -> ProfileScreen(vm, {})
                                "calendar" -> CalendarScreen(vm, {}, {})
                                "collections" -> CollectionsScreen(vm, {}, {})
                                "statistics" -> StatsScreen(vm, {})
                                "settings" -> SettingsScreen(vm, {})
                                "backup" -> BackupScreen(vm, {})
                                "pdf" -> PdfScreen(vm, {})
                                "wishes" -> WishesScreen(vm, {}, {})
                                "anniversaries" -> AnniversariesScreen(vm, {}, {})
                                "search" -> SearchScreen(vm, {}, {})
                                "storage" -> StorageScreen(vm, {})
                                else -> TrashScreen(vm, {})
                            }
                        }
                    }
                }
            }
            compose.waitUntil(120000) { !vm.loading.value }
            for (page in
                listOf(
                    "home",
                    "memories",
                    "detail",
                    "editor",
                    "profile",
                    "calendar",
                    "collections",
                    "statistics",
                    "settings",
                    "backup",
                    "pdf",
                    "wishes",
                    "anniversaries",
                    "search",
                    "storage",
                    "trash",
                    "dark",
                    "dark-editor",
                )) {
                compose.runOnIdle {
                    screen = page
                    theme = if (page.startsWith("dark")) "dark" else "light"
                }
                compose.waitForIdle()
                val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
                val file = File(folder, "$page.png")
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let {
                    dir ->
                    val output = File(dir, "screenshots-110").apply { mkdirs() }
                    file.copyTo(File(output, file.name), true)
                }
            }
        } finally {
            runBlocking {
                app.repository.replace(Snapshot())
                app.settings.update { Settings() }
            }
            paths.forEach { app.repository.file(it).delete() }
            store.clear()
        }
    }
}

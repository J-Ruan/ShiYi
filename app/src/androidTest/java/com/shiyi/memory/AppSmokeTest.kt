package com.shiyi.memory

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    val compose = createAndroidComposeRule<MainActivity>()
    private val emptyDeviceRule = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                // Validate before launching the Activity, not concurrently with its first frame.
                TestDeviceGuard.requireEmptyInstallation()
                base.evaluate()
            }
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(emptyDeviceRule).around(compose)

    @Test
    fun fourTabsAndCreateReadUpdateDeleteMemory() {
        compose.waitUntil(120_000) {
            compose.onAllNodesWithContentDescription("添加").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("添加").performClick()
        compose.onNodeWithText("添加回忆").performClick()
        compose.onNodeWithText("标题 *").performTextInput("自动化测试的回忆")
        compose.onNodeWithText("回忆描述").performTextInput("草莓蛋糕，不要太甜")
        compose.onNodeWithText("保存", useUnmergedTree = true).performClick()
        compose.waitUntil(120_000) {
            compose.onAllNodesWithText("自动化测试的回忆").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("自动化测试的回忆").performClick()
        compose.onNodeWithText("草莓蛋糕，不要太甜").assertExists()
        compose.onNodeWithContentDescription("编辑").performClick()
        compose.onNodeWithText("标题 *").performTextReplacement("已编辑的回忆")
        compose.onNodeWithText("保存", useUnmergedTree = true).performClick()
        compose.waitUntil(120_000) {
            compose.onAllNodesWithText("已编辑的回忆").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("删除").performClick()
        compose.onNodeWithText("确认删除").performClick()
        compose.waitUntil(120_000) {
            compose.onAllNodesWithContentDescription("添加").fetchSemanticsNodes().isNotEmpty()
        }
        listOf("回忆", "TA 的档案", "我的", "首页").forEach { tab ->
            compose.onAllNodesWithText(tab).onLast().performClick()
            compose.waitForIdle()
        }
    }

    @Test
    fun screenshotsNavigationDarkModeAndRotation() {
        compose.waitUntil(120_000) {
            compose.onAllNodesWithContentDescription("添加").fetchSemanticsNodes().isNotEmpty()
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.filesDir, "screenshots")
        folder.mkdirs()
        // Only this test disables screenshot protection, for empty/synthetic test data.
        compose.runOnUiThread {
            compose.activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        fun capture(name: String) {
            compose.waitForIdle()
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            File(folder, "$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let {
                output ->
                val extra = File(output, "screenshots").apply { mkdirs() }
                File(folder, "$name.png").copyTo(File(extra, "$name.png"), overwrite = true)
            }
        }
        capture("home")
        listOf("回忆" to "memories", "TA 的档案" to "profile", "我的" to "me").forEach { (tab, file) ->
            compose.onAllNodesWithText(tab).onLast().performClick()
            capture(file)
        }
        val app = context.applicationContext as MemoryApplication
        runBlocking { app.settings.update { it.copy(theme = "dark") } }
        compose.waitForIdle()
        capture("dark")
        runBlocking { app.settings.update { it.copy(theme = "system") } }
        compose.onAllNodesWithText("首页").onLast().performClick()
        compose.onNodeWithContentDescription("添加").performClick()
        compose.onNodeWithText("添加回忆").performClick()
        compose.onNodeWithText("标题 *").performTextInput("旋转后保留的草稿")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(120_000) {
            compose.onAllNodesWithText("旋转后保留的草稿").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("旋转后保留的草稿").assertExists()
        compose.onNodeWithContentDescription("返回").performClick()
    }

    @Test
    fun appLockRequiresPinAfterBackgroundAndRejectsWrongPin() {
        compose.waitUntil(120_000) {
            compose.onAllNodesWithContentDescription("添加").fetchSemanticsNodes().isNotEmpty()
        }
        val app = compose.activity.application as MemoryApplication
        val original = runBlocking { app.settings.read() }
        val salt = com.shiyi.memory.domain.Crypto.salt()
        val hash = com.shiyi.memory.domain.Crypto.hashPin("123456".toCharArray(), salt)
        val vm =
            androidx.lifecycle
                .ViewModelProvider(compose.activity)[com.shiyi.memory.ui.AppViewModel::class.java]
        try {
            compose.runOnUiThread { vm.unlocked.value = true }
            runBlocking {
                app.settings.update { it.copy(pinSalt = salt, pinHash = hash, biometric = false) }
            }
            compose.waitForIdle()
            compose.onNodeWithContentDescription("添加").performClick()
            compose.onNodeWithText("留下一点，关于我们").assertExists()
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            compose.waitUntil(120_000) {
                compose.onAllNodesWithText("数字密码").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("数字密码").performTextInput("000000")
            compose.onNodeWithText("解锁").performClick()
            compose.waitUntil(120_000) {
                compose.onAllNodesWithText("密码不正确").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("知道了").performClick()
            compose.onNodeWithText("数字密码").performTextInput("123456")
            compose.onNodeWithText("解锁").performClick()
            compose.waitUntil(120_000) {
                compose.onAllNodesWithContentDescription("添加").fetchSemanticsNodes().isNotEmpty()
            }
        } finally {
            runBlocking { app.settings.update { original } }
            compose.runOnUiThread {
                vm.unlocked.value = true
                vm.error.value = null
            }
        }
    }

    @Test
    fun multipleOriginalImagesDisplayAndSwipe() {
        compose.waitUntil(120_000) {
            compose.onAllNodesWithContentDescription("添加").fetchSemanticsNodes().isNotEmpty()
        }
        val app = compose.activity.application as MemoryApplication
        val memory = com.shiyi.memory.data.Memory(title = "多图浏览自动测试", date = "2026-10-31")
        val paths = runBlocking {
            (1..3)
                .map { i ->
                    val source = File(app.cacheDir, "ui-fixture-$i.png")
                    val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.rgb(100 + i * 20, 120, 180))
                    source.outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                    app.repository.importImage(android.net.Uri.fromFile(source))
                }
                .also { app.repository.saveMemory(memory, it, emptyList(), emptySet(), emptySet()) }
        }
        try {
            compose.waitUntil(120_000) {
                compose.onAllNodesWithText(memory.title).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(memory.title).performClick()
            compose.onAllNodesWithContentDescription("收藏的原始图片").onFirst().performClick()
            compose.onNodeWithText("1 / 3").assertExists()
            compose.onRoot().performTouchInput { swipeLeft() }
            compose.waitUntil(120_000) {
                compose.onAllNodesWithText("2 / 3").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("2 / 3").assertExists()
        } finally {
            runBlocking { app.repository.delete("memory", memory.id) }
            paths.forEach { app.repository.file(it).delete() }
        }
    }
}

package com.shiyi.memory

import androidx.test.platform.app.InstrumentationRegistry
import com.shiyi.memory.data.Settings
import com.shiyi.memory.data.Snapshot
import kotlinx.coroutines.runBlocking

/** Instrumentation can exercise erasure: refuse to touch a personal installation. */
object TestDeviceGuard {
    fun requireEmptyInstallation() = runBlocking {
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as MemoryApplication
        require(app.repository.snapshot() == Snapshot()) { "设备已有资料；请仅在空数据模拟器或专用测试设备运行测试" }
        require(app.repository.mediaDir.listFiles().orEmpty().isEmpty()) { "设备已有原图或待保存图片，拒绝运行设备测试" }
        require(app.settings.read() == Settings()) { "设备已有个人设置，拒绝运行设备测试；请使用专用测试设备" }
    }
}

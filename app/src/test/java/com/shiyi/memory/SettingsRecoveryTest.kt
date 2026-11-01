package com.shiyi.memory

import android.app.Application
import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.test.core.app.ApplicationProvider
import com.shiyi.memory.data.Memory
import com.shiyi.memory.data.Settings
import com.shiyi.memory.data.SettingsRepository
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// A separate SDK sandbox models a fresh process without an already-loaded DataStore singleton.
@Config(sdk = [29], application = Application::class)
class SettingsRecoveryTest {
    @Test
    fun corruptedSettingsRequireExplicitResetAndPreserveMemories(): Unit = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val file =
            File(context.filesDir, "datastore/settings.preferences_pb").apply {
                parentFile!!.mkdirs()
            }
        file.writeBytes(byteArrayOf(-1, -1, -1, 0))
        val database = testDatabase(context, "settings-recovery-fixture.db")
        try {
            val repository = testRepository(context, database)
            repository.saveMemory(
                Memory(id = "preserve", title = "设置损坏也应保留的合成回忆", date = "2020-01-01"),
                emptyList(),
                emptyList(),
                emptySet(),
                emptySet(),
            )
            val prefs = SettingsRepository(context)
            try {
                prefs.read()
                fail("corruption must not silently disable the lock")
            } catch (_: CorruptionException) {}
            assertEquals("preserve", repository.snapshot().memories.single().id)
            prefs.resetDamagedSettings()
            assertEquals(Settings(), prefs.read())
            assertEquals("preserve", repository.snapshot().memories.single().id)
        } finally {
            database.close()
            context.deleteDatabase("settings-recovery-fixture.db")
        }
    }
}

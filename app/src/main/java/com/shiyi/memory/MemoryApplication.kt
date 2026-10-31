package com.shiyi.memory

import android.app.Application
import com.shiyi.memory.data.*
import kotlinx.coroutines.*

class MemoryApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database by lazy { MemoryDatabase.open(this) }
    val repository by lazy { MemoryRepository(this, database) }
    val settings by lazy { SettingsRepository(this) }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            cacheDir
                .listFiles()
                ?.filter {
                    it.name.startsWith("restore-") ||
                        it.name.startsWith("backup-") ||
                        it.name.startsWith("memory-book-")
                }
                ?.forEach { it.deleteRecursively() }
            if (settings.read().notifications)
                com.shiyi.memory.platform.Reminders.schedule(this@MemoryApplication)
        }
    }
}

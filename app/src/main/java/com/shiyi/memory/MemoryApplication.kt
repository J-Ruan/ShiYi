package com.shiyi.memory

import android.app.Application
import com.shiyi.memory.data.*
import kotlinx.coroutines.*

class MemoryApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database by lazy { MemoryDatabase.open(this) }
    val repository by lazy { MemoryRepository(this, database) }
    val imageLoader by lazy {
        coil.ImageLoader.Builder(this)
            .components {
                add(com.shiyi.memory.platform.EncryptedImageFetcher.Factory(repository.images))
            }
            .diskCache(null)
            .memoryCache(null)
            .build()
    }
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
            FileCleanup.clear(this@MemoryApplication)
            if (runCatching { settings.read().notifications }.getOrDefault(false))
                com.shiyi.memory.platform.Reminders.schedule(this@MemoryApplication)
        }
    }
}

object FileCleanup {
    fun clear(context: android.content.Context, includeExports: Boolean = true) {
        (if (includeExports) listOf("sensitive", "exports") else listOf("sensitive")).forEach {
            java.io.File(context.cacheDir, it).deleteRecursively()
        }
    }
}

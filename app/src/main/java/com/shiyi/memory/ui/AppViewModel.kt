package com.shiyi.memory.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shiyi.memory.MemoryApplication
import com.shiyi.memory.data.*
import com.shiyi.memory.domain.Crypto
import com.shiyi.memory.platform.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class AppViewModel(application: Application) : AndroidViewModel(application) {
    val app = application as MemoryApplication
    val repo by lazy { app.repository }
    val prefs = app.settings
    val data = MutableStateFlow(Snapshot())
    val settings = MutableStateFlow(Settings())
    val settingsReady = MutableStateFlow(false)
    val loading = MutableStateFlow(true)
    val busy = MutableStateFlow(false)
    val unlocked = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val backup by lazy { BackupService(app, repo, prefs) }
    val prepared = MutableStateFlow<PreparedBackup?>(null)

    val startupError = MutableStateFlow<String?>(null)
    val settingsError = MutableStateFlow(false)
    val sharedDraft = MutableStateFlow<String?>(null)
    private var isInBackground = false

    fun foreground() {
        isInBackground = false
    }

    private var sharingUntil = 0L

    fun beginSharing() {
        sharingUntil = android.os.SystemClock.elapsedRealtime() + 600_000
        viewModelScope.launch {
            delay(600_000)
            if (android.os.SystemClock.elapsedRealtime() >= sharingUntil)
                java.io.File(app.cacheDir, "exports").deleteRecursively()
        }
    }

    private var externalUntil = 0L
    private var externalWasUnlocked = false

    fun beginExternal() {
        externalWasUnlocked = unlocked.value
        externalUntil = android.os.SystemClock.elapsedRealtime() + 60_000
    }

    fun endExternal() {
        if (
            android.os.SystemClock.elapsedRealtime() < externalUntil &&
                externalWasUnlocked &&
                !(app.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked)
        )
            unlocked.value = true
        externalUntil = 0
        externalWasUnlocked = false
    }

    fun background() {
        isInBackground = true
        unlocked.value = false
        if (externalUntil == 0L) lock()
    }

    val undoDeletion = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private var recoveredDraft = false
    private var databaseJob: Job? = null
    private var settingsJob: Job? = null

    init {
        loadSettings()
        loadDatabase()
    }

    private fun loadDatabase() {
        databaseJob?.cancel()
        databaseJob =
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    repo.flow.collect {
                        com.shiyi.memory.domain.validateSnapshot(it)
                        if (loading.value) LegacyMigration.finish(app)
                        data.value = it
                        startupError.value = null
                        if (!recoveredDraft) {
                            recoveredDraft = true
                            val imports = ShareImport(app, repo)
                            if (imports.recoverInterrupted()) {
                                error.value = "上次图片导入被中断，已保留复制完成的图片。请重新分享其余图片。"
                            }
                            sharedDraft.value = imports.pending()
                            repo.emptyTrash(true)
                        }
                        loading.value = false
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    startupError.value = "无法读取或迁移回忆。旧数据已保留，请检查存储空间后重试。"
                }
            }
    }

    private fun loadSettings() {
        settingsJob?.cancel()
        settingsJob =
            viewModelScope.launch {
                prefs.flow
                    .catch {
                        settingsError.value = true
                        settingsReady.value = true
                    }
                    .collect {
                        settings.value = it
                        settingsReady.value = true
                        settingsError.value = false
                    }
            }
    }

    fun retryStartup() {
        startupError.value = null
        loadSettings()
        loadDatabase()
    }

    fun resetSettings() =
        action("设置已恢复") {
            prefs.resetDamagedSettings()
            withContext(Dispatchers.Main) { loadSettings() }
        }

    fun lock() {
        externalUntil = 0
        externalWasUnlocked = false
        unlocked.value = false
        if (!busy.value) {
            prepared.value?.folder?.deleteRecursively()
            prepared.value = null
            com.shiyi.memory.FileCleanup.clear(
                app,
                android.os.SystemClock.elapsedRealtime() >= sharingUntil,
            )
        }
    }

    fun deleteMemory(id: String, back: () -> Unit) =
        action("", back) {
            repo.delete("memory", id)
            undoDeletion.emit(id)
        }

    fun acceptShare(intent: android.content.Intent) {
        if (
            intent.action !in
                listOf(
                    android.content.Intent.ACTION_SEND,
                    android.content.Intent.ACTION_SEND_MULTIPLE,
                )
        )
            return
        val uris = com.shiyi.memory.platform.ShareImport.uris(intent)
        action("分享图片已导入") {
            val state =
                combine(loading, startupError) { waiting, failure -> waiting to failure }
                    .first { !it.first || it.second != null }
            require(state.second == null) { "请先解决数据读取问题，再重新分享图片" }
            val draft = com.shiyi.memory.platform.ShareImport(app, repo).prepare(uris)
            sharedDraft.value = draft
        }
    }

    fun action(message: String = "已保存", onSuccess: () -> Unit = {}, block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
                if (message.isNotBlank()) messages.emit(message)
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = e.localizedMessage ?: "操作未完成，请重试"
            } finally {
                busy.value = false
                if (isInBackground && externalUntil == 0L) lock()
            }
        }
    }

    fun importImages(uris: List<Uri>, done: (List<String>) -> Unit) =
        action(
            "已导入原图",
            block = {
                val images = mutableListOf<String>()
                try {
                    uris.distinct().forEach { images += repo.importImage(it) }
                } catch (e: Exception) {
                    images.forEach { repo.file(it).delete() }
                    throw e
                }
                withContext(Dispatchers.Main) { done(images) }
            },
        )

    fun unlock(pin: String) =
        action(
            "",
            block = {
                val s = prefs.read()
                require(System.currentTimeMillis() >= s.retryAfter) { "尝试次数过多，请稍后再试" }
                val chars = pin.toCharArray()
                val ok =
                    try {
                        Crypto.verifyPin(chars, s.pinSalt, s.pinHash)
                    } finally {
                        chars.fill('\u0000')
                    }
                if (ok) {
                    prefs.update { it.copy(failedAttempts = 0, retryAfter = 0) }
                    unlocked.value = true
                } else {
                    prefs.update {
                        val n = it.failedAttempts + 1
                        it.copy(
                            failedAttempts = n,
                            retryAfter = if (n >= 5) System.currentTimeMillis() + 30_000 else 0,
                        )
                    }
                    error.value = "密码不正确"
                }
            },
        )

    fun setPin(pin: String) =
        action(
            "应用锁已更新",
            block = {
                require(pin.matches(Regex("[0-9]{6,12}"))) { "请设置 6–12 位数字密码" }
                val salt = Crypto.salt()
                val chars = pin.toCharArray()
                val hash =
                    try {
                        Crypto.hashPin(chars, salt)
                    } finally {
                        chars.fill('\u0000')
                    }
                prefs.update {
                    it.copy(pinSalt = salt, pinHash = hash, failedAttempts = 0, retryAfter = 0)
                }
                unlocked.value = true
            },
        )

    fun inspectBackup(uri: Uri, password: String) =
        action(
            "备份校验完成",
            block = {
                prepared.value?.folder?.deleteRecursively()
                prepared.value = backup.inspect(uri, password.toCharArray())
            },
        )

    override fun onCleared() {
        prepared.value?.folder?.deleteRecursively()
        super.onCleared()
    }
}

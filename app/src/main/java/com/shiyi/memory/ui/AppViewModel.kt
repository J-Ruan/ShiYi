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
    val repo = app.repository
    val prefs = app.settings
    val data = MutableStateFlow(Snapshot())
    val settings = MutableStateFlow(Settings())
    val settingsReady = MutableStateFlow(false)
    val loading = MutableStateFlow(true)
    val busy = MutableStateFlow(false)
    val unlocked = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val backup = BackupService(app, repo, prefs)
    val prepared = MutableStateFlow<PreparedBackup?>(null)

    init {
        viewModelScope.launch {
            repo.flow
                .retryWhen { e, attempt ->
                    error.value = "读取数据库失败：${e.localizedMessage}"
                    delay((attempt + 1).coerceAtMost(10) * 1000)
                    true
                }
                .collect {
                    data.value = it
                    loading.value = false
                }
        }
        viewModelScope.launch {
            prefs.flow
                .catch { error.value = "读取设置失败，请重新启动应用" }
                .collect {
                    settings.value = it
                    settingsReady.value = true
                }
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
            }
        }
    }

    fun importImages(uris: List<Uri>, done: (List<String>) -> Unit) =
        action(
            "已导入原图",
            block = {
                val images = uris.map { repo.importImage(it) }
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

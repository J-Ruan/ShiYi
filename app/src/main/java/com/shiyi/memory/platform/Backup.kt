package com.shiyi.memory.platform

import android.content.Context
import android.net.Uri
import com.shiyi.memory.data.*
import com.shiyi.memory.domain.*
import java.io.*
import java.util.zip.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BackupManifest(
    val format: Int = 1,
    val data: Snapshot,
    val settings: Settings,
    val checksums: Map<String, String>,
)

data class PreparedBackup(val folder: File, val manifest: BackupManifest)

class BackupService(
    private val context: Context,
    private val repository: MemoryRepository,
    private val settings: SettingsRepository,
) {
    private val json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
    }

    suspend fun export(uri: Uri, password: CharArray): Long =
        withContext(Dispatchers.IO) {
            repository.mutex.withLock {
                val temp = File.createTempFile("backup-", ".zip", context.cacheDir)
                try {
                    val snapshot = repository.snapshot()
                    validateSnapshot(snapshot)
                    val manifest =
                        BackupManifest(
                            data = snapshot,
                            settings = settings.read(),
                            checksums =
                                snapshot.images().associateWith { path ->
                                    val file = repository.file(path)
                                    require(file.isFile) { "原图缺失：$path，备份未完成" }
                                    Crypto.sha256(file)
                                },
                        )
                    ZipOutputStream(temp.outputStream().buffered()).use { zip ->
                        zip.putNextEntry(ZipEntry("manifest.json"))
                        zip.write(json.encodeToString(manifest).toByteArray())
                        zip.closeEntry()
                        snapshot.images().sorted().forEach { path ->
                            zip.putNextEntry(ZipEntry("media/$path"))
                            repository.file(path).inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                    val size =
                        temp.length() +
                            56 +
                            ((temp.length() + 1024 * 1024 - 1) / (1024 * 1024)) * 20
                    requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use {
                        Crypto.encrypt(temp, it, password)
                    }
                    settings.update {
                        it.copy(
                            lastBackup = System.currentTimeMillis(),
                            backupSize = size,
                            backupLocation = uri.toString(),
                        )
                    }
                    size
                } finally {
                    password.fill('\u0000')
                    temp.delete()
                }
            }
        }

    suspend fun inspect(uri: Uri, password: CharArray): PreparedBackup =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "restore-${newId()}").apply { mkdirs() }
            val zipFile = File(dir, "archive.zip")
            try {
                requireNotNull(context.contentResolver.openInputStream(uri)).use {
                    Crypto.decrypt(it, zipFile, password)
                }
                var total = 0L
                val seen = mutableSetOf<String>()
                ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        require(
                            !entry.isDirectory &&
                                (entry.name == "manifest.json" ||
                                    entry.name.matches(Regex("media/[A-Za-z0-9_-]+\\.img"))) &&
                                seen.add(entry.name)
                        ) {
                            "备份目录结构无效"
                        }
                        val target = File(dir, entry.name)
                        target.parentFile?.mkdirs()
                        val limit =
                            if (entry.name == "manifest.json") 32L * 1024 * 1024
                            else
                                (context.cacheDir.usableSpace / 2).coerceAtMost(
                                    4L * 1024 * 1024 * 1024
                                )
                        var bytes = 0L
                        target.outputStream().use { out ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                bytes += count
                                total += count
                                require(bytes <= limit && total <= 8L * 1024 * 1024 * 1024) {
                                    "备份超出设备可用空间或安全限制"
                                }
                                out.write(buffer, 0, count)
                            }
                        }
                        zip.closeEntry()
                    }
                }
                val manifest =
                    json.decodeFromString<BackupManifest>(File(dir, "manifest.json").readText())
                require(manifest.format == 1)
                validateSnapshot(manifest.data)
                require(manifest.settings.theme in listOf("system", "light", "dark"))
                require(manifest.checksums.keys == manifest.data.images()) { "图片清单不完整" }
                require(
                    seen == manifest.data.images().map { "media/$it" }.toSet() + "manifest.json"
                ) {
                    "备份文件集合不一致"
                }
                manifest.checksums.forEach { (path, hash) ->
                    require(Crypto.sha256(File(dir, "media/$path")) == hash) { "图片校验失败" }
                }
                zipFile.delete()
                PreparedBackup(dir, manifest)
            } catch (e: Exception) {
                dir.deleteRecursively()
                throw e
            } finally {
                password.fill('\u0000')
            }
        }

    suspend fun restore(prepared: PreparedBackup) =
        withContext(Dispatchers.IO + NonCancellable) {
            repository.mutex.withLock {
                val copied = mutableListOf<File>()
                val originalSettings = settings.read()
                try {
                    // New random names make old data usable until the database transaction commits.
                    val mapping = prepared.manifest.data.images().associateWith { newId() + ".img" }
                    mapping.forEach { (old, new) ->
                        val target = repository.file(new)
                        copied += target
                        File(prepared.folder, "media/$old").copyTo(target)
                    }
                    val s = prepared.manifest.data
                    val translated =
                        s.copy(
                            partner =
                                s.partner.map { it.copy(avatar = mapping[it.avatar].orEmpty()) },
                            media = s.media.map { it.copy(path = mapping.getValue(it.path)) },
                            memories =
                                s.memories.map {
                                    it.copy(coverPath = mapping[it.coverPath].orEmpty())
                                },
                            wishes = s.wishes.map { it.copy(image = mapping[it.image].orEmpty()) },
                        )
                    // Preserve this device's lock and notification choices.
                    settings.update {
                        prepared.manifest.settings.copy(
                            pinSalt = originalSettings.pinSalt,
                            pinHash = originalSettings.pinHash,
                            biometric = originalSettings.biometric,
                            notifications = originalSettings.notifications,
                            failedAttempts = originalSettings.failedAttempts,
                            retryAfter = originalSettings.retryAfter,
                        )
                    }
                    try {
                        repository.replace(translated)
                    } catch (e: Exception) {
                        settings.update { originalSettings }
                        throw e
                    }
                } catch (e: Exception) {
                    copied.forEach { it.delete() }
                    throw e
                } finally {
                    prepared.folder.deleteRecursively()
                }
            }
        }
}

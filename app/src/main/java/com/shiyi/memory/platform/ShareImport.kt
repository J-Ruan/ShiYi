package com.shiyi.memory.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AtomicFile
import com.shiyi.memory.data.*
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A completed copy is durable before navigation. No external URI is persisted. */
class ShareImport(private val context: Context, private val repo: MemoryRepository) {
    private val folder = File(context.noBackupFilesDir, "share-drafts").apply { mkdirs() }

    suspend fun prepare(uris: List<Uri>): String {
        require(uris.isNotEmpty() && uris.size <= 100) { "请选择 1–100 张图片" }
        val id = newId()
        val copied = mutableListOf<String>()
        val journal = File(folder, "$id.importing")
        fun checkpoint() {
            val atomic = AtomicFile(journal)
            val out = atomic.startWrite()
            try {
                out.write(Json.encodeToString(copied).toByteArray())
                atomic.finishWrite(out)
            } catch (e: Exception) {
                atomic.failWrite(out)
                throw e
            }
        }
        try {
            checkpoint()
            uris.distinct().forEach { uri ->
                require(uri.scheme == "content") { "分享图片地址无效" }
                copied += repo.importImage(uri)
                checkpoint()
            }
            check(journal.renameTo(File(folder, id)))
            return id
        } catch (e: Exception) {
            copied.forEach { repo.file(it).delete() }
            AtomicFile(journal).delete()
            throw e
        }
    }

    /**
     * Interrupted import preserves completed originals; unavailable temporary grants need
     * re-sharing.
     */
    fun recoverInterrupted(): Boolean {
        val journals =
            folder
                .listFiles()
                .orEmpty()
                .filter { it.name.endsWith(".importing") || it.name.endsWith(".importing.bak") }
                .map { it.name.removeSuffix(".bak") }
                .distinct()
        journals.forEach { name ->
            val file = File(folder, name)
            val paths =
                Json.decodeFromString<List<String>>(
                    AtomicFile(file).openRead().use { it.bufferedReader().readText() }
                )
            if (paths.isEmpty()) AtomicFile(file).delete()
            else {
                check(paths.all { repo.file(it).isFile })
                check(file.renameTo(File(folder, name.removeSuffix(".importing"))))
            }
        }
        return journals.isNotEmpty()
    }

    fun pending(): String? =
        folder
            .listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.matches(Regex("[A-Za-z0-9-]+")) }
            .minByOrNull { it.lastModified() }
            ?.name

    fun paths(id: String): List<String> {
        require(id.matches(Regex("[A-Za-z0-9-]+")))
        return Json.decodeFromString<List<String>>(
            AtomicFile(File(folder, id)).openRead().use { it.bufferedReader().readText() }
        )
    }

    fun finish(id: String) {
        if (id.matches(Regex("[A-Za-z0-9-]+"))) AtomicFile(File(folder, id)).delete()
    }

    companion object {
        @Suppress("DEPRECATION")
        fun uris(intent: Intent): List<Uri> {
            val streams =
                if (intent.action == Intent.ACTION_SEND_MULTIPLE)
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
                else listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            return (streams +
                    (0 until (intent.clipData?.itemCount ?: 0)).mapNotNull {
                        intent.clipData?.getItemAt(it)?.uri
                    })
                .distinct()
        }
    }
}

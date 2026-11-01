package com.shiyi.memory.data

import android.content.Context
import com.shiyi.memory.domain.MediaCipher
import java.io.*

/**
 * Only this boundary understands the encrypted file format. Original encoded bytes never change.
 */
class ImageStorage(
    private val context: Context,
    val directory: File = File(context.filesDir, "secure-media"),
    private val keyProvider: () -> ByteArray = { LocalKeys.media(context) },
) {
    init {
        directory.mkdirs()
    }

    fun file(path: String): File {
        require(path.matches(Regex("[A-Za-z0-9_-]+\\.img"))) { "无效图片" }
        return File(directory, path)
    }

    fun write(path: String, input: InputStream) {
        val target = file(path)
        require(!target.exists()) { "图片已存在" }
        val temp = File(directory, "${newId()}.part")
        val key = keyProvider()
        try {
            FileOutputStream(temp).use { out ->
                MediaCipher.encrypt(input, out, key)
                out.fd.sync()
            }
            require(temp.renameTo(target)) { "无法保存图片" }
        } finally {
            key.fill(0)
            temp.delete()
        }
    }

    fun copyOriginal(path: String, output: OutputStream) {
        val key = keyProvider()
        try {
            file(path).inputStream().use { MediaCipher.decrypt(it, output, key) }
        } finally {
            key.fill(0)
        }
    }

    fun temporary(path: String, sharing: Boolean = false): File {
        val dir = File(context.cacheDir, if (sharing) "exports" else "sensitive").apply { mkdirs() }
        val temp = File.createTempFile("image-", ".img", dir)
        try {
            temp.outputStream().use { copyOriginal(path, it) }
            return temp
        } catch (e: Exception) {
            temp.delete()
            throw IOException("图片损坏或无法解密", e)
        }
    }

    fun <T> withOriginal(path: String, block: (File) -> T): T {
        val temp = temporary(path)
        return try {
            block(temp)
        } finally {
            temp.delete()
        }
    }

    fun clearTemporary() {
        listOf("sensitive", "exports").forEach { File(context.cacheDir, it).deleteRecursively() }
    }
}

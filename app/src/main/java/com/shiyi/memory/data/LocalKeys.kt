package com.shiyi.memory.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.shiyi.memory.domain.Crypto
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Device bound random keys; PIN and biometrics gate UI, not scheduled reminders. */
object LocalKeys {
    private const val alias = "shiyi-storage-v1"

    @Synchronized
    fun read(context: Context): ByteArray {
        val path = File(context.noBackupFilesDir, "storage-key-v1")
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!path.exists()) {
            require(
                !context.getDatabasePath("memory-secure.db").exists() &&
                    File(context.filesDir, "secure-media").listFiles().orEmpty().isEmpty()
            ) {
                "本机加密密钥缺失。数据已保留，请从加密备份恢复，勿清除应用数据。"
            }
            if (!store.containsAlias(alias))
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                    init(
                        KeyGenParameterSpec.Builder(
                                alias,
                                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                            )
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setKeySize(256)
                            .setUserAuthenticationRequired(false)
                            .build()
                    )
                    generateKey()
                }
            val raw = Crypto.randomBytes(64)
            try {
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.ENCRYPT_MODE, store.getKey(alias, null) as SecretKey)
                c.updateAAD("ShiYi local keys v1".toByteArray())
                val atomic = AtomicFile(path)
                val out = atomic.startWrite()
                try {
                    out.write(c.iv + c.doFinal(raw))
                    atomic.finishWrite(out)
                } catch (e: Exception) {
                    atomic.failWrite(out)
                    throw e
                }
            } finally {
                raw.fill(0)
            }
        }
        val wrapped = AtomicFile(path).openRead().use { it.readBytes() }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(
            Cipher.DECRYPT_MODE,
            store.getKey(alias, null) as SecretKey,
            GCMParameterSpec(128, wrapped.copyOfRange(0, 12)),
        )
        c.updateAAD("ShiYi local keys v1".toByteArray())
        return c.doFinal(wrapped.copyOfRange(12, wrapped.size)).also { require(it.size == 64) }
    }

    fun database(context: Context): ByteArray {
        val raw = read(context)
        return try {
            raw.copyOfRange(0, 32)
                .joinToString("") { "%02x".format(it) }
                .toByteArray(Charsets.US_ASCII)
        } finally {
            raw.fill(0)
        }
    }

    fun media(context: Context): ByteArray {
        val raw = read(context)
        return try {
            raw.copyOfRange(32, 64)
        } finally {
            raw.fill(0)
        }
    }
}

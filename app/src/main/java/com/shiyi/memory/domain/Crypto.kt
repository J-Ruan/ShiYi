package com.shiyi.memory.domain

import java.io.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object Crypto {
    private val random = SecureRandom()
    private val magic = "SHIYI002".toByteArray(Charsets.US_ASCII)
    private const val iterations = 310_000

    fun randomBytes(n: Int) = ByteArray(n).also(random::nextBytes)

    fun derive(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun hashPin(pin: CharArray, salt: String) =
        Base64.getEncoder().encodeToString(derive(pin, Base64.getDecoder().decode(salt)))

    fun salt() = Base64.getEncoder().encodeToString(randomBytes(16))

    fun verifyPin(pin: CharArray, salt: String, hash: String) =
        MessageDigest.isEqual(
            Base64.getDecoder().decode(hashPin(pin, salt)),
            Base64.getDecoder().decode(hash),
        )

    private const val blockSize = 1024 * 1024

    private fun frameCipher(
        mode: Int,
        key: ByteArray,
        iv: ByteArray,
        header: ByteArray,
        index: Long,
        size: Int,
    ): Cipher {
        val nonce = iv.copyOf()
        for (i in 0..7) nonce[11 - i] =
            (nonce[11 - i].toInt() xor (index ushr (i * 8)).toInt()).toByte()
        val aad =
            java.nio.ByteBuffer.allocate(header.size + 12)
                .put(header)
                .putLong(index)
                .putInt(size)
                .array()
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            updateAAD(aad)
        }
    }

    fun encrypt(source: File, output: OutputStream, password: CharArray) {
        require(password.size >= 8) { "备份密码至少 8 位" }
        val salt = randomBytes(16)
        val iv = randomBytes(12)
        val header = magic + salt + iv
        val key = derive(password, salt)
        try {
            DataOutputStream(output).use { out ->
                out.write(header)
                var index = 0L
                source.inputStream().use { input ->
                    val buffer = ByteArray(blockSize)
                    while (true) {
                        var count = 0
                        while (count < buffer.size) {
                            val n = input.read(buffer, count, buffer.size - count)
                            if (n < 0) break
                            count += n
                        }
                        if (count == 0) break
                        val encrypted =
                            frameCipher(Cipher.ENCRYPT_MODE, key, iv, header, index++, count)
                                .doFinal(buffer, 0, count)
                        out.writeInt(count)
                        out.write(encrypted)
                    }
                    buffer.fill(0)
                }
                out.writeInt(0)
                out.write(frameCipher(Cipher.ENCRYPT_MODE, key, iv, header, index, 0).doFinal())
            }
        } finally {
            key.fill(0)
        }
    }

    fun decrypt(input: InputStream, target: File, password: CharArray) {
        val stream = DataInputStream(input)
        val header = ByteArray(36)
        var key: ByteArray? = null
        try {
            stream.readFully(header)
            require(header.copyOfRange(0, 8).contentEquals(magic)) { "不是支持的拾忆备份" }
            key = derive(password, header.copyOfRange(8, 24))
            val iv = header.copyOfRange(24, 36)
            target.outputStream().use { out ->
                var index = 0L
                var total = 0L
                while (true) {
                    val size = stream.readInt()
                    require(size in 0..blockSize)
                    total += size
                    require(total <= 8L * 1024 * 1024 * 1024) { "备份超出安全限制" }
                    val encrypted = ByteArray(size + 16)
                    stream.readFully(encrypted)
                    val plain =
                        frameCipher(Cipher.DECRYPT_MODE, key, iv, header, index++, size)
                            .doFinal(encrypted)
                    if (size == 0) {
                        require(stream.read() == -1) { "备份尾部无效" }
                        break
                    }
                    out.write(plain)
                    plain.fill(0)
                }
            }
        } catch (e: Exception) {
            target.delete()
            throw IOException("密码错误或备份文件已损坏", e)
        } finally {
            key?.fill(0)
        }
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

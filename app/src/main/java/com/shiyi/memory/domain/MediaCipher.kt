package com.shiyi.memory.domain

import java.io.*
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Authenticated bounded frames: never release a frame before its GCM tag verifies. */
object MediaCipher {
    private val magic = "SHYIMG01".toByteArray(Charsets.US_ASCII)
    private const val block = 1024 * 1024

    private fun cipher(
        mode: Int,
        key: ByteArray,
        header: ByteArray,
        index: Long,
        size: Int,
    ): Cipher {
        val nonce = header.copyOfRange(8, 20)
        for (i in 0..7) nonce[11 - i] =
            (nonce[11 - i].toInt() xor (index ushr (8 * i)).toInt()).toByte()
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            updateAAD(
                java.nio.ByteBuffer.allocate(32).put(header).putLong(index).putInt(size).array()
            )
        }
    }

    fun encrypt(input: InputStream, output: OutputStream, key: ByteArray) {
        require(key.size == 32)
        val header = magic + Crypto.randomBytes(12)
        val out = DataOutputStream(output)
        out.write(header)
        val buffer = ByteArray(block)
        try {
            var index = 0L
            while (true) {
                var count = 0
                while (count < block) {
                    val n = input.read(buffer, count, block - count)
                    if (n < 0) break
                    count += n
                }
                out.writeInt(count)
                out.write(
                    cipher(Cipher.ENCRYPT_MODE, key, header, index++, count)
                        .doFinal(buffer, 0, count)
                )
                if (count == 0) break
            }
            out.flush()
        } finally {
            buffer.fill(0)
        }
    }

    fun decrypt(input: InputStream, output: OutputStream, key: ByteArray) {
        val stream = DataInputStream(input)
        val header = ByteArray(20)
        stream.readFully(header)
        require(header.copyOfRange(0, 8).contentEquals(magic)) { "图片格式无效" }
        var index = 0L
        var total = 0L
        while (true) {
            val n = stream.readInt()
            require(n in 0..block)
            total += n
            require(total <= 4L * 1024 * 1024 * 1024) { "图片超出安全限制" }
            val encrypted = ByteArray(n + 16)
            stream.readFully(encrypted)
            val bytes = cipher(Cipher.DECRYPT_MODE, key, header, index++, n).doFinal(encrypted)
            try {
                if (n == 0) {
                    require(stream.read() == -1)
                    break
                }
                output.write(bytes)
            } finally {
                bytes.fill(0)
            }
        }
    }
}

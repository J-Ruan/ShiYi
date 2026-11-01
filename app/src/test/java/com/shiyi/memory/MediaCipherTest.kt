package com.shiyi.memory

import com.shiyi.memory.domain.MediaCipher
import java.io.*
import org.junit.Assert.*
import org.junit.Test

class MediaCipherTest {
    private val key = ByteArray(32) { it.toByte() }

    private fun encrypt(data: ByteArray) =
        ByteArrayOutputStream()
            .also { MediaCipher.encrypt(data.inputStream(), it, key) }
            .toByteArray()

    @Test
    fun originalBytesAndIndependentNonce() {
        val bytes = ByteArray(2 * 1024 * 1024 + 33) { (it % 251).toByte() }
        val a = encrypt(bytes)
        val b = encrypt(bytes)
        assertFalse(a.contentEquals(b))
        val out = ByteArrayOutputStream()
        MediaCipher.decrypt(a.inputStream(), out, key)
        assertArrayEquals(bytes, out.toByteArray())
    }

    @Test
    fun rejectsCorruptionTruncationWrongKeyAndAppend() {
        val bytes = encrypt("private fixture".toByteArray())
        val corrupt = bytes.copyOf().apply { this[lastIndex] = (last().toInt() xor 1).toByte() }
        for (bad in listOf(corrupt, bytes.copyOf(bytes.size - 1), bytes + byteArrayOf(0))) {
            assertThrows(Exception::class.java) {
                MediaCipher.decrypt(bad.inputStream(), ByteArrayOutputStream(), key)
            }
        }
        assertThrows(Exception::class.java) {
            MediaCipher.decrypt(bytes.inputStream(), ByteArrayOutputStream(), ByteArray(32))
        }
    }

    @Test
    fun reorderingFramesFailsAuthentication() {
        val bytes = encrypt(ByteArray(2 * 1024 * 1024) { it.toByte() })
        val frame = 1024 * 1024 + 20
        val bad = bytes.copyOf()
        System.arraycopy(bytes, 20 + frame, bad, 20, frame)
        System.arraycopy(bytes, 20, bad, 20 + frame, frame)
        assertThrows(Exception::class.java) {
            MediaCipher.decrypt(bad.inputStream(), ByteArrayOutputStream(), key)
        }
    }
}

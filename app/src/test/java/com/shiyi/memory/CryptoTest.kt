package com.shiyi.memory

import com.shiyi.memory.domain.Crypto
import java.io.*
import org.junit.Assert.*
import org.junit.Test

class CryptoTest {
    private fun source() =
        File.createTempFile("source", ".zip").apply { writeText("原始图片及回忆".repeat(100)) }

    @Test
    fun authenticatedRoundtripAndRandomness() {
        val input = source()
        val restored = File.createTempFile("restore", ".zip")
        try {
            val a = ByteArrayOutputStream()
            val b = ByteArrayOutputStream()
            Crypto.encrypt(input, a, "密码password".toCharArray())
            Crypto.encrypt(input, b, "密码password".toCharArray())
            assertFalse(a.toByteArray().contentEquals(b.toByteArray()))
            Crypto.decrypt(
                ByteArrayInputStream(a.toByteArray()),
                restored,
                "密码password".toCharArray(),
            )
            assertArrayEquals(input.readBytes(), restored.readBytes())
            assertFalse(String(a.toByteArray()).contains("原始图片"))
        } finally {
            input.delete()
            restored.delete()
        }
    }

    @Test
    fun wrongPasswordAndTamperingRejected() {
        val input = source()
        val restored = File.createTempFile("restore", ".zip")
        try {
            val out = ByteArrayOutputStream()
            Crypto.encrypt(input, out, "password-1".toCharArray())
            val bytes = out.toByteArray()
            assertThrows(IOException::class.java) {
                Crypto.decrypt(ByteArrayInputStream(bytes), restored, "password-2".toCharArray())
            }
            assertFalse(restored.exists())
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
            assertThrows(IOException::class.java) {
                Crypto.decrypt(ByteArrayInputStream(bytes), restored, "password-1".toCharArray())
            }
            assertFalse(restored.exists())
        } finally {
            input.delete()
            restored.delete()
        }
    }

    @Test
    fun headerIsAuthenticated() {
        val input = source()
        val restored = File.createTempFile("restore", ".zip")
        try {
            val out = ByteArrayOutputStream()
            Crypto.encrypt(input, out, "password-1".toCharArray())
            val bytes = out.toByteArray()
            bytes[10] = (bytes[10].toInt() xor 1).toByte()
            assertThrows(IOException::class.java) {
                Crypto.decrypt(ByteArrayInputStream(bytes), restored, "password-1".toCharArray())
            }
        } finally {
            input.delete()
            restored.delete()
        }
    }

    @Test
    fun pinHashIsSaltedAndVerifiable() {
        val a = Crypto.salt()
        val b = Crypto.salt()
        val hash = Crypto.hashPin("123456".toCharArray(), a)
        assertNotEquals(hash, Crypto.hashPin("123456".toCharArray(), b))
        assertTrue(Crypto.verifyPin("123456".toCharArray(), a, hash))
        assertFalse(Crypto.verifyPin("123457".toCharArray(), a, hash))
    }

    @Test
    fun shortBackupPasswordRejected() {
        val f = source()
        try {
            assertThrows(IllegalArgumentException::class.java) {
                Crypto.encrypt(f, ByteArrayOutputStream(), "short".toCharArray())
            }
        } finally {
            f.delete()
        }
    }

    @Test
    fun truncationAndAppendedDataRejected() {
        val input = source()
        val restored = File.createTempFile("restore", ".zip")
        try {
            val out = ByteArrayOutputStream()
            Crypto.encrypt(input, out, "password-1".toCharArray())
            val bytes = out.toByteArray()
            for (bad in listOf(bytes.copyOf(bytes.size - 20), bytes + byteArrayOf(0))) {
                assertThrows(IOException::class.java) {
                    Crypto.decrypt(ByteArrayInputStream(bad), restored, "password-1".toCharArray())
                }
                assertFalse(restored.exists())
            }
        } finally {
            input.delete()
            restored.delete()
        }
    }

    @Test
    fun multiBlockRoundtrip() {
        val input =
            source().apply { writeBytes(ByteArray(2 * 1024 * 1024 + 33) { (it % 251).toByte() }) }
        val restored = File.createTempFile("restore", ".zip")
        try {
            val out = ByteArrayOutputStream()
            Crypto.encrypt(input, out, "password-1".toCharArray())
            Crypto.decrypt(
                ByteArrayInputStream(out.toByteArray()),
                restored,
                "password-1".toCharArray(),
            )
            assertEquals(Crypto.sha256(input), Crypto.sha256(restored))
        } finally {
            input.delete()
            restored.delete()
        }
    }
}

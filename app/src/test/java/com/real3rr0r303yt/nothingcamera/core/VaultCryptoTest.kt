/*
 * NothingCamera – private, offline camera for Android
 * Copyright (C) 2026 Real3rr0r303YT
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.real3rr0r303yt.nothingcamera.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class VaultCryptoTest {

    /** Software stand-in for the Android Keystore master key. */
    private class TestWrapper(seed: Byte = 7) : KeyWrapper {
        private val key = SecretKeySpec(ByteArray(32) { seed }, "AES")
        override fun wrap(key: ByteArray): ByteArray {
            val iv = ByteArray(12).also(SecureRandom()::nextBytes)
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, this@TestWrapper.key, GCMParameterSpec(128, iv)) }
            return iv + c.doFinal(key)
        }
        override fun unwrap(blob: ByteArray): ByteArray {
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, blob.copyOfRange(0, 12)))
            }
            return c.doFinal(blob, 12, blob.size - 12)
        }
    }

    private val wrapper = TestWrapper()
    private fun data(size: Int) = ByteArray(size).also(SecureRandom()::nextBytes)

    @Test
    fun roundTripsVariousSizes() {
        val c = VaultCrypto.CHUNK
        for (size in listOf(0, 1, 1000, c - 1, c, c + 1, 3 * c, 3 * c + 17)) {
            val plain = data(size)
            val blob = VaultCrypto.encryptBytes(plain, wrapper, VaultCrypto.TYPE_PHOTO)
            assertArrayEquals("size $size", plain, VaultCrypto.decryptBytes(blob, wrapper))
        }
    }

    @Test
    fun streamingWritesInSmallPiecesMatch() {
        val plain = data(200_000)
        val bos = ByteArrayOutputStream()
        VaultCrypto.encrypt(bos, wrapper, VaultCrypto.TYPE_VIDEO).use { out ->
            var i = 0
            while (i < plain.size) { val n = minOf(777, plain.size - i); out.write(plain, i, n); i += n }
        }
        val blob = bos.toByteArray()
        assertEquals(VaultCrypto.TYPE_VIDEO, VaultCrypto.readType(blob.inputStream()))
        assertArrayEquals(plain, VaultCrypto.decryptBytes(blob, wrapper))
    }

    @Test
    fun ciphertextDoesNotContainPlaintext() {
        val plain = "GPS 52.52N secret".repeat(100).toByteArray()
        val blob = VaultCrypto.encryptBytes(plain, wrapper, VaultCrypto.TYPE_PHOTO)
        assertFalse(String(blob, Charsets.ISO_8859_1).contains("secret"))
    }

    @Test
    fun detectsTampering() {
        val blob = VaultCrypto.encryptBytes(data(5000), wrapper, VaultCrypto.TYPE_PHOTO)
        blob[blob.size - 40] = (blob[blob.size - 40].toInt() xor 1).toByte()
        assertFails { VaultCrypto.decryptBytes(blob, wrapper) }
    }

    @Test
    fun detectsTypeHeaderTampering() {
        val blob = VaultCrypto.encryptBytes(data(100), wrapper, VaultCrypto.TYPE_PHOTO)
        blob[4] = VaultCrypto.TYPE_VIDEO.toByte()
        assertFails { VaultCrypto.decryptBytes(blob, wrapper) }
    }

    @Test
    fun detectsTruncationAtChunkBoundary() {
        val c = VaultCrypto.CHUNK
        val blob = VaultCrypto.encryptBytes(data(2 * c + 10), wrapper, VaultCrypto.TYPE_VIDEO)
        val headerLen = blob.size - (2 * (c + 16) + (10 + 16))
        val truncated = blob.copyOf(headerLen + c + 16) // drop everything after the first chunk
        assertFails { VaultCrypto.decryptBytes(truncated, wrapper) }
    }

    @Test
    fun wrongMasterKeyFails() {
        val blob = VaultCrypto.encryptBytes(data(100), wrapper, VaultCrypto.TYPE_PHOTO)
        assertFails { VaultCrypto.decryptBytes(blob, TestWrapper(seed = 9)) }
    }

    private fun assertFails(block: () -> Unit) {
        val failed = try { block(); false } catch (e: IOException) { true }
        assertTrue("expected an IOException", failed)
    }
}

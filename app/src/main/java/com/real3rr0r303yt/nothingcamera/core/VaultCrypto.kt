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

import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Wraps/unwraps per-file data keys with a master key (Android Keystore on device). */
interface KeyWrapper {
    fun wrap(key: ByteArray): ByteArray
    fun unwrap(blob: ByteArray): ByteArray
}

/**
 * Streaming authenticated encryption for vault files.
 *
 * Every file gets its own random AES-256 data key, wrapped by the master key. The content is split
 * into 64 KiB chunks, each sealed with AES-GCM; the nonce is `prefix(7) ‖ counter(4) ‖ lastFlag(1)`
 * and the file header is authenticated as AAD. That makes reordering, truncation, appending and
 * header tampering detectable, and lets large videos be decrypted without loading them into memory.
 *
 * Layout: `"NCV1" | type(1) | wrappedLen(2) | wrapped | prefix(7) | chunk…`
 */
object VaultCrypto {
    const val TYPE_PHOTO = 0
    const val TYPE_VIDEO = 1
    const val CHUNK = 64 * 1024
    private const val TAG = 16
    private const val PREFIX = 7
    private val MAGIC = byteArrayOf('N'.code.toByte(), 'C'.code.toByte(), 'V'.code.toByte(), '1'.code.toByte())
    private val random = SecureRandom()

    fun encrypt(out: OutputStream, wrapper: KeyWrapper, type: Int): OutputStream {
        val dataKey = ByteArray(32).also(random::nextBytes)
        val prefix = ByteArray(PREFIX).also(random::nextBytes)
        val wrapped = wrapper.wrap(dataKey)
        val header = ByteBuffer.allocate(MAGIC.size + 1 + 2 + wrapped.size + PREFIX)
            .put(MAGIC).put(type.toByte()).putShort(wrapped.size.toShort()).put(wrapped).put(prefix)
            .array()
        out.write(header)
        return EncryptingStream(out, SecretKeySpec(dataKey, "AES"), prefix, header)
    }

    fun decrypt(input: InputStream, wrapper: KeyWrapper): InputStream {
        val header = readHeader(input)
        val key = try {
            SecretKeySpec(wrapper.unwrap(header.wrapped), "AES")
        } catch (e: GeneralSecurityException) {
            throw IOException("Cannot unwrap file key", e)
        }
        return DecryptingStream(PushbackInputStream(input, 1), key, header.prefix, header.raw)
    }

    /** Reads only the header – cheap way to learn whether an entry is a photo or a video. */
    fun readType(input: InputStream): Int = readHeader(input).type

    fun encryptBytes(plain: ByteArray, wrapper: KeyWrapper, type: Int): ByteArray {
        val bos = java.io.ByteArrayOutputStream(plain.size + plain.size / CHUNK * TAG + 128)
        encrypt(bos, wrapper, type).use { it.write(plain) }
        return bos.toByteArray()
    }

    fun decryptBytes(blob: ByteArray, wrapper: KeyWrapper): ByteArray =
        decrypt(blob.inputStream(), wrapper).use { it.readBytes() }

    private class Header(val type: Int, val wrapped: ByteArray, val prefix: ByteArray, val raw: ByteArray)

    private fun readHeader(input: InputStream): Header {
        val din = DataInputStream(input)
        val magic = ByteArray(4)
        try {
            din.readFully(magic)
            if (!magic.contentEquals(MAGIC)) throw IOException("Not a vault file")
            val type = din.readUnsignedByte()
            val wrappedLen = din.readUnsignedShort()
            val wrapped = ByteArray(wrappedLen).also(din::readFully)
            val prefix = ByteArray(PREFIX).also(din::readFully)
            val raw = ByteBuffer.allocate(4 + 1 + 2 + wrappedLen + PREFIX)
                .put(magic).put(type.toByte()).putShort(wrappedLen.toShort()).put(wrapped).put(prefix).array()
            return Header(type, wrapped, prefix, raw)
        } catch (e: EOFException) {
            throw IOException("Truncated vault header", e)
        }
    }

    private fun nonce(prefix: ByteArray, counter: Int, last: Boolean): ByteArray =
        ByteBuffer.allocate(12).put(prefix).putInt(counter).put(if (last) 1 else 0).array()

    private fun cipher(mode: Int, key: SecretKeySpec, nonce: ByteArray, aad: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, key, GCMParameterSpec(TAG * 8, nonce))
            updateAAD(aad)
        }

    private class EncryptingStream(
        private val out: OutputStream,
        private val key: SecretKeySpec,
        private val prefix: ByteArray,
        private val aad: ByteArray,
    ) : OutputStream() {
        private val buf = ByteArray(CHUNK)
        private var fill = 0
        private var counter = 0
        private var closed = false

        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            check(!closed) { "Stream closed" }
            var o = off
            var remaining = len
            while (remaining > 0) {
                // Only emit a full chunk once we know more data follows – the final chunk is flagged.
                if (fill == CHUNK) emit(last = false)
                val n = minOf(remaining, CHUNK - fill)
                System.arraycopy(b, o, buf, fill, n)
                fill += n; o += n; remaining -= n
            }
        }

        private fun emit(last: Boolean) {
            if (counter == Int.MAX_VALUE) throw IOException("File too large")
            val c = cipher(Cipher.ENCRYPT_MODE, key, nonce(prefix, counter++, last), aad)
            out.write(c.doFinal(buf, 0, fill))
            fill = 0
        }

        override fun flush() = out.flush()

        override fun close() {
            if (closed) return
            emit(last = true)
            buf.fill(0)
            closed = true
            out.close()
        }
    }

    private class DecryptingStream(
        private val input: PushbackInputStream,
        private val key: SecretKeySpec,
        private val prefix: ByteArray,
        private val aad: ByteArray,
    ) : InputStream() {
        private val block = ByteArray(CHUNK + TAG)
        private var plain = ByteArray(0)
        private var pos = 0
        private var counter = 0
        private var done = false

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) == -1) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (pos >= plain.size) {
                if (done) return -1
                nextChunk()
            }
            val n = minOf(len, plain.size - pos)
            System.arraycopy(plain, pos, b, off, n)
            pos += n
            return n
        }

        private fun nextChunk() {
            var read = 0
            while (read < block.size) {
                val n = input.read(block, read, block.size - read)
                if (n == -1) break
                read += n
            }
            if (read < TAG) throw IOException("Truncated vault file")
            val last = if (read < block.size) {
                true
            } else {
                val peek = input.read()
                if (peek == -1) true else { input.unread(peek); false }
            }
            plain = try {
                cipher(Cipher.DECRYPT_MODE, key, nonce(prefix, counter++, last), aad).doFinal(block, 0, read)
            } catch (e: GeneralSecurityException) {
                throw IOException("Vault file is corrupt or was tampered with", e)
            }
            pos = 0
            done = last
        }

        override fun close() {
            plain.fill(0)
            input.close()
        }
    }
}

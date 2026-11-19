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

package com.real3rr0r303yt.nothingcamera.privacy

import android.content.Context
import android.net.Uri
import com.real3rr0r303yt.nothingcamera.core.FileNames
import com.real3rr0r303yt.nothingcamera.core.VaultCrypto
import java.io.File
import java.io.RandomAccessFile

/**
 * Encrypted, app-private photo/video vault.
 *
 * Files live in `noBackupFilesDir/vault` – invisible to other apps and the gallery, excluded from
 * backups – and are encrypted with [VaultCrypto] under a Keystore master key. Wiping deletes the
 * master key first, so even unerased flash blocks are unreadable afterwards.
 */
class Vault(private val context: Context) {

    data class Entry(val file: File, val isVideo: Boolean, val time: Long) {
        val id: String get() = file.name
    }

    private val dir = File(context.noBackupFilesDir, "vault")
    private val recDir = File(context.noBackupFilesDir, "rec")
    private val playDir = File(context.cacheDir, "play")
    private val wrapper = KeystoreKeyWrapper(KEY_ALIAS)

    fun entries(): List<Entry> = dir.listFiles().orEmpty()
        .filter { it.name.endsWith(".ncv") }
        .mapNotNull { f ->
            runCatching {
                val type = f.inputStream().use(VaultCrypto::readType)
                Entry(f, type == VaultCrypto.TYPE_VIDEO, f.lastModified())
            }.getOrNull()
        }
        .sortedByDescending { it.time }

    fun savePhoto(jpeg: ByteArray): Entry = write(VaultCrypto.TYPE_PHOTO) { it.write(jpeg) }

    /** Plain temp file for CameraX to record into; encrypt it afterwards with [importVideo]. */
    fun newRecordingFile(): File = File(recDir.apply { mkdirs() }, FileNames.randomHex(16) + ".mp4")

    fun importVideo(plain: File): Entry = try {
        write(VaultCrypto.TYPE_VIDEO) { out -> plain.inputStream().use { it.copyTo(out, VaultCrypto.CHUNK) } }
    } finally {
        shred(plain)
    }

    fun readPhoto(entry: Entry): ByteArray =
        entry.file.inputStream().use { VaultCrypto.decrypt(it, wrapper).use { s -> s.readBytes() } }

    /** Decrypts a video into the cache for playback. Call [clearPlayback] when done. */
    fun decryptForPlayback(entry: Entry): File {
        val out = File(playDir.apply { mkdirs() }, FileNames.randomHex(16) + ".mp4")
        entry.file.inputStream().use { input ->
            VaultCrypto.decrypt(input, wrapper).use { plain -> out.outputStream().use { plain.copyTo(it, VaultCrypto.CHUNK) } }
        }
        return out
    }

    fun clearPlayback() {
        playDir.listFiles()?.forEach(::shred)
    }

    /** Copies an entry to the shared gallery. Metadata was already removed at capture time. */
    fun export(entry: Entry, randomName: Boolean): Uri = if (entry.isVideo) {
        MediaSaver.saveVideo(context, FileNames.video(randomName)) { out ->
            entry.file.inputStream().use { VaultCrypto.decrypt(it, wrapper).use { plain -> plain.copyTo(out, VaultCrypto.CHUNK) } }
        }
    } else {
        MediaSaver.savePhoto(context, readPhoto(entry), FileNames.photo(randomName))
    }

    fun delete(entry: Entry) = shred(entry.file)

    /** Panic wipe: destroy the master key, then erase every file. */
    fun wipe() {
        wrapper.destroy()
        dir.listFiles()?.forEach(::shred)
        clearTransient()
    }

    /** Removes decrypted/unencrypted leftovers (e.g. after a crash during recording). */
    fun clearTransient() {
        clearPlayback()
        recDir.listFiles()?.forEach(::shred)
    }

    private fun write(type: Int, body: (java.io.OutputStream) -> Unit): Entry {
        dir.mkdirs()
        val target = File(dir, FileNames.vault())
        val part = File(dir, target.name + ".part")
        try {
            part.outputStream().use { fos -> VaultCrypto.encrypt(fos, wrapper, type).use(body) }
            if (!part.renameTo(target)) throw java.io.IOException("Rename failed")
        } catch (e: Exception) {
            shred(part)
            throw e
        }
        return Entry(target, type == VaultCrypto.TYPE_VIDEO, target.lastModified())
    }

    private fun shred(file: File) {
        if (!file.exists()) return
        // Best effort overwrite (flash wear-levelling may keep old blocks – encryption covers that).
        runCatching {
            RandomAccessFile(file, "rw").use { raf ->
                val zeros = ByteArray(64 * 1024)
                var left = raf.length()
                raf.seek(0)
                while (left > 0) {
                    val n = minOf(left, zeros.size.toLong()).toInt()
                    raf.write(zeros, 0, n)
                    left -= n
                }
                raf.fd.sync()
            }
        }
        file.delete()
    }

    private companion object {
        const val KEY_ALIAS = "nothingcamera_vault_master"
    }
}

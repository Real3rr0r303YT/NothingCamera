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

import java.io.ByteArrayOutputStream

/**
 * Rewrites a JPEG so that it carries **no metadata at all** except the picture orientation.
 *
 * Dropped: every APPn segment (EXIF incl. GPS, make/model, serial numbers, timestamps, maker notes,
 * embedded thumbnails, XMP, MPF / Ultra-HDR gain maps, JFIF thumbnails) and every COM segment, plus
 * anything appended after the end-of-image marker. Kept: the ICC colour profile (APP2 `ICC_PROFILE`)
 * so colours stay correct, and the compressed image data itself, byte for byte – no re-encoding,
 * no quality loss.
 *
 * A fresh, minimal EXIF block with only the Orientation tag is written so galleries rotate the photo
 * correctly. Pure Kotlin, no Android dependencies – unit tested.
 */
object JpegSanitizer {

    private const val SOI = 0xD8
    private const val EOI = 0xD9
    private const val SOS = 0xDA
    private const val COM = 0xFE
    private val ICC = "ICC_PROFILE\u0000".toByteArray(Charsets.US_ASCII)

    fun sanitize(jpeg: ByteArray, rotationDegrees: Int): ByteArray {
        require(jpeg.size >= 4 && u8(jpeg, 0) == 0xFF && u8(jpeg, 1) == SOI) { "Not a JPEG" }
        val out = ByteArrayOutputStream(jpeg.size)
        out.write(0xFF); out.write(SOI)
        out.write(orientationSegment(orientationFor(rotationDegrees)))

        var i = 2
        while (i < jpeg.size) {
            require(u8(jpeg, i) == 0xFF) { "Corrupt JPEG at $i" }
            while (i < jpeg.size && u8(jpeg, i) == 0xFF) i++ // fill bytes
            require(i < jpeg.size) { "Truncated JPEG" }
            val marker = u8(jpeg, i++)
            when {
                marker == EOI -> {
                    out.write(0xFF); out.write(EOI)
                    return out.toByteArray() // trailing data (gain maps, junk) is dropped
                }
                marker in 0xD0..0xD7 || marker == 0x01 -> {
                    out.write(0xFF); out.write(marker)
                }
                else -> {
                    require(i + 2 <= jpeg.size) { "Truncated segment" }
                    val len = (u8(jpeg, i) shl 8) or u8(jpeg, i + 1)
                    val end = i + len
                    require(len >= 2 && end <= jpeg.size) { "Bad segment length" }
                    if (marker == SOS) {
                        out.write(0xFF); out.write(marker); out.write(jpeg, i, len)
                        i = copyEntropyData(jpeg, end, out)
                        continue
                    }
                    if (keep(marker, jpeg, i + 2, end)) {
                        out.write(0xFF); out.write(marker); out.write(jpeg, i, len)
                    }
                    i = end
                }
            }
        }
        // No EOI found: close the image ourselves.
        out.write(0xFF); out.write(EOI)
        return out.toByteArray()
    }

    /** EXIF orientation value for a clockwise rotation in degrees. */
    fun orientationFor(rotationDegrees: Int): Int = when (((rotationDegrees % 360) + 360) % 360) {
        90 -> 6
        180 -> 3
        270 -> 8
        else -> 1
    }

    /** Reads back the orientation written by [sanitize] (1 if the file did not come from us). */
    fun readOrientation(jpeg: ByteArray): Int {
        if (jpeg.size < 2 + 36) return 1
        val o = jpeg[31].toInt() and 0xFF
        val expected = orientationSegment(o)
        for (k in expected.indices) if (jpeg[2 + k] != expected[k]) return 1
        return o
    }

    /** Clockwise rotation in degrees for an EXIF orientation value (mirrored values ignored). */
    fun degreesFor(orientation: Int): Int = when (orientation) {
        6 -> 90
        3 -> 180
        8 -> 270
        else -> 0
    }

    /** Markers of the header segments up to (and including) SOS – used by tests. */
    internal fun segmentMarkers(jpeg: ByteArray): List<Int> {
        val markers = mutableListOf<Int>()
        var i = 2
        while (i + 4 <= jpeg.size) {
            if (u8(jpeg, i) != 0xFF) break
            val marker = u8(jpeg, i + 1)
            if (marker == SOS || marker == EOI) { markers += marker; break }
            markers += marker
            i += 2 + ((u8(jpeg, i + 2) shl 8) or u8(jpeg, i + 3))
        }
        return markers
    }

    private fun keep(marker: Int, data: ByteArray, payloadStart: Int, end: Int): Boolean = when {
        marker == COM -> false
        marker == 0xE2 -> startsWith(data, payloadStart, end, ICC)
        marker in 0xE0..0xEF -> false
        else -> true // SOF, DQT, DHT, DRI, …
    }

    /** Copies entropy-coded data after SOS; returns the index of the next marker's 0xFF. */
    private fun copyEntropyData(jpeg: ByteArray, start: Int, out: ByteArrayOutputStream): Int {
        var i = start
        while (i < jpeg.size) {
            val b = u8(jpeg, i)
            if (b != 0xFF) { out.write(b); i++; continue }
            if (i + 1 >= jpeg.size) return jpeg.size
            val next = u8(jpeg, i + 1)
            when {
                next == 0x00 || next in 0xD0..0xD7 -> { out.write(0xFF); out.write(next); i += 2 }
                next == 0xFF -> i++ // fill byte
                else -> return i // real marker: EOI or next segment (progressive JPEG)
            }
        }
        return i
    }

    /** APP1 "Exif" segment, big-endian TIFF, a single IFD0 entry: Orientation (0x0112, SHORT). */
    internal fun orientationSegment(orientation: Int): ByteArray {
        val payload = byteArrayOf(
            'E'.code.toByte(), 'x'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(), 0, 0,
            'M'.code.toByte(), 'M'.code.toByte(), 0x00, 0x2A, 0, 0, 0, 8, // TIFF header, IFD0 at 8
            0x00, 0x01, // one entry
            0x01, 0x12, 0x00, 0x03, 0, 0, 0, 1, // tag 0x0112, SHORT, count 1
            0x00, orientation.toByte(), 0, 0, // value, padding
            0, 0, 0, 0, // no next IFD
        )
        val len = payload.size + 2
        return byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (len shr 8).toByte(), len.toByte()) + payload
    }

    private fun startsWith(data: ByteArray, from: Int, end: Int, prefix: ByteArray): Boolean {
        if (end - from < prefix.size) return false
        for (k in prefix.indices) if (data[from + k] != prefix[k]) return false
        return true
    }

    private fun u8(b: ByteArray, i: Int) = b[i].toInt() and 0xFF
}

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

class JpegSanitizerTest {

    private fun seg(marker: Int, payload: ByteArray): ByteArray {
        val len = payload.size + 2
        return byteArrayOf(0xFF.toByte(), marker.toByte(), (len shr 8).toByte(), len.toByte()) + payload
    }

    private fun ascii(s: String) = s.toByteArray(Charsets.ISO_8859_1)

    private val entropy = byteArrayOf(0x12, 0x34, 0xFF.toByte(), 0x00, 0x56, 0xFF.toByte(), 0xD3.toByte(), 0x78)

    private fun sampleJpeg(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        out.write(seg(0xE0, ascii("JFIF\u0000\u0001\u0002")))
        out.write(seg(0xE1, ascii("Exif\u0000\u0000GPS 52.52N 13.40E Nothing A059 SERIAL123")))
        out.write(seg(0xE1, ascii("http://ns.adobe.com/xap/1.0/\u0000<x:xmpmeta>secret</x:xmpmeta>")))
        out.write(seg(0xE2, ascii("ICC_PROFILE\u0000\u0001\u0001DisplayP3")))
        out.write(seg(0xE2, ascii("MPF\u0000gainmap-index")))
        out.write(seg(0xFE, ascii("comment with owner name")))
        out.write(seg(0xDB, byteArrayOf(0, 1, 2, 3)))
        out.write(seg(0xC0, byteArrayOf(8, 0, 16, 0, 16, 1, 1, 0x11, 0)))
        out.write(seg(0xDA, byteArrayOf(1, 1, 0, 0, 63, 0)))
        out.write(entropy)
        out.write(byteArrayOf(0xFF.toByte(), 0xD9.toByte()))
        out.write(ascii("TRAILING-GAINMAP-IMAGE"))
        return out.toByteArray()
    }

    private fun ByteArray.contains(sub: String): Boolean = String(this, Charsets.ISO_8859_1).contains(sub)

    @Test
    fun removesAllMetadataButKeepsIccAndImageData() {
        val clean = JpegSanitizer.sanitize(sampleJpeg(), 90)
        for (secret in listOf("GPS", "SERIAL123", "xmpmeta", "MPF", "owner name", "JFIF", "TRAILING")) {
            assertFalse("leaked $secret", clean.contains(secret))
        }
        assertTrue(clean.contains("ICC_PROFILE"))
        assertTrue(clean.contains(String(entropy, Charsets.ISO_8859_1)))
        // Starts with SOI, ends with EOI.
        assertEquals(0xD8, clean[1].toInt() and 0xFF)
        assertEquals(0xD9, clean.last().toInt() and 0xFF)
        // Our single APP1 (orientation), then ICC APP2, then the image segments.
        assertEquals(listOf(0xE1, 0xE2, 0xDB, 0xC0, 0xDA), JpegSanitizer.segmentMarkers(clean))
    }

    @Test
    fun writesOrientationTag() {
        val clean = JpegSanitizer.sanitize(sampleJpeg(), 270)
        val app1 = JpegSanitizer.orientationSegment(8)
        assertArrayEquals(app1, clean.copyOfRange(2, 2 + app1.size))
    }

    @Test
    fun readsOrientationBack() {
        for (deg in listOf(0, 90, 180, 270)) {
            val clean = JpegSanitizer.sanitize(sampleJpeg(), deg)
            assertEquals(deg, JpegSanitizer.degreesFor(JpegSanitizer.readOrientation(clean)))
        }
        assertEquals(1, JpegSanitizer.readOrientation(sampleJpeg()))
    }

    @Test
    fun orientationMapping() {
        assertEquals(1, JpegSanitizer.orientationFor(0))
        assertEquals(6, JpegSanitizer.orientationFor(90))
        assertEquals(3, JpegSanitizer.orientationFor(180))
        assertEquals(8, JpegSanitizer.orientationFor(270))
        assertEquals(8, JpegSanitizer.orientationFor(-90))
        assertEquals(1, JpegSanitizer.orientationFor(360))
    }

    @Test
    fun orientationSegmentLengthIsConsistent() {
        val s = JpegSanitizer.orientationSegment(6)
        val declared = ((s[2].toInt() and 0xFF) shl 8) or (s[3].toInt() and 0xFF)
        assertEquals(s.size - 2, declared)
    }

    @Test
    fun sanitizingTwiceIsStable() {
        val once = JpegSanitizer.sanitize(sampleJpeg(), 180)
        assertArrayEquals(once, JpegSanitizer.sanitize(once, 180))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonJpeg() {
        JpegSanitizer.sanitize(ascii("PNG not a jpeg"), 0)
    }
}

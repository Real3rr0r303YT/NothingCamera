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


package com.real3rr0r303yt.nothingcamera.camera

import android.graphics.Bitmap
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/**
 * Zoom beyond what the camera driver offers: decodes only the centre region of the JPEG
 * (no full-size bitmap in memory) and re-encodes it. The result has no metadata at all;
 * [com.real3rr0r303yt.nothingcamera.core.JpegSanitizer] adds the orientation afterwards.
 */
object DigitalZoom {

    fun cropCenter(jpeg: ByteArray, factor: Float, quality: Int = 95): ByteArray {
        if (factor <= 1.01f) return jpeg
        val decoder = newDecoder(jpeg) ?: return jpeg
        try {
            val w = decoder.width
            val h = decoder.height
            val cw = (w / factor).roundToInt().coerceIn(16, w)
            val ch = (h / factor).roundToInt().coerceIn(16, h)
            val left = (w - cw) / 2
            val top = (h - ch) / 2
            val bmp = decoder.decodeRegion(Rect(left, top, left + cw, top + ch), null) ?: return jpeg
            val out = ByteArrayOutputStream(cw * ch / 4)
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bmp.recycle()
            return out.toByteArray()
        } finally {
            decoder.recycle()
        }
    }

    private fun newDecoder(jpeg: ByteArray): BitmapRegionDecoder? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            BitmapRegionDecoder.newInstance(jpeg, 0, jpeg.size)
        } else {
            legacyDecoder(jpeg)
        }
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun legacyDecoder(jpeg: ByteArray): BitmapRegionDecoder? = BitmapRegionDecoder.newInstance(jpeg, 0, jpeg.size, false)
}

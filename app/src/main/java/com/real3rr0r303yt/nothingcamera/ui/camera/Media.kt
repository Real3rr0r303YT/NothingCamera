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

package com.real3rr0r303yt.nothingcamera.ui.camera

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.real3rr0r303yt.nothingcamera.core.JpegSanitizer
import kotlinx.coroutines.flow.MutableSharedFlow
import java.io.IOException

/** Request coming from another app (ACTION_IMAGE_CAPTURE / ACTION_VIDEO_CAPTURE). */
data class CaptureRequest(val video: Boolean, val output: Uri?, val durationLimitMs: Long = 0)

/** Hardware keys (volume / camera button) act as shutter while the viewfinder is visible. */
object ShutterKeys {
    @Volatile var active = false
    val events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}

/** Decodes a small, correctly rotated preview from a sanitized JPEG. */
fun decodeThumbnail(jpeg: ByteArray, maxSide: Int = 256): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return null
    val degrees = JpegSanitizer.degreesFor(JpegSanitizer.readOrientation(jpeg))
    if (degrees == 0) return bmp
    val m = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true).also { if (it !== bmp) bmp.recycle() }
}

fun videoThumbnail(context: Context, uri: Uri): Bitmap? =
    runCatching { context.contentResolver.loadThumbnail(uri, Size(256, 256), null) }.getOrNull()

/** Result for ACTION_IMAGE_CAPTURE: write to EXTRA_OUTPUT, or return a small bitmap in "data". */
fun deliverPhoto(context: Context, request: CaptureRequest, jpeg: ByteArray): Intent {
    val output = request.output
    return if (output != null) {
        val stream = runCatching { context.contentResolver.openOutputStream(output, "wt") }.getOrNull()
            ?: context.contentResolver.openOutputStream(output)
            ?: throw IOException("Cannot write $output")
        stream.use { it.write(jpeg) }
        Intent().setData(output)
    } else {
        Intent().putExtra("data", decodeThumbnail(jpeg, 512))
    }
}

/** Current display rotation the device is physically held in (portrait UI stays locked). */
@Composable
fun rememberDeviceRotation(): State<Int> {
    val context = LocalContext.current
    val rotation = remember { mutableIntStateOf(Surface.ROTATION_0) }
    DisposableEffect(context) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) return
                rotation.intValue = when (orientation) {
                    in 45 until 135 -> Surface.ROTATION_270
                    in 135 until 225 -> Surface.ROTATION_180
                    in 225 until 315 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
            }
        }
        listener.enable()
        onDispose { listener.disable() }
    }
    return rotation
}

/** Angle to rotate icons by so they stay upright for the user. */
fun iconAngle(rotation: Int): Float = when (rotation) {
    Surface.ROTATION_90 -> 90f
    Surface.ROTATION_180 -> 180f
    Surface.ROTATION_270 -> -90f
    else -> 0f
}

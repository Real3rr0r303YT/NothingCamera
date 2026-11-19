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

import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** File names for new captures. Random names hide *when* a photo was taken. */
object FileNames {
    private val random = SecureRandom()
    private const val HEX = "0123456789abcdef"

    fun photo(randomName: Boolean, now: Long = System.currentTimeMillis(), zone: TimeZone = TimeZone.getDefault()) =
        name("IMG", "jpg", randomName, now, zone)

    fun video(randomName: Boolean, now: Long = System.currentTimeMillis(), zone: TimeZone = TimeZone.getDefault()) =
        name("VID", "mp4", randomName, now, zone)

    fun vault(): String = randomHex(24) + ".ncv"

    internal fun name(prefix: String, ext: String, randomName: Boolean, now: Long, zone: TimeZone): String {
        val body = if (randomName) {
            randomHex(16)
        } else {
            SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.ROOT).apply { timeZone = zone }.format(Date(now))
        }
        return "${prefix}_$body.$ext"
    }

    fun randomHex(chars: Int): String = buildString(chars) {
        repeat(chars) { append(HEX[random.nextInt(16)]) }
    }
}

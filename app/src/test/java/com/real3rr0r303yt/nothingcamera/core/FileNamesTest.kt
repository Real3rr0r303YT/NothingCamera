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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class FileNamesTest {
    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun timestampNames() {
        // 2026-11-19 13:47:05.123 UTC
        val t = 1_795_096_025_123L
        assertEquals("IMG_20261119_134705_123.jpg", FileNames.photo(false, t, utc))
        assertEquals("VID_20261119_134705_123.mp4", FileNames.video(false, t, utc))
    }

    @Test
    fun randomNamesHideTheTime() {
        val a = FileNames.photo(true)
        val b = FileNames.photo(true)
        assertTrue(a.matches(Regex("IMG_[0-9a-f]{16}\\.jpg")))
        assertNotEquals(a, b)
        assertTrue(FileNames.vault().matches(Regex("[0-9a-f]{24}\\.ncv")))
    }
}

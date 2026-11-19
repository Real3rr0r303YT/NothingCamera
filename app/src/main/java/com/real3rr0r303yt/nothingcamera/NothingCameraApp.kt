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

package com.real3rr0r303yt.nothingcamera

import android.app.Application
import com.real3rr0r303yt.nothingcamera.data.SettingsStore
import com.real3rr0r303yt.nothingcamera.privacy.Vault
import kotlin.concurrent.thread

class NothingCameraApp : Application() {
    lateinit var settings: SettingsStore
        private set
    lateinit var vault: Vault
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        vault = Vault(this)
        // Shred decrypted/unencrypted leftovers from a previous run (crash, force stop).
        thread(name = "cleanup") { runCatching { vault.clearTransient() } }
    }
}

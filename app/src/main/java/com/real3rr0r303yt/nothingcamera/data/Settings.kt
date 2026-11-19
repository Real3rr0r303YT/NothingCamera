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

package com.real3rr0r303yt.nothingcamera.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SaveTarget { GALLERY, VAULT }
enum class Flash { OFF, AUTO, ON }
enum class Ratio { R4_3, R16_9 }
enum class VideoQuality { HD, FHD, UHD }

data class Settings(
    val saveTarget: SaveTarget = SaveTarget.GALLERY,
    val randomNames: Boolean = false,
    val vaultLock: Boolean = true,
    val blockScreenshots: Boolean = true,
    val shutterSound: Boolean = true,
    val recordAudio: Boolean = true,
    val grid: Boolean = false,
    val ratio: Ratio = Ratio.R4_3,
    val videoQuality: VideoQuality = VideoQuality.FHD,
    val videoFps: Int = 30,
    val flash: Flash = Flash.OFF,
    val timerSeconds: Int = 0,
)

/**
 * Settings in a private SharedPreferences file (app sandbox only, excluded from every backup).
 * No location, no history – just the toggles below.
 */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Settings> = _state.asStateFlow()

    fun update(transform: (Settings) -> Settings) {
        val next = transform(_state.value)
        _state.value = next
        prefs.edit()
            .putString("saveTarget", next.saveTarget.name)
            .putBoolean("randomNames", next.randomNames)
            .putBoolean("vaultLock", next.vaultLock)
            .putBoolean("blockScreenshots", next.blockScreenshots)
            .putBoolean("shutterSound", next.shutterSound)
            .putBoolean("recordAudio", next.recordAudio)
            .putBoolean("grid", next.grid)
            .putString("ratio", next.ratio.name)
            .putString("videoQuality", next.videoQuality.name)
            .putInt("videoFps", next.videoFps)
            .putString("flash", next.flash.name)
            .putInt("timerSeconds", next.timerSeconds)
            .apply()
    }

    private fun load(): Settings {
        val d = Settings()
        return Settings(
            saveTarget = enumOr(prefs.getString("saveTarget", null), d.saveTarget),
            randomNames = prefs.getBoolean("randomNames", d.randomNames),
            vaultLock = prefs.getBoolean("vaultLock", d.vaultLock),
            blockScreenshots = prefs.getBoolean("blockScreenshots", d.blockScreenshots),
            shutterSound = prefs.getBoolean("shutterSound", d.shutterSound),
            recordAudio = prefs.getBoolean("recordAudio", d.recordAudio),
            grid = prefs.getBoolean("grid", d.grid),
            ratio = enumOr(prefs.getString("ratio", null), d.ratio),
            videoQuality = enumOr(prefs.getString("videoQuality", null), d.videoQuality),
            videoFps = prefs.getInt("videoFps", d.videoFps).takeIf { it == 30 || it == 60 } ?: 30,
            flash = enumOr(prefs.getString("flash", null), d.flash),
            timerSeconds = prefs.getInt("timerSeconds", d.timerSeconds),
        )
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default
}

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

package com.real3rr0r303yt.nothingcamera.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.real3rr0r303yt.nothingcamera.BuildConfig
import com.real3rr0r303yt.nothingcamera.Links
import com.real3rr0r303yt.nothingcamera.R
import com.real3rr0r303yt.nothingcamera.data.SaveTarget
import com.real3rr0r303yt.nothingcamera.data.Settings
import com.real3rr0r303yt.nothingcamera.data.SettingsStore
import com.real3rr0r303yt.nothingcamera.data.VideoQuality
import com.real3rr0r303yt.nothingcamera.privacy.Vault
import com.real3rr0r303yt.nothingcamera.ui.components.ActionRow
import com.real3rr0r303yt.nothingcamera.ui.components.Card
import com.real3rr0r303yt.nothingcamera.ui.components.ChoiceRow
import com.real3rr0r303yt.nothingcamera.ui.components.ScreenHeader
import com.real3rr0r303yt.nothingcamera.ui.components.SectionLabel
import com.real3rr0r303yt.nothingcamera.ui.components.ToggleRow
import com.real3rr0r303yt.nothingcamera.ui.theme.NothingGrey
import com.real3rr0r303yt.nothingcamera.ui.theme.NothingOutline
import com.real3rr0r303yt.nothingcamera.ui.theme.NothingRed
import com.real3rr0r303yt.nothingcamera.ui.theme.NothingSurface
import com.real3rr0r303yt.nothingcamera.ui.theme.NothingWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    settings: Settings,
    store: SettingsStore,
    vault: Vault,
    onOpenVault: (() -> Unit)?,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmWipe by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf(currentLanguage()) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.settings), onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
        ) {
            PrivacyBanner()

            SectionLabel(stringResource(R.string.section_privacy))
            Card {
                ChoiceRow(
                    title = stringResource(R.string.save_to),
                    subtitle = stringResource(R.string.save_to_summary),
                    options = listOf(
                        SaveTarget.GALLERY to stringResource(R.string.gallery),
                        SaveTarget.VAULT to stringResource(R.string.vault),
                    ),
                    selected = settings.saveTarget,
                    onSelect = { v -> store.update { it.copy(saveTarget = v) } },
                )
                ToggleRow(
                    stringResource(R.string.random_names),
                    stringResource(R.string.random_names_summary),
                    settings.randomNames,
                ) { v -> store.update { it.copy(randomNames = v) } }
                ToggleRow(
                    stringResource(R.string.vault_lock),
                    stringResource(R.string.vault_lock_summary),
                    settings.vaultLock,
                ) { v -> store.update { it.copy(vaultLock = v) } }
                ToggleRow(
                    stringResource(R.string.block_screenshots),
                    stringResource(R.string.block_screenshots_summary),
                    settings.blockScreenshots,
                ) { v -> store.update { it.copy(blockScreenshots = v) } }
            }

            SectionLabel(stringResource(R.string.section_capture))
            Card {
                ToggleRow(stringResource(R.string.shutter_sound), null, settings.shutterSound) { v ->
                    store.update { it.copy(shutterSound = v) }
                }
                ToggleRow(
                    stringResource(R.string.record_audio),
                    stringResource(R.string.record_audio_summary),
                    settings.recordAudio,
                ) { v -> store.update { it.copy(recordAudio = v) } }
                ToggleRow(stringResource(R.string.grid), null, settings.grid) { v -> store.update { it.copy(grid = v) } }
                ChoiceRow(
                    title = stringResource(R.string.video_quality),
                    subtitle = null,
                    options = listOf(VideoQuality.HD to "720p", VideoQuality.FHD to "1080p", VideoQuality.UHD to "4K"),
                    selected = settings.videoQuality,
                    onSelect = { v -> store.update { it.copy(videoQuality = v) } },
                )
            }

            SectionLabel(stringResource(R.string.section_language))
            Card {
                ChoiceRow(
                    title = stringResource(R.string.language),
                    subtitle = null,
                    options = listOf(
                        "" to stringResource(R.string.language_system),
                        "en" to "English",
                        "de" to "Deutsch",
                        "tr" to "Türkçe",
                    ),
                    selected = language,
                    onSelect = { tag ->
                        language = tag
                        AppCompatDelegate.setApplicationLocales(
                            if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
                        )
                    },
                )
            }

            SectionLabel(stringResource(R.string.vault))
            Card {
                if (onOpenVault != null) {
                    ActionRow(stringResource(R.string.open_vault), stringResource(R.string.vault_info), Icons.Outlined.Lock, onOpenVault)
                }
                ActionRow(
                    stringResource(R.string.wipe_vault),
                    stringResource(R.string.wipe_vault_summary),
                    Icons.Outlined.DeleteForever,
                    { confirmWipe = true },
                    danger = true,
                )
            }

            SectionLabel(stringResource(R.string.section_support))
            DonateCard { Links.open(context, Links.DONATE) }

            SectionLabel(stringResource(R.string.section_about))
            Card {
                ActionRow(
                    stringResource(R.string.source_code),
                    "github.com/Real3rr0r303YT/NothingCamera",
                    Icons.Outlined.Code,
                    { Links.open(context, Links.SOURCE) },
                )
                ActionRow(
                    stringResource(R.string.license),
                    "GNU GPL v3.0",
                    Icons.Outlined.Gavel,
                    { Links.open(context, Links.LICENSE) },
                )
                ActionRow(
                    stringResource(R.string.made_by),
                    "TikTok · @3rr0r303YT",
                    Icons.Outlined.VideoLibrary,
                    { Links.open(context, Links.TIKTOK) },
                    accent = true,
                )
            }
            Text(
                "NothingCamera v${BuildConfig.VERSION_NAME} · ${stringResource(R.string.unofficial)}",
                style = MaterialTheme.typography.labelSmall,
                color = NothingGrey,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text(stringResource(R.string.wipe_vault)) },
            text = { Text(stringResource(R.string.wipe_vault_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmWipe = false
                    scope.launch {
                        withContext(Dispatchers.IO) { vault.wipe() }
                        Toast.makeText(context, R.string.vault_wiped, Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(R.string.wipe), color = NothingRed) }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text(stringResource(R.string.cancel)) } },
            containerColor = NothingSurface,
        )
    }
}

private fun currentLanguage(): String {
    val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    return listOf("en", "de", "tr").firstOrNull { tags.startsWith(it) } ?: ""
}

@Composable
private fun PrivacyBanner() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, NothingRed, RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Shield, null, tint = NothingRed)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(stringResource(R.string.privacy_summary), style = MaterialTheme.typography.bodySmall, color = NothingGrey)
        }
    }
}

@Composable
private fun DonateCard(onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(NothingSurface)
            .border(1.dp, NothingOutline, RoundedCornerShape(22.dp))
            .padding(18.dp),
    ) {
        Text(stringResource(R.string.donate_text), style = MaterialTheme.typography.bodyMedium, color = NothingGrey)
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(NothingWhite)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(NothingRed))
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Outlined.Coffee, null, tint = androidx.compose.ui.graphics.Color.Black)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.donate_button), style = MaterialTheme.typography.labelLarge, color = androidx.compose.ui.graphics.Color.Black)
        }
        Spacer(Modifier.height(8.dp))
        Text("buymeacoffee.com/3rr0r303yt", style = MaterialTheme.typography.labelSmall, color = NothingGrey,
            modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

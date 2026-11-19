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

package com.real3rr0r303yt.nothingcamera.ui.vault

import android.graphics.BitmapFactory
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.real3rr0r303yt.nothingcamera.R
import com.real3rr0r303yt.nothingcamera.data.Settings
import com.real3rr0r303yt.nothingcamera.privacy.Vault
import com.real3rr0r303yt.nothingcamera.ui.camera.decodeThumbnail
import com.real3rr0r303yt.nothingcamera.ui.components.ScreenHeader
import com.real3rr0r303yt.nothingcamera.ui.theme.NothingGrey
import com.real3rr0r303yt.nothingcamera.ui.theme.NothingRed
import com.real3rr0r303yt.nothingcamera.ui.theme.NothingSurface
import com.real3rr0r303yt.nothingcamera.ui.theme.NothingWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun VaultScreen(vault: Vault, settings: Settings, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    val entries by produceState<List<Vault.Entry>?>(null, reload) {
        value = withContext(Dispatchers.IO) { vault.entries() }
    }
    var open by remember { mutableStateOf<Vault.Entry?>(null) }
    var confirmWipe by remember { mutableStateOf(false) }

    val viewing = open
    if (viewing != null) {
        Viewer(
            vault = vault,
            entry = viewing,
            onClose = { open = null },
            onExport = {
                scope.launch {
                    val ok = runCatching { withContext(Dispatchers.IO) { vault.export(viewing, settings.randomNames) } }.isSuccess
                    Toast.makeText(context, if (ok) R.string.exported else R.string.error_generic, Toast.LENGTH_SHORT).show()
                }
            },
            onDelete = {
                scope.launch {
                    withContext(Dispatchers.IO) { vault.delete(viewing) }
                    open = null
                    reload++
                }
            },
        )
        return
    }

    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        ScreenHeader(stringResource(R.string.vault), onBack) {
            IconButton(onClick = { confirmWipe = true }) {
                Icon(Icons.Outlined.DeleteForever, stringResource(R.string.wipe_vault), tint = NothingRed)
            }
        }
        Text(
            stringResource(R.string.vault_info),
            style = MaterialTheme.typography.bodySmall,
            color = NothingGrey,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        val list = entries
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NothingRed)
            }
            list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Lock, null, tint = NothingGrey, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.vault_empty), color = NothingGrey, textAlign = TextAlign.Center)
                }
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(list, key = { it.id }) { entry ->
                    Tile(vault, entry) { open = entry }
                }
            }
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
                        reload++
                    }
                }) { Text(stringResource(R.string.wipe), color = NothingRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipe = false }) { Text(stringResource(R.string.cancel)) }
            },
            containerColor = NothingSurface,
        )
    }
}

@Composable
private fun Tile(vault: Vault, entry: Vault.Entry, onClick: () -> Unit) {
    val thumb by produceState<ImageBitmap?>(null, entry.id) {
        if (!entry.isVideo) {
            value = withContext(Dispatchers.IO) {
                runCatching { decodeThumbnail(vault.readPhoto(entry), 320)?.asImageBitmap() }.getOrNull()
            }
        }
    }
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(NothingSurface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val t = thumb
        if (t != null) {
            Image(t, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(if (entry.isVideo) Icons.Outlined.PlayArrow else Icons.Outlined.Lock, null, tint = NothingGrey)
        }
    }
}

@Composable
private fun Viewer(vault: Vault, entry: Vault.Entry, onClose: () -> Unit, onExport: () -> Unit, onDelete: () -> Unit) {
    BackHandler(onBack = onClose)
    var confirmDelete by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (entry.isVideo) {
            VideoPlayer(vault, entry)
        } else {
            val image by produceState<ImageBitmap?>(null, entry.id) {
                value = withContext(Dispatchers.IO) {
                    runCatching { decodeFull(vault.readPhoto(entry))?.asImageBitmap() }.getOrNull()
                }
            }
            val img = image
            if (img == null) {
                CircularProgressIndicator(color = NothingRed, modifier = Modifier.align(Alignment.Center))
            } else {
                Image(img, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }

        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, stringResource(R.string.close), tint = NothingWhite) }
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ViewerAction(Icons.Outlined.Download, stringResource(R.string.export_to_gallery), onExport)
            ViewerAction(Icons.Outlined.Delete, stringResource(R.string.delete), { confirmDelete = true }, danger = true)
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.delete), color = NothingRed) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
            containerColor = NothingSurface,
        )
    }
}

@Composable
private fun ViewerAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, danger: Boolean = false) {
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x99000000))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = if (danger) NothingRed else NothingWhite)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (danger) NothingRed else NothingWhite)
    }
}

/** Decrypts to a temporary cache file for playback and shreds it when leaving the viewer. */
@Composable
private fun VideoPlayer(vault: Vault, entry: Vault.Entry) {
    val file by produceState<File?>(null, entry.id) {
        value = withContext(Dispatchers.IO) { runCatching { vault.decryptForPlayback(entry) }.getOrNull() }
    }
    DisposableEffect(entry.id) {
        onDispose { vault.clearPlayback() }
    }
    val f = file
    if (f == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = NothingRed) }
        return
    }
    AndroidView(
        factory = { ctx ->
            VideoView(ctx).apply {
                setMediaController(MediaController(ctx).also { it.setAnchorView(this) })
                setVideoPath(f.absolutePath)
                start()
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

/** Full-size decode capped at ~2048 px, rotated per the orientation tag. */
private fun decodeFull(jpeg: ByteArray): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
    val side = maxOf(bounds.outWidth, bounds.outHeight)
    return decodeThumbnail(jpeg, maxSide = if (side > 0) minOf(side, 2048) else 2048)
}

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

package com.real3rr0r303yt.nothingcamera.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.real3rr0r303yt.nothingcamera.R
import com.real3rr0r303yt.nothingcamera.camera.CameraController
import com.real3rr0r303yt.nothingcamera.data.SettingsStore
import com.real3rr0r303yt.nothingcamera.privacy.Vault
import com.real3rr0r303yt.nothingcamera.ui.camera.CameraScreen
import com.real3rr0r303yt.nothingcamera.ui.camera.CaptureRequest
import com.real3rr0r303yt.nothingcamera.ui.components.DotMatrixText
import com.real3rr0r303yt.nothingcamera.ui.settings.SettingsScreen
import com.real3rr0r303yt.nothingcamera.ui.theme.NothingBlack
import com.real3rr0r303yt.nothingcamera.ui.theme.NothingGrey
import com.real3rr0r303yt.nothingcamera.ui.theme.NothingWhite
import com.real3rr0r303yt.nothingcamera.ui.vault.VaultScreen

private enum class Screen { CAMERA, SETTINGS, VAULT }

@Composable
fun CameraApp(
    store: SettingsStore,
    vault: Vault,
    request: CaptureRequest?,
    startInVideo: Boolean,
    onResult: (Intent?) -> Unit,
    authenticate: (onSuccess: () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val settings by store.state.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.CAMERA) }
    val controller = remember { CameraController(context.applicationContext) }
    DisposableEffect(Unit) { onDispose { controller.release() } }

    // The vault locks again as soon as the app leaves the foreground.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && screen == Screen.VAULT) screen = Screen.CAMERA
            if (event == Lifecycle.Event.ON_STOP) vault.clearPlayback()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    var cameraGranted by remember { mutableStateOf(granted(context, Manifest.permission.CAMERA)) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        cameraGranted = it
        askedOnce = true
    }
    var audioCallback by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        audioCallback?.invoke(ok)
        audioCallback = null
    }
    val currentSettings by rememberUpdatedState(settings)

    val openVault: () -> Unit = {
        val show = { screen = Screen.VAULT }
        if (currentSettings.vaultLock) authenticate(show) else show()
    }

    Box(Modifier.fillMaxSize().background(NothingBlack)) {
        when {
            !cameraGranted -> PermissionScreen(
                permanentlyDenied = askedOnce,
                onGrant = { cameraLauncher.launch(Manifest.permission.CAMERA) },
                onOpenSettings = {
                    context.startActivity(
                        Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            )
            screen == Screen.SETTINGS -> SettingsScreen(
                settings = settings,
                store = store,
                vault = vault,
                onOpenVault = if (request == null) openVault else null,
                onBack = { screen = Screen.CAMERA },
            )
            screen == Screen.VAULT && request == null -> VaultScreen(vault, settings) { screen = Screen.CAMERA }
            else -> CameraScreen(
                controller = controller,
                settings = settings,
                store = store,
                vault = vault,
                request = request,
                startInVideo = startInVideo,
                onResult = onResult,
                onOpenSettings = { screen = Screen.SETTINGS },
                onOpenVault = openVault,
                requestAudio = { done ->
                    audioCallback = done
                    audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
            )
        }
    }

    // Re-check after returning from system settings.
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) cameraGranted = granted(context, Manifest.permission.CAMERA)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

private fun granted(context: android.content.Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

@Composable
private fun PermissionScreen(permanentlyDenied: Boolean, onGrant: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DotMatrixText("CAMERA", dotSize = 6.dp, gap = 2.dp, accentLast = true)
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.permission_camera),
            style = MaterialTheme.typography.bodyLarge,
            color = NothingGrey,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = if (permanentlyDenied) onOpenSettings else onGrant,
            colors = ButtonDefaults.buttonColors(containerColor = NothingWhite, contentColor = Color.Black),
        ) {
            Text(stringResource(if (permanentlyDenied) R.string.open_app_settings else R.string.grant))
        }
    }
}

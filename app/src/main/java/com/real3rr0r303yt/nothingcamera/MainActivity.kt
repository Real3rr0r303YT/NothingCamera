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

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.real3rr0r303yt.nothingcamera.ui.CameraApp
import com.real3rr0r303yt.nothingcamera.ui.camera.CaptureRequest
import com.real3rr0r303yt.nothingcamera.ui.camera.ShutterKeys
import com.real3rr0r303yt.nothingcamera.ui.theme.NothingCameraTheme
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(Color.BLACK),
        )
        super.onCreate(savedInstanceState)
        val app = application as NothingCameraApp
        val request = captureRequest(intent)

        // FLAG_SECURE: no screenshots, no screen recording, blank preview in the recents switcher.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                app.settings.state.collect { s ->
                    if (s.blockScreenshots) {
                        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        }

        setContent {
            NothingCameraTheme {
                CameraApp(
                    store = app.settings,
                    vault = app.vault,
                    request = request,
                    startInVideo = intent?.action == MediaStore.INTENT_ACTION_VIDEO_CAMERA,
                    onResult = { data ->
                        setResult(if (data != null) Activity.RESULT_OK else Activity.RESULT_CANCELED, data)
                        finish()
                    },
                    authenticate = ::authenticate,
                )
            }
        }
    }

    /** Volume and camera keys trigger the shutter while the viewfinder is shown. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (ShutterKeys.active && keyCode in SHUTTER_KEYS) {
            if (event.repeatCount == 0) ShutterKeys.events.tryEmit(Unit)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (ShutterKeys.active && keyCode in SHUTTER_KEYS) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun authenticate(onSuccess: () -> Unit) {
        val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        } else {
            BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        }
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            // No screen lock on this device – nothing to verify against.
            Toast.makeText(this, R.string.no_screen_lock, Toast.LENGTH_LONG).show()
            onSuccess()
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.unlock_vault))
                .setSubtitle(getString(R.string.unlock_vault_summary))
                .setAllowedAuthenticators(authenticators)
                .build(),
        )
    }

    private fun captureRequest(intent: Intent?): CaptureRequest? {
        val action = intent?.action ?: return null
        val video = when (action) {
            MediaStore.ACTION_IMAGE_CAPTURE -> false
            MediaStore.ACTION_VIDEO_CAPTURE -> true
            else -> return null
        }
        val output = IntentCompat.getParcelableExtra(intent, MediaStore.EXTRA_OUTPUT, Uri::class.java)
        val limitSeconds = intent.getIntExtra(MediaStore.EXTRA_DURATION_LIMIT, 0)
        return CaptureRequest(video = video, output = output, durationLimitMs = limitSeconds * 1000L)
    }

    private companion object {
        val SHUTTER_KEYS = setOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_CAMERA)
    }
}

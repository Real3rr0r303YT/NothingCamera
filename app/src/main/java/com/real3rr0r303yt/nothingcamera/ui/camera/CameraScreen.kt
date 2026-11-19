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

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaActionSound
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.GridOff
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer10
import androidx.compose.material.icons.outlined.Timer3
import androidx.compose.material.icons.outlined.TimerOff
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.real3rr0r303yt.nothingcamera.R
import com.real3rr0r303yt.nothingcamera.camera.BindConfig
import com.real3rr0r303yt.nothingcamera.camera.CameraController
import com.real3rr0r303yt.nothingcamera.camera.Lens
import com.real3rr0r303yt.nothingcamera.camera.Mode
import com.real3rr0r303yt.nothingcamera.camera.VideoTarget
import com.real3rr0r303yt.nothingcamera.core.FileNames
import com.real3rr0r303yt.nothingcamera.core.JpegSanitizer
import com.real3rr0r303yt.nothingcamera.data.Flash
import com.real3rr0r303yt.nothingcamera.data.Ratio
import com.real3rr0r303yt.nothingcamera.data.SaveTarget
import com.real3rr0r303yt.nothingcamera.data.Settings
import com.real3rr0r303yt.nothingcamera.data.SettingsStore
import com.real3rr0r303yt.nothingcamera.privacy.MediaSaver
import com.real3rr0r303yt.nothingcamera.privacy.Vault
import com.real3rr0r303yt.nothingcamera.ui.components.DotMatrixText
import com.real3rr0r303yt.nothingcamera.ui.components.GlassIconButton
import com.real3rr0r303yt.nothingcamera.ui.theme.NothingGrey
import com.real3rr0r303yt.nothingcamera.ui.theme.NothingRed
import com.real3rr0r303yt.nothingcamera.ui.theme.NothingWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

private data class LastCapture(val thumb: ImageBitmap?, val uri: Uri?, val inVault: Boolean, val isVideo: Boolean)

@Composable
fun CameraScreen(
    controller: CameraController,
    settings: Settings,
    store: SettingsStore,
    vault: Vault,
    request: CaptureRequest?,
    startInVideo: Boolean,
    onResult: (Intent?) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVault: () -> Unit,
    requestAudio: (onDone: (Boolean) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val cam by controller.state.collectAsStateWithLifecycle()
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
    }

    var mode by rememberSaveable { mutableStateOf(if (request?.video == true || startInVideo) Mode.VIDEO else Mode.PHOTO) }
    var lens by rememberSaveable { mutableStateOf(Lens.BACK) }
    val rotation by rememberDeviceRotation()
    val angle by animateFloatAsState(iconAngle(rotation), label = "iconAngle")
    var countdown by remember { mutableIntStateOf(0) }
    var countdownJob by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf(false) }
    var flashOverlay by remember { mutableStateOf(false) }
    var last by remember { mutableStateOf<LastCapture?>(null) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var initError by remember { mutableStateOf<String?>(null) }
    val currentSettings by rememberUpdatedState(settings)

    val sound = remember { MediaActionSound() }
    DisposableEffect(Unit) {
        sound.load(MediaActionSound.SHUTTER_CLICK)
        onDispose { sound.release() }
    }
    fun play(kind: Int) {
        if (currentSettings.shutterSound) sound.play(kind)
    }

    LaunchedEffect(Unit) {
        try {
            controller.init()
        } catch (e: Exception) {
            initError = e.message ?: e.javaClass.simpleName
        }
    }
    LaunchedEffect(cam.ready, mode, lens, settings.ratio, settings.videoQuality) {
        if (cam.ready) {
            controller.bind(owner, previewView, BindConfig(mode, lens, settings.ratio, settings.videoQuality, settings.flash, rotation))
        }
    }
    LaunchedEffect(settings.flash) { controller.setFlash(settings.flash) }
    LaunchedEffect(rotation) { controller.setRotation(rotation) }
    LaunchedEffect(cam.extensionModes, lens) {
        if ((mode == Mode.NIGHT || mode == Mode.PORTRAIT) && cam.ready && mode !in cam.extensionModes) mode = Mode.PHOTO
    }
    LaunchedEffect(focusPoint) {
        if (focusPoint != null) { delay(1200); focusPoint = null }
    }

    fun toast(res: Int) = Toast.makeText(context, res, Toast.LENGTH_SHORT).show()

    fun shoot() {
        if (busy) return
        busy = true
        scope.launch {
            try {
                play(MediaActionSound.SHUTTER_CLICK)
                launch { flashOverlay = true; delay(90); flashOverlay = false }
                val (raw, rot) = controller.takePhoto()
                // Strip every byte of metadata before anything is written anywhere.
                val clean = withContext(Dispatchers.Default) { JpegSanitizer.sanitize(raw, rot) }
                raw.fill(0)
                if (request != null) {
                    onResult(withContext(Dispatchers.IO) { deliverPhoto(context, request, clean) })
                    return@launch
                }
                val thumb = withContext(Dispatchers.Default) { decodeThumbnail(clean)?.asImageBitmap() }
                last = if (currentSettings.saveTarget == SaveTarget.VAULT) {
                    withContext(Dispatchers.IO) { vault.savePhoto(clean) }
                    LastCapture(thumb, null, inVault = true, isVideo = false)
                } else {
                    val uri = withContext(Dispatchers.IO) {
                        MediaSaver.savePhoto(context, clean, FileNames.photo(currentSettings.randomNames))
                    }
                    LastCapture(thumb, uri, inVault = false, isVideo = false)
                }
            } catch (e: Exception) {
                toast(R.string.error_capture)
            } finally {
                busy = false
            }
        }
    }

    fun record(audio: Boolean) {
        var plain: File? = null
        var pfd: ParcelFileDescriptor? = null
        val output = request?.output
        val target: VideoTarget = try {
            when {
                output != null -> {
                    val fd = context.contentResolver.openFileDescriptor(output, "rw")
                        ?: throw IllegalStateException("No descriptor")
                    pfd = fd
                    VideoTarget.Descriptor(fd, request?.durationLimitMs ?: 0L)
                }
                request == null && currentSettings.saveTarget == SaveTarget.VAULT ->
                    VideoTarget.PlainFile(vault.newRecordingFile().also { plain = it })
                else -> VideoTarget.Gallery(FileNames.video(currentSettings.randomNames))
            }
        } catch (e: Exception) {
            toast(R.string.error_capture)
            return
        }
        play(MediaActionSound.START_VIDEO_RECORDING)
        val started = controller.startRecording(target, audio) { ok, uri ->
            play(MediaActionSound.STOP_VIDEO_RECORDING)
            runCatching { pfd?.close() }
            scope.launch {
                val vaultFile = plain
                when {
                    request != null -> onResult(
                        if (ok) Intent().setData(output ?: uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) else null,
                    )
                    vaultFile != null -> {
                        if (ok) {
                            runCatching { withContext(Dispatchers.IO) { vault.importVideo(vaultFile) } }
                                .onSuccess { last = LastCapture(null, null, inVault = true, isVideo = true) }
                                .onFailure { toast(R.string.error_capture) }
                        } else {
                            withContext(Dispatchers.IO) { vault.clearTransient() }
                            toast(R.string.error_capture)
                        }
                    }
                    ok && uri != null -> {
                        val thumb = withContext(Dispatchers.IO) { videoThumbnail(context, uri)?.asImageBitmap() }
                        last = LastCapture(thumb, uri, inVault = false, isVideo = true)
                    }
                    else -> toast(R.string.error_capture)
                }
            }
        }
        if (!started) {
            runCatching { pfd?.close() }
            toast(R.string.error_capture)
        }
    }

    fun startVideo() {
        val wantAudio = currentSettings.recordAudio
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (wantAudio && !granted) requestAudio { ok -> record(ok) } else record(wantAudio)
    }

    fun onShutter() {
        if (countdown > 0) {
            countdownJob?.cancel(); countdown = 0
            return
        }
        if (mode == Mode.VIDEO && cam.recording) {
            controller.stopRecording()
            return
        }
        if (busy) return
        val action = { if (mode == Mode.VIDEO) startVideo() else shoot() }
        val seconds = currentSettings.timerSeconds
        if (seconds > 0) {
            countdownJob = scope.launch {
                for (s in seconds downTo 1) { countdown = s; delay(1000) }
                countdown = 0
                action()
            }
        } else {
            action()
        }
    }

    // Volume / camera keys as shutter while this screen is visible.
    DisposableEffect(Unit) {
        ShutterKeys.active = true
        onDispose { ShutterKeys.active = false }
    }
    LaunchedEffect(Unit) { ShutterKeys.events.collect { onShutter() } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Gesture layer + overlays aligned to the actual image area.
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .pointerInputs(
                    onZoom = { factor -> controller.setZoom(controller.state.value.zoom * factor) },
                    onTap = { off -> focusPoint = off; controller.focus(previewView, off.x, off.y) },
                    onDoubleTap = { if (!controller.state.value.recording && cam.hasFront && cam.hasBack) lens = if (lens == Lens.BACK) Lens.FRONT else Lens.BACK },
                ),
        ) {
            val density = LocalDensity.current
            val w = constraints.maxWidth.toFloat()
            val h = constraints.maxHeight.toFloat()
            val ar = if (settings.ratio == Ratio.R16_9) 16f / 9f else 4f / 3f
            val imgW = minOf(w, h / ar)
            val imgH = imgW * ar
            val left = (w - imgW) / 2
            val top = (h - imgH) / 2

            if (settings.grid) {
                Canvas(Modifier.fillMaxSize()) {
                    val line = Color(0x55FFFFFF)
                    for (k in 1..2) {
                        val x = left + imgW * k / 3
                        val y = top + imgH * k / 3
                        drawLine(line, Offset(x, top), Offset(x, top + imgH), strokeWidth = 1.dp.toPx())
                        drawLine(line, Offset(left, y), Offset(left + imgW, y), strokeWidth = 1.dp.toPx())
                    }
                }
            }
            focusPoint?.let { p ->
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(NothingWhite, radius = 34.dp.toPx(), center = p, style = Stroke(1.5.dp.toPx()))
                    drawCircle(NothingRed, radius = 3.dp.toPx(), center = p)
                }
            }
            if (flashOverlay) Box(Modifier.fillMaxSize().background(Color(0xAA000000)))
            if (countdown > 0) {
                DotMatrixText(
                    countdown.toString(),
                    dotSize = 14.dp,
                    gap = 4.dp,
                    accentLast = true,
                    modifier = Modifier.align(Alignment.Center).rotate(angle),
                )
            }
            with(density) {
                if (cam.recording) {
                    RecordingBadge(
                        ms = cam.recordedMs,
                        paused = cam.paused,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset { IntOffset(0, (top + 16.dp.toPx()).roundToInt()) },
                    )
                }
            }
        }

        val err = initError ?: cam.error
        if (err != null) {
            Text(
                stringResource(R.string.error_camera, err),
                color = NothingGrey,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
        }

        TopBar(
            settings = settings,
            mode = mode,
            hasFlash = cam.hasFlash,
            angle = angle,
            showVaultToggle = request == null,
            recording = cam.recording,
            onFlash = {
                store.update {
                    val next = if (mode == Mode.VIDEO) {
                        if (it.flash == Flash.ON) Flash.OFF else Flash.ON
                    } else {
                        Flash.entries[(it.flash.ordinal + 1) % Flash.entries.size]
                    }
                    it.copy(flash = next)
                }
            },
            onTimer = { store.update { it.copy(timerSeconds = when (it.timerSeconds) { 0 -> 3; 3 -> 10; else -> 0 }) } },
            onGrid = { store.update { it.copy(grid = !it.grid) } },
            onRatio = { store.update { it.copy(ratio = if (it.ratio == Ratio.R4_3) Ratio.R16_9 else Ratio.R4_3) } },
            onVaultToggle = {
                store.update {
                    val next = if (it.saveTarget == SaveTarget.VAULT) SaveTarget.GALLERY else SaveTarget.VAULT
                    toast(if (next == SaveTarget.VAULT) R.string.saving_to_vault else R.string.saving_to_gallery)
                    it.copy(saveTarget = next)
                }
            },
            onSettings = onOpenSettings,
        )

        BottomControls(
            modifier = Modifier.align(Alignment.BottomCenter),
            mode = mode,
            modes = if (request != null) listOf(mode) else buildList {
                if (Mode.NIGHT in cam.extensionModes) add(Mode.NIGHT)
                if (Mode.PORTRAIT in cam.extensionModes) add(Mode.PORTRAIT)
                add(Mode.PHOTO); add(Mode.VIDEO)
            },
            onMode = { if (!cam.recording && countdown == 0) mode = it },
            zoom = cam.zoom,
            minZoom = cam.minZoom,
            maxZoom = cam.maxZoom,
            onZoom = controller::setZoom,
            recording = cam.recording,
            paused = cam.paused,
            counting = countdown > 0,
            busy = busy,
            angle = angle,
            last = last,
            vaultTarget = settings.saveTarget == SaveTarget.VAULT && request == null,
            canFlip = cam.hasFront && cam.hasBack && !cam.recording,
            onShutter = ::onShutter,
            onPause = controller::togglePause,
            onFlip = { lens = if (lens == Lens.BACK) Lens.FRONT else Lens.BACK },
            onThumb = thumb@{
                if (request != null) return@thumb
                val l = last
                if (l != null && !l.inVault && l.uri != null) {
                    openInGallery(context, l.uri, l.isVideo)
                    return@thumb
                }
                if (l?.inVault == true || settings.saveTarget == SaveTarget.VAULT) onOpenVault()
            },
        )
    }
}

private fun openInGallery(context: Context, uri: Uri, video: Boolean) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, if (video) "video/mp4" else "image/jpeg")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_gallery_app, Toast.LENGTH_SHORT).show()
    }
}

private fun Modifier.pointerInputs(
    onZoom: (Float) -> Unit,
    onTap: (Offset) -> Unit,
    onDoubleTap: () -> Unit,
): Modifier = this
    .then(
        Modifier.pointerInput("zoom") {
            detectTransformGestures { _, _, zoom, _ -> if (zoom != 1f) onZoom(zoom) }
        },
    )
    .then(
        Modifier.pointerInput("tap") {
            detectTapGestures(onTap = onTap, onDoubleTap = { onDoubleTap() })
        },
    )

@Composable
private fun RecordingBadge(ms: Long, paused: Boolean, modifier: Modifier = Modifier) {
    val total = ms / 1000
    val text = String.format(Locale.ROOT, "%02d:%02d", total / 60, total % 60)
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x99000000))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (paused) NothingGrey else NothingRed))
        Spacer(Modifier.width(10.dp))
        DotMatrixText(text, dotSize = 2.5.dp, gap = 0.8.dp, monoDigits = true)
    }
}

@Composable
private fun TopBar(
    settings: Settings,
    mode: Mode,
    hasFlash: Boolean,
    angle: Float,
    showVaultToggle: Boolean,
    recording: Boolean,
    onFlash: () -> Unit,
    onTimer: () -> Unit,
    onGrid: () -> Unit,
    onRatio: () -> Unit,
    onVaultToggle: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasFlash) {
            val icon = when {
                mode == Mode.VIDEO -> if (settings.flash == Flash.ON) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff
                settings.flash == Flash.AUTO -> Icons.Outlined.FlashAuto
                settings.flash == Flash.ON -> Icons.Outlined.FlashOn
                else -> Icons.Outlined.FlashOff
            }
            GlassIconButton(icon, stringResource(R.string.flash), onFlash, rotation = angle, active = settings.flash != Flash.OFF)
        } else {
            Spacer(Modifier.size(44.dp))
        }
        GlassIconButton(
            when (settings.timerSeconds) { 3 -> Icons.Outlined.Timer3; 10 -> Icons.Outlined.Timer10; else -> Icons.Outlined.TimerOff },
            stringResource(R.string.timer),
            onTimer,
            rotation = angle,
            active = settings.timerSeconds > 0,
        )
        GlassIconButton(
            if (settings.grid) Icons.Outlined.GridOn else Icons.Outlined.GridOff,
            stringResource(R.string.grid),
            onGrid,
            rotation = angle,
            active = settings.grid,
        )
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0x66000000))
                .border(1.dp, Color(0x33FFFFFF), CircleShape)
                .clickable(enabled = !recording, role = Role.Button, onClick = onRatio),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (settings.ratio == Ratio.R16_9) "16:9" else "4:3",
                style = MaterialTheme.typography.labelSmall,
                color = NothingWhite,
                modifier = Modifier.rotate(angle),
            )
        }
        if (showVaultToggle) {
            val vault = settings.saveTarget == SaveTarget.VAULT
            GlassIconButton(
                if (vault) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                stringResource(if (vault) R.string.saving_to_vault else R.string.saving_to_gallery),
                onVaultToggle,
                rotation = angle,
                active = vault,
            )
        }
        GlassIconButton(Icons.Outlined.Settings, stringResource(R.string.settings), onSettings, rotation = angle)
    }
}

@Composable
private fun BottomControls(
    modifier: Modifier,
    mode: Mode,
    modes: List<Mode>,
    onMode: (Mode) -> Unit,
    zoom: Float,
    minZoom: Float,
    maxZoom: Float,
    onZoom: (Float) -> Unit,
    recording: Boolean,
    paused: Boolean,
    counting: Boolean,
    busy: Boolean,
    angle: Float,
    last: LastCapture?,
    vaultTarget: Boolean,
    canFlip: Boolean,
    onShutter: () -> Unit,
    onPause: () -> Unit,
    onFlip: () -> Unit,
    onThumb: () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ZoomChips(zoom, minZoom, maxZoom, angle, onZoom)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            modes.forEach { m ->
                val selected = m == mode
                Column(
                    Modifier.clickable(enabled = !recording, role = Role.Tab) { onMode(m) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(modeLabel(m)).uppercase(Locale.ROOT),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) NothingWhite else NothingGrey,
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.size(5.dp).clip(CircleShape).background(if (selected) NothingRed else Color.Transparent))
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: last capture / vault, or pause while recording.
            if (recording) {
                GlassIconButton(
                    if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                    stringResource(if (paused) R.string.resume else R.string.pause),
                    onPause,
                    rotation = angle,
                    size = 56,
                )
            } else {
                Thumb(last, vaultTarget, angle, onThumb)
            }

            Shutter(mode = mode, recording = recording, counting = counting, busy = busy, onClick = onShutter)

            if (canFlip) {
                GlassIconButton(Icons.Outlined.Cameraswitch, stringResource(R.string.switch_camera), onFlip, rotation = angle, size = 56)
            } else {
                Spacer(Modifier.size(56.dp))
            }
        }
    }
}

private fun modeLabel(mode: Mode) = when (mode) {
    Mode.NIGHT -> R.string.mode_night
    Mode.PORTRAIT -> R.string.mode_portrait
    Mode.PHOTO -> R.string.mode_photo
    Mode.VIDEO -> R.string.mode_video
}

@Composable
private fun Thumb(last: LastCapture?, vaultTarget: Boolean, angle: Float, onClick: () -> Unit) {
    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(Color(0x66000000))
            .border(1.dp, if (vaultTarget || last?.inVault == true) NothingRed else Color(0x33FFFFFF), CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val thumb = last?.thumb
        when {
            thumb != null -> Image(
                thumb,
                contentDescription = stringResource(R.string.last_capture),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().rotate(angle),
            )
            last?.isVideo == true -> Icon(Icons.Outlined.Videocam, stringResource(R.string.last_capture), tint = NothingWhite)
            vaultTarget || last?.inVault == true ->
                Icon(Icons.Outlined.Lock, stringResource(R.string.vault), tint = NothingRed, modifier = Modifier.rotate(angle))
            else -> Icon(Icons.Outlined.PhotoLibrary, stringResource(R.string.last_capture), tint = NothingGrey, modifier = Modifier.rotate(angle))
        }
    }
}

@Composable
private fun Shutter(mode: Mode, recording: Boolean, counting: Boolean, busy: Boolean, onClick: () -> Unit) {
    val video = mode == Mode.VIDEO
    Box(
        Modifier
            .size(78.dp)
            .clip(CircleShape)
            .border(3.dp, NothingWhite, CircleShape)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.shutter), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val inner = when {
            recording -> Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(NothingRed)
            counting -> Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(NothingWhite)
            video -> Modifier.size(60.dp).clip(CircleShape).background(NothingRed)
            busy -> Modifier.size(52.dp).clip(CircleShape).background(NothingGrey)
            else -> Modifier.size(62.dp).clip(CircleShape).background(NothingWhite)
        }
        Box(inner)
    }
}

@Composable
private fun ZoomChips(zoom: Float, minZoom: Float, maxZoom: Float, angle: Float, onZoom: (Float) -> Unit) {
    if (maxZoom <= 1.01f && minZoom >= 0.99f) return
    val presets = buildList {
        if (minZoom < 0.95f) add(minZoom)
        add(1f)
        if (maxZoom >= 2f) add(2f)
        if (maxZoom >= 5f) add(5f)
    }
    val active = presets.lastOrNull { zoom + 0.01f >= it } ?: presets.first()
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color(0x66000000)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        presets.forEach { p ->
            val selected = p == active
            val label = if (selected) formatZoom(zoom) else formatZoom(p).removeSuffix("×")
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (selected) Color(0x33FFFFFF) else Color.Transparent)
                    .clickable(role = Role.Button) { onZoom(p) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) NothingRed else NothingWhite,
                    modifier = Modifier.rotate(angle),
                )
            }
        }
    }
}

private fun formatZoom(z: Float): String {
    val rounded = (z * 10).roundToInt() / 10f
    val text = if (rounded % 1f == 0f) rounded.toInt().toString() else String.format(Locale.ROOT, "%.1f", rounded)
    return "$text×"
}

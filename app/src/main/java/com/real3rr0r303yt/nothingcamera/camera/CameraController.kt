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

package com.real3rr0r303yt.nothingcamera.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Range
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileDescriptorOutputOptions
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import com.real3rr0r303yt.nothingcamera.data.Flash
import com.real3rr0r303yt.nothingcamera.data.Ratio
import com.real3rr0r303yt.nothingcamera.data.VideoQuality
import com.real3rr0r303yt.nothingcamera.privacy.MediaSaver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class Mode { NIGHT, PORTRAIT, PHOTO, VIDEO }
enum class Lens { BACK, FRONT }

data class CameraState(
    val ready: Boolean = false,
    val hasBack: Boolean = true,
    val hasFront: Boolean = true,
    val hasFlash: Boolean = false,
    val minZoom: Float = 1f,
    /** Upper limit incl. digital crop zoom (up to [MAX_PHOTO_ZOOM] for photos). */
    val maxZoom: Float = 1f,
    /** What the camera driver itself can do (optical + its own digital zoom). */
    val halMaxZoom: Float = 1f,
    val zoom: Float = 1f,
    /** Extra crop factor on top of the driver's zoom (1 = none). Photos only. */
    val digital: Float = 1f,
    /** Frame rate the current video session really uses. */
    val fps: Int = 30,
    /** True when 60 fps was asked for but the camera can't do it at this quality. */
    val fpsFallback: Boolean = false,
    val extensionModes: Set<Mode> = emptySet(),
    val recording: Boolean = false,
    val paused: Boolean = false,
    val recordedMs: Long = 0,
    val error: String? = null,
)

data class BindConfig(
    val mode: Mode,
    val lens: Lens,
    val ratio: Ratio,
    val quality: VideoQuality,
    val flash: Flash,
    val rotation: Int,
    val fps: Int = 30,
)

/** Total zoom offered for photos; beyond the driver's limit the centre is cropped. */
const val MAX_PHOTO_ZOOM = 40f

/** Where a video recording goes. */
sealed interface VideoTarget {
    data class Gallery(val name: String) : VideoTarget
    data class PlainFile(val file: File) : VideoTarget
    data class Descriptor(val pfd: ParcelFileDescriptor, val durationLimitMs: Long) : VideoTarget
}

/** Thin CameraX wrapper. Never attaches location to anything it writes. */
class CameraController(private val context: Context) {

    private val main = ContextCompat.getMainExecutor(context)
    private val io = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null
    private var extensions: ExtensionsManager? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var bound: BindConfig? = null

    private val _state = MutableStateFlow(CameraState())
    val state: StateFlow<CameraState> = _state.asStateFlow()

    suspend fun init() {
        if (provider != null) return
        val p = ProcessCameraProvider.getInstance(context).await()
        provider = p
        extensions = runCatching { ExtensionsManager.getInstanceAsync(context, p).await() }.getOrNull()
        _state.update {
            it.copy(
                ready = true,
                hasBack = has(p, CameraSelector.DEFAULT_BACK_CAMERA),
                hasFront = has(p, CameraSelector.DEFAULT_FRONT_CAMERA),
            )
        }
    }

    private fun has(p: ProcessCameraProvider, selector: CameraSelector) =
        runCatching { p.hasCamera(selector) }.getOrDefault(false)

    private fun baseSelector(lens: Lens) =
        if (lens == Lens.FRONT) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

    private fun extensionModesFor(lens: Lens): Set<Mode> {
        val ext = extensions ?: return emptySet()
        val base = baseSelector(lens)
        return buildSet {
            if (runCatching { ext.isExtensionAvailable(base, ExtensionMode.NIGHT) }.getOrDefault(false)) add(Mode.NIGHT)
            if (runCatching { ext.isExtensionAvailable(base, ExtensionMode.BOKEH) }.getOrDefault(false)) add(Mode.PORTRAIT)
        }
    }

    fun bind(owner: LifecycleOwner, previewView: PreviewView, requested: BindConfig, isFallback: Boolean = false) {
        val p = provider ?: return
        if (recording != null) return
        val available = extensionModesFor(requested.lens)
        val mode = if (requested.mode in setOf(Mode.NIGHT, Mode.PORTRAIT) && requested.mode !in available) Mode.PHOTO else requested.mode
        // 60 fps only if the sensor offers a 60 fps range at all; otherwise fall back right away.
        val cfg = if (mode == Mode.VIDEO && requested.fps > 30 && !supportsFps(p, requested.lens, requested.fps)) {
            return bind(owner, previewView, requested.copy(fps = 30), isFallback = true)
        } else {
            requested
        }

        val ratioStrategy = if (cfg.ratio == Ratio.R16_9) {
            AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
        } else {
            AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
        }
        val preview = Preview.Builder()
            .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(ratioStrategy).build())
            .build()
        preview.setSurfaceProvider(previewView.surfaceProvider)

        var selector = baseSelector(cfg.lens)
        val ext = extensions
        if (ext != null && mode == Mode.NIGHT) selector = ext.getExtensionEnabledCameraSelector(selector, ExtensionMode.NIGHT)
        if (ext != null && mode == Mode.PORTRAIT) selector = ext.getExtensionEnabledCameraSelector(selector, ExtensionMode.BOKEH)

        val useCase = if (mode == Mode.VIDEO) {
            imageCapture = null
            val quality = when (cfg.quality) {
                VideoQuality.HD -> Quality.HD
                VideoQuality.FHD -> Quality.FHD
                VideoQuality.UHD -> Quality.UHD
            }
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(quality, FallbackStrategy.lowerQualityOrHigherThan(quality)))
                .setAspectRatio(if (cfg.ratio == Ratio.R16_9) AspectRatio.RATIO_16_9 else AspectRatio.RATIO_4_3)
                .build()
            val builder = VideoCapture.Builder(recorder).setTargetRotation(cfg.rotation)
            if (cfg.fps > 30) builder.setTargetFrameRate(Range(cfg.fps, cfg.fps))
            builder.build().also { videoCapture = it }
        } else {
            videoCapture = null
            ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setFlashMode(flashMode(cfg.flash))
                .setJpegQuality(95)
                .setTargetRotation(cfg.rotation)
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(ratioStrategy)
                        .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                        .build(),
                )
                .build()
                .also { imageCapture = it }
        }

        try {
            p.unbindAll()
            val cam = p.bindToLifecycle(owner, selector, preview, useCase)
            camera = cam
            bound = cfg.copy(mode = mode)
            val zs = cam.cameraInfo.zoomState.value
            if (mode == Mode.VIDEO) cam.cameraControl.enableTorch(cfg.flash == Flash.ON)
            val halMax = zs?.maxZoomRatio ?: 1f
            _state.update {
                it.copy(
                    hasFlash = cam.cameraInfo.hasFlashUnit(),
                    minZoom = zs?.minZoomRatio ?: 1f,
                    halMaxZoom = halMax,
                    // Video can't be cropped after the fact, so it stays at the driver's limit.
                    maxZoom = if (mode == Mode.VIDEO) halMax else maxOf(halMax, MAX_PHOTO_ZOOM),
                    zoom = zs?.zoomRatio ?: 1f,
                    digital = 1f,
                    fps = if (mode == Mode.VIDEO) cfg.fps else 30,
                    fpsFallback = isFallback,
                    extensionModes = available,
                    error = null,
                )
            }
        } catch (e: Exception) {
            if (mode == Mode.VIDEO && cfg.fps > 30) {
                bind(owner, previewView, cfg.copy(fps = 30), isFallback = true)
            } else {
                _state.update { it.copy(error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun setFlash(flash: Flash) {
        imageCapture?.flashMode = flashMode(flash)
        if (bound?.mode == Mode.VIDEO) camera?.cameraControl?.enableTorch(flash == Flash.ON)
    }

    fun setRotation(rotation: Int) {
        imageCapture?.targetRotation = rotation
        if (recording == null) videoCapture?.targetRotation = rotation
    }

    /** Total zoom: the driver handles what it can, the rest is a centre crop (photos only). */
    fun setZoom(ratio: Float) {
        val s = _state.value
        val total = ratio.coerceIn(s.minZoom, s.maxZoom)
        val hal = minOf(total, s.halMaxZoom)
        camera?.cameraControl?.setZoomRatio(hal)
        _state.update { it.copy(zoom = total, digital = (total / hal).coerceAtLeast(1f)) }
    }

    private fun supportsFps(p: ProcessCameraProvider, lens: Lens, fps: Int): Boolean {
        val info = runCatching { baseSelector(lens).filter(p.availableCameraInfos).firstOrNull() }.getOrNull()
            ?: return false
        return info.supportedFrameRateRanges.any { it.upper >= fps && it.lower <= fps }
    }

    fun focus(previewView: PreviewView, x: Float, y: Float) {
        val point = previewView.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
        camera?.cameraControl?.startFocusAndMetering(action)
    }

    /** Captures a JPEG **in memory** – nothing touches the disk before metadata is stripped. */
    suspend fun takePhoto(): Pair<ByteArray, Int> = suspendCancellableCoroutine { cont ->
        val capture = imageCapture ?: return@suspendCancellableCoroutine cont.resumeWithException(IllegalStateException("Not in photo mode"))
        capture.takePicture(io, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    if (image.format != ImageFormat.JPEG) throw IllegalStateException("Unexpected format ${image.format}")
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
                    cont.resume(bytes to image.imageInfo.rotationDegrees)
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                } finally {
                    image.close()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                cont.resumeWithException(exception)
            }
        })
    }

    /**
     * Starts a recording. [onFinished] gets the output URI (null for [VideoTarget.PlainFile] targets
     * on failure) once the file is complete.
     */
    @SuppressLint("MissingPermission") // caller checks RECORD_AUDIO before passing audio = true
    fun startRecording(target: VideoTarget, audio: Boolean, onFinished: (ok: Boolean, uri: Uri?) -> Unit): Boolean {
        val recorder = videoCapture?.output ?: return false
        if (recording != null) return false
        val pending: PendingRecording = when (target) {
            is VideoTarget.Gallery -> recorder.prepareRecording(
                context,
                MediaStoreOutputOptions.Builder(context.contentResolver, MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY))
                    .setContentValues(MediaSaver.videoValues(target.name))
                    .build(),
            )
            is VideoTarget.PlainFile -> recorder.prepareRecording(context, FileOutputOptions.Builder(target.file).build())
            is VideoTarget.Descriptor -> recorder.prepareRecording(
                context,
                FileDescriptorOutputOptions.Builder(target.pfd)
                    .apply { if (target.durationLimitMs > 0) setDurationLimitMillis(target.durationLimitMs) }
                    .build(),
            )
        }
        if (audio) pending.withAudioEnabled()
        recording = pending.start(main) { event ->
            when (event) {
                is VideoRecordEvent.Start -> _state.update { it.copy(recording = true, paused = false, recordedMs = 0) }
                is VideoRecordEvent.Pause -> _state.update { it.copy(paused = true) }
                is VideoRecordEvent.Resume -> _state.update { it.copy(paused = false) }
                is VideoRecordEvent.Status ->
                    _state.update { it.copy(recordedMs = event.recordingStats.recordedDurationNanos / 1_000_000) }
                is VideoRecordEvent.Finalize -> {
                    recording = null
                    _state.update { it.copy(recording = false, paused = false) }
                    val ok = event.error in setOf(
                        VideoRecordEvent.Finalize.ERROR_NONE,
                        VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED,
                        VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED,
                        VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE,
                    )
                    val uri = event.outputResults.outputUri.takeIf { it != Uri.EMPTY }
                    onFinished(ok, uri)
                }
            }
        }
        return true
    }

    fun stopRecording() {
        recording?.stop()
    }

    fun togglePause() {
        val r = recording ?: return
        if (_state.value.paused) r.resume() else r.pause()
    }

    fun release() {
        recording?.stop()
        recording = null
        provider?.unbindAll()
        io.shutdown()
    }

    private fun flashMode(flash: Flash) = when (flash) {
        Flash.OFF -> ImageCapture.FLASH_MODE_OFF
        Flash.AUTO -> ImageCapture.FLASH_MODE_AUTO
        Flash.ON -> ImageCapture.FLASH_MODE_ON
    }

    private suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { cont ->
        addListener({
            try {
                cont.resume(get())
            } catch (e: ExecutionException) {
                cont.resumeWithException(e.cause ?: e)
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        }, main)
    }
}

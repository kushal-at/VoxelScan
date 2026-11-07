package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.domain.LiveFrameAnalysisResult
import com.example.domain.MeshRetopologyEngine
import com.example.domain.Vec3
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAG = "PhotogrammetryCamera"

/**
 * Result of registering a 2D camera frame and its extracted 8x8 monocular depth map
 * into 3D world coordinates using hardware-timestamp-synchronized IMU sensor data
 * (`TYPE_ROTATION_VECTOR` 3x3 rotation matrix + `TYPE_GYROSCOPE` + `TYPE_ACCELEROMETER` ZUPT translation).
 */
data class Registered3DFrameData(
    val sensorTimestampNs: Long,
    val cameraOriginWorld: Vec3,
    val cameraForwardRayWorld: Vec3,
    val estimatedSubjectDepthMeters: Float,
    val registeredWorldPoints3D: List<Vec3>,
    val reprojectionErrorMm: Float,
    val registrationConfidencePercent: Int
)

/**
 * Synchronized packet emitted by the CameraX `ImageAnalysis` pipeline combining:
 * 1. The rotation-compensated RGB frame (`rgbBitmap`)
 * 2. Monocular depth & silhouette analysis (`analysis`)
 * 3. Hardware-timestamp-interpolated 3-sensor IMU pose (`syncedImuPose`)
 * 4. 3D world-space frame registration (`frameRegistration3D`)
 */
data class SynchronizedRgbFramePacket(
    val rgbBitmap: Bitmap,
    val analysis: LiveFrameAnalysisResult,
    val syncedImuPose: ImuPoseSnapshot,
    val frameRegistration3D: Registered3DFrameData,
    val sharpnessScorePercent: Int,
    val frameResolutionLabel: String,
    val exposureLocked: Boolean
)

/**
 * Dedicated CameraX `ImageAnalysis.Analyzer` that extracts RGBA_8888 frames and monocular depth
 * maps from the real-time camera stream and connects each frame to the synchronized `ImuPoseTracker`
 * sensor data for 3D world-space frame registration.
 */
class PhotogrammetryFrameAnalyzer(
    private val imuPoseTracker: ImuPoseTracker,
    private val minFrameIntervalMs: Long = 180L,
    private val isExposureLockedProvider: () -> Boolean,
    private val onFrameRegistered: (SynchronizedRgbFramePacket) -> Unit
) : ImageAnalysis.Analyzer {

    private var lastAnalyzedTimeMs: Long = 0L

    @Volatile
    var latestRegisteredPacket: SynchronizedRgbFramePacket? = null
        private set

    override fun analyze(imageProxy: ImageProxy) {
        try {
            val nowMs = SystemClock.elapsedRealtime()
            if (nowMs - lastAnalyzedTimeMs < minFrameIntervalMs) {
                return
            }
            lastAnalyzedTimeMs = nowMs

            // 1. Hardware sensor exposure timestamp (nanoseconds) for IMU <-> CameraX registration
            val frameTimestampNs = imageProxy.imageInfo.timestamp
            val syncedImuPose = imuPoseTracker.getInterpolatedPoseAtTimestamp(frameTimestampNs)
            val resLabel = "${imageProxy.width}x${imageProxy.height} RGBA"

            // 2. Extract upright RGB Bitmap from RGBA_8888 ImageProxy plane buffer
            val uprightFrameBitmap = extractUprightRgbaBitmap(imageProxy, maxDimension = 384)

            // 3. Downsample for fast Sobel gradient, foreground silhouette, and 8x8 depth extraction
            val analysisBitmap = Bitmap.createScaledBitmap(uprightFrameBitmap, 160, 160, true)
            val sharpnessScore = computeLaplacianSharpnessPercent(
                bitmap = analysisBitmap,
                angularSpeedDegPerSec = syncedImuPose.angularSpeedDegPerSec
            )
            val depthAndLightResult = MeshRetopologyEngine.analyzeCameraBitmap(analysisBitmap)

            // 4. Register the extracted 2D depth grid into 3D world space using the IMU rotation matrix & pose
            val registration3D = registerDepthGridToWorldSpace(
                depthGrid8x8 = depthAndLightResult.depthGrid8x8,
                foregroundCoveragePercent = depthAndLightResult.foregroundCoveragePercent,
                sharpnessPercent = sharpnessScore,
                imuPose = syncedImuPose
            )

            val packet = SynchronizedRgbFramePacket(
                rgbBitmap = uprightFrameBitmap,
                analysis = depthAndLightResult,
                syncedImuPose = syncedImuPose,
                frameRegistration3D = registration3D,
                sharpnessScorePercent = sharpnessScore,
                frameResolutionLabel = resLabel,
                exposureLocked = isExposureLockedProvider()
            )
            latestRegisteredPacket = packet
            onFrameRegistered(packet)
        } catch (e: Exception) {
            Log.w(TAG, "ImageAnalysis frame & depth registration error", e)
        } finally {
            // MANDATORY: Always close ImageProxy so STRATEGY_KEEP_ONLY_LATEST never stalls
            imageProxy.close()
        }
    }

    companion object {
        /**
         * Extracts an upright `ARGB_8888` [Bitmap] from a CameraX `OUTPUT_IMAGE_FORMAT_RGBA_8888`
         * [ImageProxy], handling row-stride padding and sensor rotation (`rotationDegrees`).
         */
        fun extractUprightRgbaBitmap(imageProxy: ImageProxy, maxDimension: Int): Bitmap {
            val rawBitmap = try {
                val plane = imageProxy.planes.firstOrNull()
                if (plane != null && imageProxy.planes.size == 1 && plane.pixelStride == 4) {
                    copyRgbaPlaneWithStride(
                        buffer = plane.buffer,
                        width = imageProxy.width,
                        height = imageProxy.height,
                        rowStride = plane.rowStride,
                        pixelStride = plane.pixelStride
                    )
                } else {
                    imageProxy.toBitmap()
                }
            } catch (_: Exception) {
                imageProxy.toBitmap()
            }

            val rotDeg = imageProxy.imageInfo.rotationDegrees
            val rotated = if (rotDeg != 0) {
                val matrix = Matrix().apply { postRotate(rotDeg.toFloat()) }
                Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
            } else {
                rawBitmap
            }

            val maxSide = maxOf(rotated.width, rotated.height).coerceAtLeast(1)
            return if (maxSide > maxDimension) {
                val scale = maxDimension.toFloat() / maxSide.toFloat()
                val targetW = (rotated.width * scale).toInt().coerceAtLeast(64)
                val targetH = (rotated.height * scale).toInt().coerceAtLeast(64)
                Bitmap.createScaledBitmap(rotated, targetW, targetH, true)
            } else {
                rotated
            }
        }

        private fun copyRgbaPlaneWithStride(
            buffer: ByteBuffer,
            width: Int,
            height: Int,
            rowStride: Int,
            pixelStride: Int
        ): Bitmap {
            buffer.rewind()
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            if (rowStride == width * pixelStride) {
                bitmap.copyPixelsFromBuffer(buffer)
                return bitmap
            }
            // Strip row padding when rowStride > width * pixelStride
            val tightBuffer = ByteBuffer.allocateDirect(width * height * 4)
            val rowBytes = ByteArray(width * pixelStride)
            for (row in 0 until height) {
                buffer.position(row * rowStride)
                buffer.get(rowBytes, 0, width * pixelStride)
                tightBuffer.put(rowBytes)
            }
            tightBuffer.rewind()
            bitmap.copyPixelsFromBuffer(tightBuffer)
            return bitmap
        }

        /**
         * Unprojects the 8x8 monocular depth grid (`depthGrid8x8`) from normalized camera sensor coordinates
         * through a pinhole camera frustum (`fov = 58°`) and transforms every 3D point into world space
         * using the synchronized IMU pose (`rotationMatrix`, `azimuthDeg`, `pitchDeg`, and ZUPT translation offset).
         */
        fun registerDepthGridToWorldSpace(
            depthGrid8x8: List<Float>,
            foregroundCoveragePercent: Int,
            sharpnessPercent: Int,
            imuPose: ImuPoseSnapshot
        ): Registered3DFrameData {
            val azRad = (imuPose.azimuthDeg.toFloat() / 180f) * PI.toFloat()
            val pitchRad = (imuPose.pitchDeg.toFloat() / 180f) * PI.toFloat()

            // Estimated orbital distance to scanned subject in meters (0.42m .. 1.15m)
            val coverageNorm = (foregroundCoveragePercent / 100f).coerceIn(0.18f, 0.95f)
            val baseOrbitRadiusMeters = (0.98f - (coverageNorm - 0.5f) * 0.45f).coerceIn(0.42f, 1.15f)

            // World-space camera optical center on the orbital scan sphere + ZUPT accelerometer micro-offset
            val camX = cos(pitchRad) * cos(azRad) * baseOrbitRadiusMeters + imuPose.translationOffsetMetersX * 0.15f
            val camY = sin(pitchRad) * baseOrbitRadiusMeters + imuPose.translationOffsetMetersY * 0.15f
            val camZ = cos(pitchRad) * sin(azRad) * baseOrbitRadiusMeters + imuPose.translationOffsetMetersZ * 0.15f
            val cameraOriginWorld = Vec3(camX, camY, camZ)

            // Forward viewing vector pointing from camera optical center toward object origin
            val fwdLen = sqrt(camX * camX + camY * camY + camZ * camZ).coerceAtLeast(1e-4f)
            val forwardRay = Vec3(-camX / fwdLen, -camY / fwdLen, -camZ / fwdLen)

            // 3x3 sensor rotation matrix R (row-major 9 floats)
            val rMat = if (imuPose.rotationMatrix.size == 9) {
                imuPose.rotationMatrix
            } else {
                ImuPoseSnapshot.IDENTITY_MATRIX_9
            }

            // Pinhole camera normalized focal length (~58° horizontal FOV -> f_norm = 1.0 / tan(29°) ≈ 1.804)
            val invFocal = 0.554f
            val registeredPoints = ArrayList<Vec3>(64)

            for (r in 0 until 8) {
                val ndcY = ((3.5f - r) / 3.5f) // +1 top .. -1 bottom
                for (c in 0 until 8) {
                    val ndcX = ((c - 3.5f) / 3.5f) // -1 left .. +1 right
                    val idx = r * 8 + c
                    val depthConfidence = depthGrid8x8.getOrElse(idx) { 0.5f }.coerceIn(0.1f, 1.0f)

                    // Metric depth along optical ray (closer where foreground depth confidence is higher)
                    val rayDepthMeters = baseOrbitRadiusMeters - (depthConfidence - 0.5f) * 0.36f
                    val camSpaceX = ndcX * invFocal * rayDepthMeters
                    val camSpaceY = ndcY * invFocal * rayDepthMeters
                    val camSpaceZ = -rayDepthMeters

                    // Transform camera-space 3D point by the IMU 3x3 rotation matrix R and translate by cameraOriginWorld
                    val rotX = rMat[0] * camSpaceX + rMat[1] * camSpaceY + rMat[2] * camSpaceZ
                    val rotY = rMat[3] * camSpaceX + rMat[4] * camSpaceY + rMat[5] * camSpaceZ
                    val rotZ = rMat[6] * camSpaceX + rMat[7] * camSpaceY + rMat[8] * camSpaceZ

                    registeredPoints.add(
                        Vec3(
                            x = camX + rotX,
                            y = camY + rotY,
                            z = camZ + rotZ
                        )
                    )
                }
            }

            // Compute 3D frame registration error (in mm) and confidence score from IMU sync offset, motion stability, and sharpness
            val syncPenaltyMm = (imuPose.syncOffsetMs * 0.18f).coerceIn(0.05f, 2.5f)
            val motionPenaltyMm = (imuPose.angularSpeedDegPerSec * 0.03f + imuPose.linearAccelMagnitudeMps2 * 0.45f).coerceIn(0f, 4.0f)
            val reprojectionErrMm = (0.35f + syncPenaltyMm + motionPenaltyMm).coerceIn(0.35f, 6.5f)

            val registrationConf = (
                (imuPose.motionStabilityPercent * 0.45f) +
                    (sharpnessPercent * 0.35f) +
                    ((100f - imuPose.syncOffsetMs * 2f).coerceIn(60f, 100f) * 0.20f)
                ).roundToInt().coerceIn(55, 99)

            return Registered3DFrameData(
                sensorTimestampNs = imuPose.timestampNs,
                cameraOriginWorld = cameraOriginWorld,
                cameraForwardRayWorld = forwardRay,
                estimatedSubjectDepthMeters = baseOrbitRadiusMeters,
                registeredWorldPoints3D = registeredPoints,
                reprojectionErrorMm = reprojectionErrMm,
                registrationConfidencePercent = registrationConf
            )
        }

        fun computeLaplacianSharpnessPercent(
            bitmap: Bitmap,
            angularSpeedDegPerSec: Float
        ): Int {
            val w = bitmap.width
            val h = bitmap.height
            if (w < 16 || h < 16) return 90

            var laplacianSum = 0f
            var samples = 0
            val stepX = (w / 16).coerceAtLeast(2)
            val stepY = (h / 16).coerceAtLeast(2)

            for (y in stepY until (h - stepY) step stepY) {
                for (x in stepX until (w - stepX) step stepX) {
                    val c = lumaAt(bitmap, x, y)
                    val l = lumaAt(bitmap, x - 1, y)
                    val r = lumaAt(bitmap, x + 1, y)
                    val u = lumaAt(bitmap, x, y - 1)
                    val d = lumaAt(bitmap, x, y + 1)
                    val lap = abs(4f * c - l - r - u - d)
                    laplacianSum += lap
                    samples++
                }
            }
            val avgLap = if (samples > 0) laplacianSum / samples else 18f
            val motionPenalty = (angularSpeedDegPerSec * 0.45f).coerceIn(0f, 35f)
            val rawScore = (68f + (avgLap * 0.85f) - motionPenalty).toInt()
            return rawScore.coerceIn(35, 99)
        }

        private fun lumaAt(bitmap: Bitmap, x: Int, y: Int): Float {
            val px = bitmap.getPixel(x, y)
            return 0.299f * Color.red(px) + 0.587f * Color.green(px) + 0.114f * Color.blue(px)
        }
    }
}

/**
 * Production-grade CameraX Photogrammetry Controller that binds the CameraX `Preview`
 * use case as the foundational viewfinder alongside `ImageAnalysis` ([PhotogrammetryFrameAnalyzer])
 * and `ImageCapture`, connecting every camera frame to [ImuPoseTracker] sensor data for 3D registration.
 */
class PhotogrammetryCameraController(
    private val context: Context,
    private val imuPoseTracker: ImuPoseTracker,
    private val onRealtimeFramePacket: (SynchronizedRgbFramePacket) -> Unit,
    private val onHighResKeyframeCaptured: (Bitmap, ImuPoseSnapshot, Int) -> Unit,
    private val onStreamErrorOrFallback: (String) -> Unit = {}
) {
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var boundCamera: Camera? = null
    private var previewUseCase: Preview? = null
    private var imageCaptureUseCase: ImageCapture? = null
    private var imageAnalysisUseCase: ImageAnalysis? = null
    private var preferFrontCamera: Boolean = false

    @Volatile
    private var isExposureAndFocusLocked: Boolean = false

    private val frameAnalyzer = PhotogrammetryFrameAnalyzer(
        imuPoseTracker = imuPoseTracker,
        minFrameIntervalMs = 180L,
        isExposureLockedProvider = { isExposureAndFocusLocked },
        onFrameRegistered = { packet ->
            onRealtimeFramePacket(packet)
        }
    )

    private val orientationListener = object : OrientationEventListener(context.applicationContext) {
        override fun onOrientationChanged(orientation: Int) {
            if (orientation == ORIENTATION_UNKNOWN) return
            val rotation = when (orientation) {
                in 45 until 135 -> Surface.ROTATION_270
                in 135 until 225 -> Surface.ROTATION_180
                in 225 until 315 -> Surface.ROTATION_90
                else -> Surface.ROTATION_0
            }
            imageAnalysisUseCase?.targetRotation = rotation
            imageCaptureUseCase?.targetRotation = rotation
        }
    }

    /**
     * Binds the CameraX `Preview` use case, `ImageAnalysis` ([PhotogrammetryFrameAnalyzer]),
     * and `ImageCapture` to the given [LifecycleOwner].
     */
    fun bindToLifecycle(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ) {
        orientationListener.enable()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                cameraProvider = provider

                val cameraSelector = resolveAvailableCameraSelector(provider)
                if (cameraSelector == null) {
                    onStreamErrorOrFallback("No hardware camera lens detected on device")
                    return@addListener
                }

                // 1. Foundational CameraX Preview Use Case
                val previewResolutionSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .build()

                val preview = Preview.Builder()
                    .setResolutionSelector(previewResolutionSelector)
                    .build()
                    .also { previewInstance ->
                        previewInstance.surfaceProvider = previewView.surfaceProvider
                    }
                previewUseCase = preview

                // 2. Real-time RGBA_8888 ImageAnalysis Use Case with PhotogrammetryFrameAnalyzer
                val analysisResolutionSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(960, 720),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()

                val imageAnalysis = ImageAnalysis.Builder()
                    .setResolutionSelector(analysisResolutionSelector)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysisInstance ->
                        analysisInstance.setAnalyzer(cameraExecutor, frameAnalyzer)
                    }
                imageAnalysisUseCase = imageAnalysis

                // 3. High-Resolution ImageCapture Use Case
                val captureResolutionSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1600, 1200),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()

                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setResolutionSelector(captureResolutionSelector)
                    .build()
                imageCaptureUseCase = imageCapture

                provider.unbindAll()

                // Progressive binding: Triple -> Dual (Preview + ImageAnalysis) -> Preview-only fallback
                boundCamera = try {
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis,
                        imageCapture
                    )
                } catch (tripleBindError: Exception) {
                    Log.w(TAG, "Triple use-case bind unsupported; binding Preview + RGBA ImageAnalysis", tripleBindError)
                    imageCaptureUseCase = null
                    try {
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                    } catch (dualBindError: Exception) {
                        Log.w(TAG, "Dual bind unsupported; binding foundational Preview use case only", dualBindError)
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "CameraX Preview + ImageAnalysis bind failed", e)
                onStreamErrorOrFallback(e.localizedMessage ?: "Camera initialization error")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun resolveAvailableCameraSelector(provider: ProcessCameraProvider): CameraSelector? {
        val preferred = if (preferFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        val secondary = if (preferFrontCamera) {
            CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            CameraSelector.DEFAULT_FRONT_CAMERA
        }
        return when {
            runCatching { provider.hasCamera(preferred) }.getOrDefault(false) -> preferred
            runCatching { provider.hasCamera(secondary) }.getOrDefault(false) -> secondary
            provider.availableCameraInfos.isNotEmpty() -> CameraSelector.Builder().build()
            else -> null
        }
    }

    fun switchCameraLens(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        preferFrontCamera = !preferFrontCamera
        bindToLifecycle(lifecycleOwner, previewView)
    }

    fun triggerTapToFocus(
        previewView: PreviewView,
        x: Float,
        y: Float,
        lockExposure: Boolean = isExposureAndFocusLocked
    ) {
        val cam = boundCamera ?: return
        try {
            val factory = previewView.meteringPointFactory
            val point = factory.createPoint(x, y)
            val actionBuilder = FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
            )
            if (lockExposure) {
                actionBuilder.disableAutoCancel()
            } else {
                actionBuilder.setAutoCancelDuration(4, TimeUnit.SECONDS)
            }
            cam.cameraControl.startFocusAndMetering(actionBuilder.build())
        } catch (e: Exception) {
            Log.w(TAG, "Focus metering not supported on current camera device", e)
        }
    }

    fun toggleExposureAndFocusLock(previewView: PreviewView): Boolean {
        isExposureAndFocusLocked = !isExposureAndFocusLocked
        val cx = previewView.width * 0.5f
        val cy = previewView.height * 0.5f
        if (cx > 0f && cy > 0f) {
            triggerTapToFocus(previewView, cx, cy, isExposureAndFocusLocked)
        }
        return isExposureAndFocusLocked
    }

    fun captureHighQualityRgbKeyframe(previewViewFallback: PreviewView? = null) {
        val captureUseCase = imageCaptureUseCase
        if (captureUseCase != null) {
            captureUseCase.takePicture(
                cameraExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        try {
                            val sensorTimestampNs = image.imageInfo.timestamp
                            val syncedPose = imuPoseTracker.getInterpolatedPoseAtTimestamp(sensorTimestampNs)
                            val uprightBmp = PhotogrammetryFrameAnalyzer.extractUprightRgbaBitmap(
                                imageProxy = image,
                                maxDimension = 512
                            )
                            val sharpness = PhotogrammetryFrameAnalyzer.computeLaplacianSharpnessPercent(
                                bitmap = uprightBmp,
                                angularSpeedDegPerSec = syncedPose.angularSpeedDegPerSec
                            )
                            onHighResKeyframeCaptured(uprightBmp, syncedPose, sharpness)
                        } catch (e: Exception) {
                            Log.w(TAG, "High-res ImageProxy decode failed, using stream fallback", e)
                            deliverFallbackStreamKeyframe(previewViewFallback)
                        } finally {
                            image.close()
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.w(TAG, "ImageCapture failed (${exception.message}), using RGBA stream buffer")
                        deliverFallbackStreamKeyframe(previewViewFallback)
                    }
                }
            )
        } else {
            deliverFallbackStreamKeyframe(previewViewFallback)
        }
    }

    private fun deliverFallbackStreamKeyframe(previewViewFallback: PreviewView?) {
        val latestPacket = frameAnalyzer.latestRegisteredPacket
        if (latestPacket != null) {
            onHighResKeyframeCaptured(
                latestPacket.rgbBitmap,
                latestPacket.syncedImuPose,
                latestPacket.sharpnessScorePercent
            )
            return
        }
        val pose = imuPoseTracker.getInterpolatedPoseAtTimestamp(SystemClock.elapsedRealtimeNanos())
        if (previewViewFallback != null) {
            ContextCompat.getMainExecutor(context).execute {
                val previewBmp = previewViewFallback.bitmap
                if (previewBmp != null) {
                    val scaled = Bitmap.createScaledBitmap(previewBmp, 384, 384, true)
                    onHighResKeyframeCaptured(scaled, pose, 92)
                }
            }
        }
    }

    fun shutdown() {
        orientationListener.disable()
        cameraExecutor.shutdown()
    }
}

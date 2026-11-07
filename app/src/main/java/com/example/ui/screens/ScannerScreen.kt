package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Flare
import androidx.compose.material.icons.rounded.Grid4x4
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.domain.MeshRetopologyEngine
import com.example.ui.components.CameraXPreviewSurface
import com.example.ui.components.CircularHudIconButton
import com.example.ui.components.CircularScanProgressOverlay
import com.example.ui.components.FluentHairlineDivider
import com.example.ui.components.FluentMetricStrip
import com.example.ui.components.GlossyStatusPill
import com.example.ui.components.TactileFilterChip
import com.example.ui.components.TactileIconOrbButton
import com.example.ui.theme.BrightYellowSurface
import com.example.ui.theme.CobaltBluePrimary
import com.example.ui.theme.CrispBlueBorder
import com.example.ui.theme.DeepAmberInk
import com.example.ui.theme.DeepNavyInk
import com.example.ui.theme.PorcelainWhite
import com.example.ui.theme.PureWhite
import com.example.ui.theme.RoyalBlueAccent
import com.example.ui.theme.SkyBlueSoft
import com.example.ui.theme.SlateBlueMuted
import com.example.ui.theme.SolarYellowAccent
import com.example.ui.theme.TranslucentGlossWhite
import com.example.ui.theme.TranslucentHudOverlay
import com.example.ui.viewmodel.ScannerHudState
import com.example.util.HapticFeedbackController
import com.example.util.ImuPoseSnapshot
import com.example.util.ImuPoseTracker
import com.example.util.PhotogrammetryCameraController
import com.example.util.SynchronizedRgbFramePacket
import com.example.util.TactilePulse
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ScannerScreen(
    scannerState: ScannerHudState,
    onToggleRecording: () -> Unit,
    onCaptureGuidepointManually: () -> Unit,
    onResetScan: () -> Unit,
    onToggleAutoImuLock: () -> Unit,
    onToggleDepthOverlay: () -> Unit,
    onToggleLightrayOverlay: () -> Unit,
    onAeAfLockChanged: (Boolean) -> Unit,
    onImuPoseUpdated: (ImuPoseSnapshot) -> Unit,
    onManualHudOrbitDrag: (Int, Int) -> Unit,
    onSynchronizedCameraPacket: (SynchronizedRgbFramePacket) -> Unit,
    onHighResKeyframeCaptured: (Bitmap, ImuPoseSnapshot, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(scannerState.lastLockedKeyframeEventId) {
        if (scannerState.lastLockedKeyframeEventId > 0L) {
            HapticFeedbackController.perform(context, haptics, TactilePulse.GUIDEPOINT_LOCK)
        }
    }

    val imuPoseTracker = remember(context) {
        ImuPoseTracker(context) { snapshot ->
            onImuPoseUpdated(snapshot)
        }
    }

    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val cameraController = remember(context, imuPoseTracker) {
        PhotogrammetryCameraController(
            context = context,
            imuPoseTracker = imuPoseTracker,
            onRealtimeFramePacket = onSynchronizedCameraPacket,
            onHighResKeyframeCaptured = onHighResKeyframeCaptured
        )
    }

    DisposableEffect(imuPoseTracker) {
        imuPoseTracker.start()
        onDispose {
            imuPoseTracker.stop()
        }
    }

    DisposableEffect(cameraController) {
        onDispose {
            cameraController.shutdown()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PorcelainWhite)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // 1. Immersive Google Pixel Camera / Google Lens Spatial Viewfinder Stage
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .height(425.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(PureWhite)
                .testTag("realtime_scanner_viewport")
        ) {
            val capturedCount = maxOf(scannerState.capturedKeyframes.size, scannerState.lockedGuidepoints)
            if (hasCameraPermission) {
                CameraXPreviewSurface(
                    previewView = previewView,
                    cameraController = cameraController,
                    onOrbitDrag = onManualHudOrbitDrag,
                    capturedKeyframesCount = capturedCount,
                    totalKeyframesCount = scannerState.totalGuidepoints,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.img_launch_hero_v12_1794072150623),
                    contentDescription = "Geodesic 3D Scan Guidepoint Stage",
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val dAz = (dragAmount.x * 0.25f).toInt()
                                val dPitch = (-dragAmount.y * 0.18f).toInt()
                                if (dAz != 0 || dPitch != 0) {
                                    onManualHudOrbitDrag(dAz, dPitch)
                                }
                            }
                        },
                    contentScale = ContentScale.Crop
                )
                CircularScanProgressOverlay(
                    capturedKeyframesCount = capturedCount,
                    totalKeyframesCount = scannerState.totalGuidepoints,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Live IMU-rotated Geodesic Dome + 8x8 Monocular Depth Contours + Lightray Vectors
            RealtimeDepthAndGuidepointHudCanvas(
                scannerState = scannerState,
                modifier = Modifier.fillMaxSize()
            )

            // Floating Top Google Lens Compact Coaching Pill
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = TranslucentHudOverlay,
                shadowElevation = 4.dp,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (scannerState.isRecordingVideo) SolarYellowAccent else CrispBlueBorder.copy(alpha = 0.7f)
                ),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlossyStatusPill(
                            text = if (scannerState.isRecordingVideo) {
                                "REC ${scannerState.recordingSeconds}s"
                            } else {
                                "GP-${String.format(Locale.US, "%02d", scannerState.activeGuidepointIndex)} • ${scannerState.targetGuidepointAzimuthDeg}°"
                            },
                            isYellowHighlight = scannerState.isRecordingVideo,
                            icon = if (scannerState.isRecordingVideo) Icons.Rounded.FiberManualRecord else Icons.Rounded.Explore
                        )
                        GlossyStatusPill(
                            text = "${scannerState.lockedGuidepoints}/${scannerState.totalGuidepoints}",
                            isYellowHighlight = true,
                            icon = Icons.Rounded.CheckCircle
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = scannerState.guidanceInstruction,
                        style = MaterialTheme.typography.titleSmall,
                        color = DeepNavyInk,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (scannerState.isDeepProcessing) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = scannerState.deepProcessStage,
                            style = MaterialTheme.typography.labelSmall,
                            color = DeepAmberInk
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { scannerState.deepProcessProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            color = CobaltBluePrimary,
                            trackColor = BrightYellowSurface
                        )
                    }
                }
            }

            // Floating Right-Side Pure Circular Icon Rail (Pixel Camera Style — No Text Clutter)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.End
            ) {
                CircularHudIconButton(
                    icon = if (scannerState.isAeAfLocked) Icons.Rounded.Lock else Icons.Rounded.CenterFocusStrong,
                    label = "Toggle AE/AF Exposure Lock",
                    active = scannerState.isAeAfLocked,
                    activeYellow = true,
                    onClick = {
                        val locked = cameraController.toggleExposureAndFocusLock(previewView)
                        onAeAfLockChanged(locked)
                    },
                    testTagName = "toggle_ae_af_lock_btn"
                )

                CircularHudIconButton(
                    icon = Icons.Rounded.Grid4x4,
                    label = "Toggle Depth Map Overlay",
                    active = scannerState.showDepthHeatmapOverlay,
                    activeYellow = false,
                    onClick = onToggleDepthOverlay,
                    testTagName = "toggle_depth_overlay_btn"
                )

                CircularHudIconButton(
                    icon = Icons.Rounded.Flare,
                    label = "Toggle Lightray Vectors",
                    active = scannerState.showLightrayVectorsOverlay,
                    activeYellow = true,
                    onClick = onToggleLightrayOverlay,
                    testTagName = "toggle_lightray_overlay_btn"
                )
            }

            // Floating Camera Permission Icon Orb (if camera permission not yet granted)
            if (!hasCameraPermission) {
                TactileIconOrbButton(
                    icon = Icons.Rounded.CameraAlt,
                    contentDesc = "Enable Live Camera",
                    badgeText = "Grant Camera",
                    orbSize = 64.dp,
                    iconSize = 28.dp,
                    isYellowAccent = true,
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    testTagName = "enable_live_camera_btn",
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        // 2. Icon-First Camera Shutter & Sensor Command Deck (Google Pixel Camera + Fluent 2)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = TranslucentGlossWhite,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Primary Icon-First Camera Shutter Dock (Replaces wide text buttons)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Compact Icon Mode Switcher (Auto-Gyro vs Turntable)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TactileFilterChip(
                            label = "Auto Gyro",
                            selected = scannerState.autoImuLockEnabled,
                            onClick = onToggleAutoImuLock,
                            leadingIcon = Icons.Rounded.Explore,
                            iconOnly = true,
                            testTagName = "chip_auto_imu_lock"
                        )
                        TactileFilterChip(
                            label = "Turntable",
                            selected = !scannerState.autoImuLockEnabled,
                            onClick = onToggleAutoImuLock,
                            leadingIcon = Icons.Rounded.Sync,
                            iconOnly = true,
                            testTagName = "chip_manual_turntable"
                        )
                    }

                    // Center: Iconic 72.dp Circular Camera Shutter / 3D Build Orb
                    TactileIconOrbButton(
                        icon = when {
                            scannerState.isRecordingVideo -> Icons.Rounded.Stop
                            scannerState.lockedGuidepoints > 0 -> Icons.Rounded.ViewInAr
                            else -> Icons.Rounded.Videocam
                        },
                        contentDesc = if (scannerState.isRecordingVideo) {
                            "Finish and Reconstruct 3D Mesh"
                        } else {
                            "Start Real 3D Scan"
                        },
                        badgeText = when {
                            scannerState.isRecordingVideo -> "Finish (${scannerState.lockedGuidepoints})"
                            scannerState.lockedGuidepoints > 0 -> "Build 3D (${scannerState.lockedGuidepoints})"
                            else -> "Scan 3D"
                        },
                        orbSize = 72.dp,
                        iconSize = 32.dp,
                        isYellowAccent = scannerState.isRecordingVideo,
                        pulseType = TactilePulse.RECORD_START_STOP,
                        onClick = onToggleRecording,
                        testTagName = "record_video_scan_btn"
                    )

                    // Right: Circular Keyframe Snap Orb + Optional Reset Orb
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TactileIconOrbButton(
                            icon = Icons.Rounded.AddAPhoto,
                            contentDesc = "Capture High-Res Angle Keyframe",
                            badgeText = "${scannerState.lockedGuidepoints}/12",
                            orbSize = 56.dp,
                            iconSize = 24.dp,
                            isYellowAccent = true,
                            pulseType = TactilePulse.GUIDEPOINT_LOCK,
                            onClick = {
                                if (hasCameraPermission) {
                                    cameraController.captureHighQualityRgbKeyframe(previewView)
                                } else {
                                    onCaptureGuidepointManually()
                                }
                            },
                            testTagName = "lock_guidepoint_btn"
                        )

                        if (scannerState.capturedKeyframes.isNotEmpty()) {
                            TactileIconOrbButton(
                                icon = Icons.Rounded.RestartAlt,
                                contentDesc = "Reset Captured Keyframes",
                                badgeText = "Reset",
                                orbSize = 48.dp,
                                iconSize = 20.dp,
                                isSoftSurface = true,
                                onClick = {
                                    imuPoseTracker.resetTranslationOrigin()
                                    onResetScan()
                                },
                                testTagName = "reset_scan_keyframes_btn"
                            )
                        }
                    }
                }

                // Captured Keyframes Filmstrip (Google Photos Filmstrip style)
                if (scannerState.capturedKeyframes.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        scannerState.capturedKeyframes.forEach { kf ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(64.dp)
                            ) {
                                Image(
                                    bitmap = kf.rgbBitmap.asImageBitmap(),
                                    contentDescription = "Captured Keyframe GP-${kf.guidepointIndex}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "${kf.azimuthDeg}°",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DeepNavyInk,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                FluentHairlineDivider()

                // Icon-Enhanced 3-Column Sensor & 3D Depth Registration Ribbon
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("imu_sensor_fusion_card")
                ) {
                    FluentMetricStrip(
                        metrics = listOf(
                            Triple("Azimuth", "${scannerState.imuAzimuthDeg}°", false),
                            Triple("Gyro |ω|", "${scannerState.imuAngularSpeedDegPerSec.toInt()}°/s", !scannerState.isDeviceSteady),
                            Triple("Accel |a|", String.format(Locale.US, "%.2fm/s²", scannerState.imuLinearAccelMps2), false)
                        ),
                        icons = listOf(
                            Icons.Rounded.Explore,
                            Icons.Rounded.Sync,
                            Icons.Rounded.Speed
                        )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = String.format(
                                Locale.US,
                                "Quat [%.2f, %.2f, %.2f, %.2f] • %.2fm • %d Pts",
                                scannerState.imuQuaternionW,
                                scannerState.imuQuaternionX,
                                scannerState.imuQuaternionY,
                                scannerState.imuQuaternionZ,
                                scannerState.estimatedSubjectDepthMeters,
                                scannerState.registeredWorldPointsCount
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateBlueMuted
                        )
                        Text(
                            text = String.format(
                                Locale.US,
                                "Reg %d%% • %.2fmm",
                                scannerState.registrationConfidencePercent,
                                scannerState.reprojectionErrorMm
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = CobaltBluePrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RealtimeDepthAndGuidepointHudCanvas(
    scannerState: ScannerHudState,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f
        val cy = h * 0.54f
        val domeRadius = minOf(w, h) * 0.33f

        if (scannerState.showDepthHeatmapOverlay && scannerState.depthMatrix8x8.size >= 64) {
            val gridW = domeRadius * 1.50f
            val gridH = domeRadius * 1.50f
            val startX = cx - gridW * 0.5f
            val startY = cy - gridH * 0.5f
            val cellW = gridW / 8f
            val cellH = gridH / 8f

            for (r in 0 until 8) {
                for (c in 0 until 8) {
                    val depthVal = scannerState.depthMatrix8x8[r * 8 + c]
                    val cellColor = if (depthVal > 0.62f) {
                        SolarYellowAccent.copy(alpha = 0.22f)
                    } else {
                        CobaltBluePrimary.copy(alpha = 0.12f)
                    }
                    drawRect(
                        color = cellColor,
                        topLeft = Offset(startX + c * cellW + 2f, startY + r * cellH + 2f),
                        size = Size(cellW - 4f, cellH - 4f)
                    )
                }
            }
        }

        drawCircle(
            color = CobaltBluePrimary.copy(alpha = 0.80f),
            radius = domeRadius,
            center = Offset(cx, cy),
            style = Stroke(
                width = 2.2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
            )
        )
        drawCircle(
            color = SolarYellowAccent.copy(alpha = 0.90f),
            radius = domeRadius * 0.52f,
            center = Offset(cx, cy),
            style = Stroke(width = 2.0f)
        )

        if (scannerState.showLightrayVectorsOverlay) {
            val azRad = (scannerState.lightrayAzimuthDeg.toFloat() / 180f) * PI.toFloat()
            for (rayIdx in -1..1) {
                val rayAngle = azRad + rayIdx * 0.36f
                val startPt = Offset(
                    cx + cos(rayAngle) * (domeRadius * 1.15f),
                    cy + sin(rayAngle) * (domeRadius * 0.34f)
                )
                val endPt = Offset(
                    cx + cos(rayAngle) * (domeRadius * 0.34f),
                    cy + sin(rayAngle) * (domeRadius * 0.34f)
                )
                drawLine(
                    color = SolarYellowAccent,
                    start = Offset(
                        cx + cos(rayAngle) * (domeRadius * 1.15f),
                        cy + sin(rayAngle) * (domeRadius * 1.15f)
                    ),
                    end = endPt,
                    strokeWidth = 4.0f,
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = CobaltBluePrimary,
                    radius = 5.0f,
                    center = endPt
                )
            }
        }

        val imuOffsetRad = (scannerState.imuAzimuthDeg.toFloat() / 180f) * PI.toFloat()
        val total = scannerState.totalGuidepoints.coerceAtLeast(1)

        for (i in 0 until total) {
            val gpNumber = i + 1
            val gpTargetRad = (MeshRetopologyEngine.getTargetAzimuthForGuidepoint(gpNumber) / 180f) * PI.toFloat()
            val relAngle = gpTargetRad - imuOffsetRad - (PI.toFloat() / 2f)
            val gx = cx + cos(relAngle) * domeRadius
            val gy = cy + sin(relAngle) * domeRadius
            val isLocked = gpNumber in scannerState.lockedGuidepointIndices
            val isNextTarget = gpNumber == scannerState.activeGuidepointIndex

            if (isLocked) {
                drawCircle(
                    color = PureWhite,
                    radius = 12f,
                    center = Offset(gx, gy)
                )
                drawCircle(
                    color = CobaltBluePrimary,
                    radius = 9f,
                    center = Offset(gx, gy)
                )
                drawCircle(
                    color = SolarYellowAccent,
                    radius = 4f,
                    center = Offset(gx, gy)
                )
            } else if (isNextTarget) {
                drawCircle(
                    color = SolarYellowAccent,
                    radius = 16f,
                    center = Offset(gx, gy),
                    style = Stroke(width = 4.0f)
                )
                drawCircle(
                    color = SolarYellowAccent,
                    radius = 8f,
                    center = Offset(gx, gy)
                )
            } else {
                drawCircle(
                    color = PureWhite,
                    radius = 8.5f,
                    center = Offset(gx, gy)
                )
                drawCircle(
                    color = RoyalBlueAccent,
                    radius = 8.5f,
                    center = Offset(gx, gy),
                    style = Stroke(width = 2.2f)
                )
            }
        }

        val topSensorReticle = Offset(cx, cy - domeRadius)
        drawCircle(
            color = SolarYellowAccent,
            radius = 20f,
            center = topSensorReticle,
            style = Stroke(width = 2.8f)
        )
    }
}

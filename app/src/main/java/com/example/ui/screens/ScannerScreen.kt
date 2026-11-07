package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.ViewInAr
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.ui.components.GlossyStatusPill
import com.example.ui.components.GlossyTranslucentCard
import com.example.ui.components.TactileFilterChip
import com.example.ui.components.TactilePrimaryButton
import com.example.ui.components.TelemetryStatTile
import com.example.ui.theme.BrightYellowSurface
import com.example.ui.theme.CobaltBluePrimary
import com.example.ui.theme.CrispBlueBorder
import com.example.ui.theme.DeepAmberInk
import com.example.ui.theme.DeepNavyInk
import com.example.ui.theme.PureWhite
import com.example.ui.theme.RoyalBlueAccent
import com.example.ui.theme.SkyBlueSoft
import com.example.ui.theme.SlateBlueMuted
import com.example.ui.theme.SolarYellowAccent
import com.example.ui.theme.TranslucentHudOverlay
import com.example.ui.theme.WarmYellowBorder
import com.example.ui.viewmodel.ScannerHudState
import com.example.util.HapticFeedbackController
import com.example.util.TactilePulse
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ScannerScreen(
    scannerState: ScannerHudState,
    onToggleRecording: () -> Unit,
    onLockGuidepoint: () -> Unit,
    onToggleDepthOverlay: () -> Unit,
    onToggleLightrayOverlay: () -> Unit,
    onSelectCaptureProfile: (String) -> Unit,
    onCameraFrameAnalyzed: (List<Float>, Int, Int, Int) -> Unit,
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. Top Accuracy Guidance Banner (Smooth Glossy Translucent Card)
        GlossyTranslucentCard(
            borderColor = if (scannerState.isRecordingVideo) WarmYellowBorder else CrispBlueBorder
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlossyStatusPill(
                    text = if (scannerState.isRecordingVideo) {
                        "REC ${scannerState.recordingSeconds}s • 60FPS DEPTH"
                    } else {
                        "GUIDEPOINT CALIBRATION READY"
                    },
                    isYellowHighlight = scannerState.isRecordingVideo,
                    icon = if (scannerState.isRecordingVideo) Icons.Default.FiberManualRecord else Icons.Default.ViewInAr
                )
                GlossyStatusPill(
                    text = "ACCURACY ${scannerState.accuracyScorePercent}%",
                    isYellowHighlight = true,
                    icon = Icons.Default.CheckCircle
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = scannerState.guidanceInstruction,
                style = MaterialTheme.typography.titleLarge,
                color = DeepNavyInk
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Guidepoints Locked: ${scannerState.lockedGuidepoints}/${scannerState.totalGuidepoints} • Lightray Vector: ${scannerState.lightrayAzimuthDeg}° Az / ${scannerState.lightrayElevationDeg}° El (${scannerState.estimatedLux} lx)",
                style = MaterialTheme.typography.bodyMedium,
                color = SlateBlueMuted
            )

            if (scannerState.isDeepProcessing) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = BrightYellowSurface,
                    border = BorderStroke(1.dp, WarmYellowBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = scannerState.deepProcessStage,
                            style = MaterialTheme.typography.titleMedium,
                            color = DeepAmberInk
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { scannerState.deepProcessProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = CobaltBluePrimary,
                            trackColor = PureWhite
                        )
                    }
                }
            }
        }

        // 2. Realtime Video + Depth Map + Lightray + Geodesic Guidepoints Viewport
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .testTag("realtime_scanner_viewport"),
            shape = RoundedCornerShape(28.dp),
            color = PureWhite,
            shadowElevation = 6.dp,
            border = BorderStroke(2.dp, if (scannerState.isRecordingVideo) SolarYellowAccent else CobaltBluePrimary)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (hasCameraPermission) {
                    RealtimeCameraDepthPreview(
                        onFrameMetrics = onCameraFrameAnalyzed,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Clean illustrated geodesic guide diagram when camera permission not yet granted
                    Image(
                        painter = painterResource(id = R.drawable.img_hero_scanner_guide_1794062544213),
                        contentDescription = "Geodesic 3D Scan Guidepoint Diagram",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                // Interactive Realtime Depth Map + Lightray Vectors + 12 Orbital Guidepoints HUD Canvas
                RealtimeDepthAndGuidepointHudCanvas(
                    scannerState = scannerState,
                    modifier = Modifier.fillMaxSize()
                )

                // Top-right HUD Layer Toggles (Depth Map & Lightrays)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        onClick = {
                            HapticFeedbackController.perform(context, haptics, TactilePulse.TAP_LIGHT)
                            onToggleDepthOverlay()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (scannerState.showDepthHeatmapOverlay) CobaltBluePrimary else TranslucentHudOverlay,
                        border = BorderStroke(1.dp, CrispBlueBorder),
                        modifier = Modifier.testTag("toggle_depth_overlay_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.GridOn,
                                contentDescription = "Toggle Depth Map",
                                tint = if (scannerState.showDepthHeatmapOverlay) PureWhite else CobaltBluePrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "DEPTH",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (scannerState.showDepthHeatmapOverlay) PureWhite else CobaltBluePrimary
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            HapticFeedbackController.perform(context, haptics, TactilePulse.TAP_LIGHT)
                            onToggleLightrayOverlay()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (scannerState.showLightrayVectorsOverlay) SolarYellowAccent else TranslucentHudOverlay,
                        border = BorderStroke(1.dp, WarmYellowBorder),
                        modifier = Modifier.testTag("toggle_lightray_overlay_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.LightMode,
                                contentDescription = "Toggle Lightrays",
                                tint = DeepNavyInk,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "RAYS",
                                style = MaterialTheme.typography.labelSmall,
                                color = DeepNavyInk
                            )
                        }
                    }
                }

                // Optional Camera Permission Chip at bottom-left of viewport if not yet granted
                if (!hasCameraPermission) {
                    Surface(
                        onClick = {
                            HapticFeedbackController.perform(context, haptics, TactilePulse.TAP_LIGHT)
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = TranslucentHudOverlay,
                        border = BorderStroke(1.5.dp, CobaltBluePrimary),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(14.dp)
                            .testTag("enable_live_camera_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Enable Camera Sensor",
                                tint = CobaltBluePrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Enable Live Camera Sensor",
                                style = MaterialTheme.typography.labelLarge,
                                color = CobaltBluePrimary
                            )
                        }
                    }
                }
            }
        }

        // 3. Primary Capture & Guidepoint Actions (With Tactile Haptic Feedback)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TactilePrimaryButton(
                text = if (scannerState.isRecordingVideo) "Finish & Deep Process" else "Start Video Capture",
                icon = if (scannerState.isRecordingVideo) Icons.Default.StopCircle else Icons.Default.FiberManualRecord,
                onClick = onToggleRecording,
                isYellowAccent = scannerState.isRecordingVideo,
                pulseType = TactilePulse.RECORD_START_STOP,
                modifier = Modifier.weight(1.2f),
                testTagName = "record_video_scan_btn"
            )

            TactilePrimaryButton(
                text = "Lock GP (${scannerState.lockedGuidepoints}/${scannerState.totalGuidepoints})",
                icon = Icons.Default.AddLocationAlt,
                onClick = onLockGuidepoint,
                isYellowAccent = true,
                pulseType = TactilePulse.GUIDEPOINT_LOCK,
                modifier = Modifier.weight(1f),
                testTagName = "lock_guidepoint_btn"
            )
        }

        // 4. Subject Topology Profile Selector & Live Telemetry Strip
        GlossyTranslucentCard {
            Text(
                text = "Subject Retopology Target Profile",
                style = MaterialTheme.typography.titleMedium,
                color = DeepNavyInk,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Select subject geometry prior to optimize depth-map guessing & quad edge-flow.",
                style = MaterialTheme.typography.bodyMedium,
                color = SlateBlueMuted
            )
            Spacer(modifier = Modifier.height(14.dp))

            val profiles = listOf(
                "VASE_AMPHORA" to "Ceramic Vessel",
                "MODULAR_CRATE" to "Hard-Surface Crate",
                "ORGANIC_BOULDER" to "Organic Rock",
                "CUSTOM_VIDEO_SCAN" to "Mechanical Part"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                profiles.forEach { (key, label) ->
                    TactileFilterChip(
                        label = label,
                        selected = scannerState.selectedCaptureProfile == key,
                        onClick = { onSelectCaptureProfile(key) },
                        testTagName = "profile_chip_$key"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TelemetryStatTile(
                    label = "Depth Confidence",
                    value = "${scannerState.depthConfidencePercent}%",
                    modifier = Modifier.weight(1f)
                )
                TelemetryStatTile(
                    label = "Lightray Vector",
                    value = "${scannerState.lightrayAzimuthDeg}° / ${scannerState.lightrayElevationDeg}°",
                    isHighlightedYellow = true,
                    modifier = Modifier.weight(1f)
                )
                TelemetryStatTile(
                    label = "Guidepoints",
                    value = "${scannerState.lockedGuidepoints} / ${scannerState.totalGuidepoints}",
                    modifier = Modifier.weight(1f)
                )
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
        val cy = h * 0.52f
        val domeRadius = minOf(w, h) * 0.36f

        // 1. Optional 8x8 Realtime Depth Map Grid Overlay (Blue/Yellow contour cells)
        if (scannerState.showDepthHeatmapOverlay && scannerState.depthMatrix8x8.size >= 64) {
            val gridW = domeRadius * 1.6f
            val gridH = domeRadius * 1.6f
            val startX = cx - gridW * 0.5f
            val startY = cy - gridH * 0.5f
            val cellW = gridW / 8f
            val cellH = gridH / 8f

            for (r in 0 until 8) {
                for (c in 0 until 8) {
                    val depthVal = scannerState.depthMatrix8x8[r * 8 + c]
                    val cellColor = if (depthVal > 0.68f) {
                        SolarYellowAccent.copy(alpha = 0.22f)
                    } else {
                        CobaltBluePrimary.copy(alpha = 0.16f)
                    }
                    drawRect(
                        color = cellColor,
                        topLeft = Offset(startX + c * cellW + 2f, startY + r * cellH + 2f),
                        size = Size(cellW - 4f, cellH - 4f)
                    )
                }
            }
        }

        // 2. Geodesic Orbit Ring & Crosshair Calibration Guides
        drawCircle(
            color = CobaltBluePrimary.copy(alpha = 0.75f),
            radius = domeRadius,
            center = Offset(cx, cy),
            style = Stroke(
                width = 2.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
            )
        )
        drawCircle(
            color = SolarYellowAccent.copy(alpha = 0.85f),
            radius = domeRadius * 0.58f,
            center = Offset(cx, cy),
            style = Stroke(width = 2f)
        )

        // 3. Realtime Incident Lightray Direction Vectors (Yellow Rays converging on subject)
        if (scannerState.showLightrayVectorsOverlay) {
            val azRad = (scannerState.lightrayAzimuthDeg.toFloat() / 180f) * PI.toFloat()
            for (rayIdx in -1..1) {
                val rayAngle = azRad + rayIdx * 0.38f
                val startPt = Offset(
                    cx + cos(rayAngle) * (domeRadius * 1.18f),
                    cy + sin(rayAngle) * (domeRadius * 1.18f)
                )
                val endPt = Offset(
                    cx + cos(rayAngle) * (domeRadius * 0.35f),
                    cy + sin(rayAngle) * (domeRadius * 0.35f)
                )
                drawLine(
                    color = SolarYellowAccent,
                    start = startPt,
                    end = endPt,
                    strokeWidth = 4.5f,
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = CobaltBluePrimary,
                    radius = 6f,
                    center = endPt
                )
            }
        }

        // 4. 12 Orbital Guidepoints around the Geodesic Ring
        val total = scannerState.totalGuidepoints.coerceAtLeast(1)
        for (i in 0 until total) {
            val angle = (i.toFloat() / total.toFloat()) * 2f * PI.toFloat() - (PI.toFloat() / 2f)
            val gx = cx + cos(angle) * domeRadius
            val gy = cy + sin(angle) * domeRadius
            val isLocked = i < scannerState.lockedGuidepoints
            val isNextTarget = (i + 1) == scannerState.activeGuidepointIndex

            if (isLocked) {
                drawCircle(
                    color = PureWhite,
                    radius = 13f,
                    center = Offset(gx, gy)
                )
                drawCircle(
                    color = CobaltBluePrimary,
                    radius = 10f,
                    center = Offset(gx, gy)
                )
                drawCircle(
                    color = SolarYellowAccent,
                    radius = 4.5f,
                    center = Offset(gx, gy)
                )
            } else if (isNextTarget) {
                // Active target guidepoint pulsing reticle in bright yellow
                drawCircle(
                    color = SolarYellowAccent,
                    radius = 16f,
                    center = Offset(gx, gy),
                    style = Stroke(width = 4f)
                )
                drawCircle(
                    color = SolarYellowAccent,
                    radius = 8f,
                    center = Offset(gx, gy)
                )
            } else {
                drawCircle(
                    color = PureWhite,
                    radius = 9f,
                    center = Offset(gx, gy)
                )
                drawCircle(
                    color = RoyalBlueAccent,
                    radius = 9f,
                    center = Offset(gx, gy),
                    style = Stroke(width = 2.5f)
                )
            }
        }
    }
}

@Composable
private fun RealtimeCameraDepthPreview(
    onFrameMetrics: (List<Float>, Int, Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
        }
    }

    LaunchedEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                var lastAnalyzedMs = 0L
                imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    val now = System.currentTimeMillis()
                    if (now - lastAnalyzedMs >= 350L) {
                        lastAnalyzedMs = now
                        val plane = imageProxy.planes.firstOrNull()
                        if (plane != null) {
                            val buffer = plane.buffer
                            val width = imageProxy.width.coerceAtLeast(8)
                            val height = imageProxy.height.coerceAtLeast(8)
                            val rowStride = plane.rowStride
                            val pixelStride = plane.pixelStride
                            val grid = ArrayList<Float>(64)
                            var lumaSum = 0L
                            var brightestX = 4
                            var brightestY = 4
                            var maxCellLuma = -1

                            for (r in 0 until 8) {
                                val py = ((r + 0.5f) * height / 8f).toInt().coerceIn(0, height - 1)
                                for (c in 0 until 8) {
                                    val px = ((c + 0.5f) * width / 8f).toInt().coerceIn(0, width - 1)
                                    val idx = py * rowStride + px * pixelStride
                                    val luma = if (idx >= 0 && idx < buffer.limit()) {
                                        buffer.get(idx).toInt() and 0xFF
                                    } else {
                                        140
                                    }
                                    lumaSum += luma
                                    if (luma > maxCellLuma) {
                                        maxCellLuma = luma
                                        brightestX = c
                                        brightestY = r
                                    }
                                    grid.add((luma / 255f).coerceIn(0.1f, 0.99f))
                                }
                            }
                            val avgLuma = (lumaSum / 64L).toInt()
                            val estLux = (avgLuma * 4).coerceIn(120, 1200)
                            val azimuth = ((brightestX / 7f) * 360f).toInt().coerceIn(0, 359)
                            val elevation = (25 + ((7 - brightestY) / 7f) * 60f).toInt().coerceIn(15, 85)
                            onFrameMetrics(grid, azimuth, elevation, estLux)
                        }
                    }
                    imageProxy.close()
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
            } catch (_: Exception) {
                // Fallback gracefully on emulators without physical camera hardware
            }
        }, ContextCompat.getMainExecutor(context))
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}

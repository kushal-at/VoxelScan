package com.example.ui.components

import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FlipCameraAndroid
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.BrightYellowSurface
import com.example.ui.theme.CobaltBluePrimary
import com.example.ui.theme.CrispBlueBorder
import com.example.ui.theme.DeepAmberInk
import com.example.ui.theme.DeepNavyInk
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyBlueSoft
import com.example.ui.theme.SolarYellowAccent
import com.example.ui.theme.TranslucentHudOverlay
import com.example.ui.theme.WarmYellowBorder
import com.example.util.HapticFeedbackController
import com.example.util.PhotogrammetryCameraController
import com.example.util.TactilePulse
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Dedicated Jetpack Compose component implementing the CameraX `Preview` use case
 * via [PreviewView] as the real-time camera feed foundation for the 3D scanning interface.
 *
 * Includes a real-time [CircularScanProgressOverlay] displaying scan completion percentage
 * based on captured keyframes in the primary Solar Yellow (`#F5B800`) and Cobalt Blue (`#0A4D9C`)
 * color scheme, and pure circular icon HUD controls.
 */
@Composable
fun CameraXPreviewSurface(
    previewView: PreviewView,
    cameraController: PhotogrammetryCameraController,
    onOrbitDrag: (Int, Int) -> Unit,
    capturedKeyframesCount: Int = 0,
    totalKeyframesCount: Int = 12,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current

    var isStreamActive by remember { mutableStateOf(false) }
    var focusTapOffset by remember { mutableStateOf<Offset?>(null) }

    DisposableEffect(lifecycleOwner, previewView) {
        val observer = Observer<PreviewView.StreamState> { state ->
            isStreamActive = (state == PreviewView.StreamState.STREAMING)
        }
        previewView.previewStreamState.observe(lifecycleOwner, observer)
        cameraController.bindToLifecycle(lifecycleOwner, previewView)
        onDispose {
            previewView.previewStreamState.removeObserver(observer)
        }
    }

    LaunchedEffect(focusTapOffset) {
        if (focusTapOffset != null) {
            delay(1_100L)
            focusTapOffset = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("camerax_preview_surface")
            .pointerInput(previewView) {
                detectTapGestures { tapOffset ->
                    focusTapOffset = tapOffset
                    HapticFeedbackController.perform(context, haptics, TactilePulse.TAP_LIGHT)
                    cameraController.triggerTapToFocus(previewView, tapOffset.x, tapOffset.y)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val dAz = (dragAmount.x * 0.25f).toInt()
                    val dPitch = (-dragAmount.y * 0.18f).toInt()
                    if (dAz != 0 || dPitch != 0) {
                        onOrbitDrag(dAz, dPitch)
                    }
                }
            }
    ) {
        AndroidView(
            factory = {
                previewView.apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            update = { view ->
                view.scaleType = PreviewView.ScaleType.FILL_CENTER
            },
            modifier = Modifier.fillMaxSize()
        )

        // Real-time Circular Scan Completion Progress Overlay (Solar Yellow & Cobalt Blue)
        CircularScanProgressOverlay(
            capturedKeyframesCount = capturedKeyframesCount,
            totalKeyframesCount = totalKeyframesCount,
            modifier = Modifier.fillMaxSize()
        )

        // Tap-to-Focus Metering Reticle Animation
        AnimatedVisibility(
            visible = focusTapOffset != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            val tapPt = focusTapOffset
            if (tapPt != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = SolarYellowAccent,
                        radius = 34.dp.toPx(),
                        center = tapPt,
                        style = Stroke(width = 3.dp.toPx())
                    )
                    drawCircle(
                        color = CobaltBluePrimary,
                        radius = 6.dp.toPx(),
                        center = tapPt
                    )
                }
            }
        }

        // Bottom-left Compact Sensor & Stream Icon Badge
        Surface(
            shape = CircleShape,
            color = TranslucentHudOverlay,
            border = BorderStroke(1.dp, CrispBlueBorder),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
                .testTag("camerax_stream_status_badge")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Sensors,
                    contentDescription = "CameraX Preview Stream State",
                    tint = if (isStreamActive) CobaltBluePrimary else SolarYellowAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (isStreamActive) "LIVE" else "READY",
                    style = MaterialTheme.typography.labelSmall,
                    color = DeepNavyInk,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Bottom-right Pure Circular Camera Lens Flip Icon Button
        Surface(
            onClick = {
                HapticFeedbackController.perform(context, haptics, TactilePulse.TAP_LIGHT)
                cameraController.switchCameraLens(lifecycleOwner, previewView)
            },
            shape = CircleShape,
            color = TranslucentHudOverlay,
            shadowElevation = 3.dp,
            border = BorderStroke(1.dp, CrispBlueBorder),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(14.dp)
                .size(48.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = "Switch Camera Lens"
                }
                .testTag("switch_camera_lens_btn")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.FlipCameraAndroid,
                    contentDescription = "Switch Camera Lens",
                    tint = CobaltBluePrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Circular Progress Indicator Overlay for the CameraX Preview that displays real-time
 * scan completion percentage (`0% .. 100%`) based on the number of captured keyframes,
 * styled with the primary Solar Yellow (`#F5B800`), Cobalt Blue (`#0A4D9C`), and Pure White palette.
 */
@Composable
fun CircularScanProgressOverlay(
    capturedKeyframesCount: Int,
    totalKeyframesCount: Int,
    modifier: Modifier = Modifier
) {
    val safeTotal = totalKeyframesCount.coerceAtLeast(1)
    val safeCaptured = capturedKeyframesCount.coerceIn(0, safeTotal)
    val targetFraction = safeCaptured.toFloat() / safeTotal.toFloat()
    val completionPercent = (targetFraction * 100f).roundToInt().coerceIn(0, 100)

    val animatedProgress by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scan_completion_arc"
    )

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Central Orbital Scan Completion Progress Ring around the Viewfinder Reticle
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w * 0.5f
            val cy = h * 0.54f
            val ringRadius = minOf(w, h) * 0.375f
            val arcTopLeft = Offset(cx - ringRadius, cy - ringRadius)
            val arcSize = Size(ringRadius * 2f, ringRadius * 2f)

            drawCircle(
                color = PureWhite.copy(alpha = 0.55f),
                radius = ringRadius,
                center = Offset(cx, cy),
                style = Stroke(width = 7.dp.toPx())
            )

            drawCircle(
                color = CobaltBluePrimary.copy(alpha = 0.28f),
                radius = ringRadius,
                center = Offset(cx, cy),
                style = Stroke(width = 4.5.dp.toPx())
            )

            for (i in 0 until safeTotal) {
                val angleRad = ((i.toFloat() / safeTotal.toFloat()) * 2f * PI.toFloat()) - (PI.toFloat() / 2f)
                val innerR = ringRadius - 6.dp.toPx()
                val outerR = ringRadius + 6.dp.toPx()
                val isTickCaptured = i < safeCaptured
                drawLine(
                    color = if (isTickCaptured) SolarYellowAccent else CobaltBluePrimary.copy(alpha = 0.45f),
                    start = Offset(cx + cos(angleRad) * innerR, cy + sin(angleRad) * innerR),
                    end = Offset(cx + cos(angleRad) * outerR, cy + sin(angleRad) * outerR),
                    strokeWidth = if (isTickCaptured) 3.dp.toPx() else 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            if (animatedProgress > 0.005f) {
                val sweepDeg = (animatedProgress * 360f).coerceIn(0f, 360f)
                drawArc(
                    color = CobaltBluePrimary,
                    startAngle = -90f,
                    sweepAngle = sweepDeg,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = SolarYellowAccent,
                    startAngle = -90f,
                    sweepAngle = sweepDeg,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // 2. Floating Circular Progress Gauge Pill (Left Rail of Viewfinder)
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = TranslucentHudOverlay,
            shadowElevation = 4.dp,
            border = BorderStroke(
                width = 1.2.dp,
                color = if (completionPercent >= 100) SolarYellowAccent else CrispBlueBorder
            ),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp)
                .testTag("circular_scan_progress_overlay")
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(50.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.fillMaxSize(),
                        color = SkyBlueSoft,
                        strokeWidth = 5.dp
                    )
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxSize(),
                        color = CobaltBluePrimary,
                        trackColor = SkyBlueSoft.copy(alpha = 0.3f),
                        strokeWidth = 5.dp,
                        strokeCap = StrokeCap.Round
                    )
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(3.5.dp),
                        color = SolarYellowAccent,
                        trackColor = BrightYellowSurface.copy(alpha = 0.35f),
                        strokeWidth = 3.dp,
                        strokeCap = StrokeCap.Round
                    )
                    Text(
                        text = "$completionPercent%",
                        style = MaterialTheme.typography.labelSmall,
                        color = DeepNavyInk,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.size(4.dp))

                Surface(
                    shape = CircleShape,
                    color = if (completionPercent >= 100) SolarYellowAccent else BrightYellowSurface,
                    border = BorderStroke(0.8.dp, WarmYellowBorder)
                ) {
                    Text(
                        text = "$safeCaptured/$safeTotal",
                        style = MaterialTheme.typography.labelSmall,
                        color = DeepAmberInk,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

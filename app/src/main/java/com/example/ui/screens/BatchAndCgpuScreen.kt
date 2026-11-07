package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Flare
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ScanAssetEntity
import com.example.ui.components.FluentHairlineDivider
import com.example.ui.components.FluentMetricStrip
import com.example.ui.components.GlossyStatusPill
import com.example.ui.components.GlossyTranslucentCard
import com.example.ui.components.TactileIconOrbButton
import com.example.ui.theme.BrightYellowSurface
import com.example.ui.theme.CobaltBluePrimary
import com.example.ui.theme.DeepAmberInk
import com.example.ui.theme.DeepNavyInk
import com.example.ui.theme.PorcelainWhite
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyBlueSoft
import com.example.ui.theme.SlateBlueMuted
import com.example.ui.theme.SolarYellowAccent
import com.example.ui.viewmodel.BatchPipelineState
import com.example.ui.viewmodel.CgpuEngineState
import com.example.util.HapticFeedbackController
import com.example.util.TactilePulse

@Composable
fun BatchAndCgpuScreen(
    allAssets: List<ScanAssetEntity>,
    batchState: BatchPipelineState,
    cgpuState: CgpuEngineState,
    onRunBatchPipeline: () -> Unit,
    onEnqueueBatchAsset: (String, String) -> Unit,
    onSelectAssetForInspection: (ScanAssetEntity) -> Unit,
    onToggleLowEndTurbo: () -> Unit,
    onToggleInt8Depth: () -> Unit,
    onToggleFp16Shaders: () -> Unit,
    onToggleTileMemoryPool: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PorcelainWhite)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. Editorial Header & Icon-First Action Dock
        GlossyTranslucentCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlossyStatusPill(
                    text = "BATCH PIPELINE",
                    icon = Icons.Rounded.AutoAwesomeMotion
                )
                GlossyStatusPill(
                    text = "${allAssets.size} QUEUED",
                    isYellowHighlight = true,
                    icon = Icons.Rounded.Layers
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Multi-Video 3D Queue",
                        style = MaterialTheme.typography.headlineSmall,
                        color = DeepNavyInk,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Batch quad-retopology to .fbx & .obj with baked _albedo.png & _noise.png.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateBlueMuted
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Icon-First Run & Enqueue Orbs
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TactileIconOrbButton(
                        icon = if (batchState.isRunningBatch) Icons.Rounded.HourglassTop else Icons.Rounded.PlayArrow,
                        contentDesc = if (batchState.isRunningBatch) "Running Batch" else "Run Batch (${allAssets.size})",
                        badgeText = if (batchState.isRunningBatch) "BUSY" else "RUN (${allAssets.size})",
                        orbSize = 54.dp,
                        iconSize = 26.dp,
                        enabled = !batchState.isRunningBatch,
                        pulseType = TactilePulse.BATCH_STEP_COMPLETE,
                        onClick = onRunBatchPipeline,
                        testTagName = "run_batch_pipeline_btn"
                    )
                    TactileIconOrbButton(
                        icon = Icons.Rounded.AddAPhoto,
                        contentDesc = "+ Enqueue Scan",
                        badgeText = "+ SCAN",
                        orbSize = 54.dp,
                        iconSize = 22.dp,
                        isYellowAccent = true,
                        onClick = {
                            val profiles = listOf("VASE_AMPHORA", "MODULAR_CRATE", "ORGANIC_BOULDER", "CUSTOM_VIDEO_SCAN")
                            val chosen = profiles.random()
                            onEnqueueBatchAsset("Batch Prop Capture #${(100..999).random()}", chosen)
                        },
                        testTagName = "enqueue_batch_job_btn"
                    )
                }
            }

            if (batchState.isRunningBatch) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = BrightYellowSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Processing: ${batchState.currentAssetTitle}",
                            style = MaterialTheme.typography.titleSmall,
                            color = DeepNavyInk,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${batchState.currentStageName} (${batchState.completedAssetsCount}/${batchState.totalQueuedCount})",
                            style = MaterialTheme.typography.bodySmall,
                            color = DeepAmberInk
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { batchState.currentStageProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = CobaltBluePrimary,
                            trackColor = PureWhite
                        )
                    }
                }
            } else if (batchState.lastBatchSummary.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = BrightYellowSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Batch Complete",
                            tint = CobaltBluePrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = batchState.lastBatchSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = DeepNavyInk,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // 2. Microsoft Fluent 2 Divided List for Queued Assets
        GlossyTranslucentCard {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.ViewInAr,
                    contentDescription = null,
                    tint = CobaltBluePrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Asset Queue • Tap to Inspect in 3D",
                    style = MaterialTheme.typography.titleMedium,
                    color = DeepNavyInk,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            allAssets.forEachIndexed { idx, asset ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            HapticFeedbackController.perform(context, haptics, TactilePulse.TAP_LIGHT)
                            onSelectAssetForInspection(asset)
                        }
                        .padding(vertical = 12.dp, horizontal = 4.dp)
                        .testTag("batch_item_${asset.id}"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    if (asset.status == "READY") SkyBlueSoft else BrightYellowSurface
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ViewInAr,
                                contentDescription = asset.title,
                                tint = if (asset.status == "READY") CobaltBluePrimary else DeepAmberInk,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = asset.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = DeepNavyInk,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${asset.retopoQuadCount} Quads • ${asset.capturedGuidepoints}/${asset.totalGuidepoints} Views • ${asset.targetEngine}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SlateBlueMuted
                            )
                        }
                    }
                    GlossyStatusPill(
                        text = asset.status,
                        isYellowHighlight = asset.status != "READY",
                        icon = if (asset.status == "READY") Icons.Rounded.CheckCircle else Icons.Rounded.HourglassTop
                    )
                }
                if (idx < allAssets.lastIndex) {
                    FluentHairlineDivider()
                }
            }
        }

        // 3. Mobile CGPU & Low-End Device Optimization Console with Material Extended Icons
        GlossyTranslucentCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Memory,
                        contentDescription = "Mobile CGPU Engine",
                        tint = CobaltBluePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Mobile CGPU & Low-End Optimizer",
                        style = MaterialTheme.typography.titleMedium,
                        color = DeepNavyInk,
                        fontWeight = FontWeight.Bold
                    )
                }
                GlossyStatusPill(
                    text = if (cgpuState.lowEndTurboEnabled) "TURBO ON" else "STANDARD",
                    isYellowHighlight = cgpuState.lowEndTurboEnabled,
                    icon = Icons.Rounded.Speed
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = cgpuState.hardwareTierLabel,
                style = MaterialTheme.typography.labelSmall,
                color = CobaltBluePrimary
            )

            Spacer(modifier = Modifier.height(10.dp))

            FluentMetricStrip(
                metrics = listOf(
                    Triple("CGPU Load", "${cgpuState.cgpuComputeUtilization}%", false),
                    Triple("RAM Pool", "${cgpuState.memoryFootprintMb} MB", true),
                    Triple("Target Rate", "${cgpuState.dynamicFramePacingFps} FPS", false)
                ),
                icons = listOf(
                    Icons.Rounded.DeveloperBoard,
                    Icons.Rounded.Storage,
                    Icons.Rounded.Speed
                )
            )

            Spacer(modifier = Modifier.height(8.dp))
            FluentHairlineDivider()

            CgpuOptimizationToggleRow(
                icon = Icons.Rounded.Bolt,
                title = "Low-End Device Turbo Mode",
                subtitle = "Zero-copy ringbuffers and lightweight mesh decimation on 2GB–4GB RAM devices.",
                checked = cgpuState.lowEndTurboEnabled,
                onCheckedChange = {
                    HapticFeedbackController.perform(context, haptics, TactilePulse.TAP_LIGHT)
                    onToggleLowEndTurbo()
                },
                testTag = "switch_low_end_turbo"
            )
            FluentHairlineDivider()

            CgpuOptimizationToggleRow(
                icon = Icons.Rounded.Memory,
                title = "INT8 Quantized Depth Map Solver",
                subtitle = "Executes real-time depth guessing in 8-bit integer tensor blocks for 3.2× faster throughput.",
                checked = cgpuState.int8DepthQuantization,
                onCheckedChange = {
                    HapticFeedbackController.perform(context, haptics, TactilePulse.TAP_LIGHT)
                    onToggleInt8Depth()
                },
                testTag = "switch_int8_depth"
            )
            FluentHairlineDivider()

            CgpuOptimizationToggleRow(
                icon = Icons.Rounded.Flare,
                title = "FP16 Half-Precision Lightray Shaders",
                subtitle = "Uses mobile GPU half-float compute units for specular & normal vector reconstruction.",
                checked = cgpuState.fp16LightrayShaders,
                onCheckedChange = {
                    HapticFeedbackController.perform(context, haptics, TactilePulse.TAP_LIGHT)
                    onToggleFp16Shaders()
                },
                testTag = "switch_fp16_lightrays"
            )
            FluentHairlineDivider()

            CgpuOptimizationToggleRow(
                icon = Icons.Rounded.GridView,
                title = "Tile-Based Texture Memory Pooling",
                subtitle = "Streams 512px Albedo & Noise tiles to prevent OutOfMemoryError during batch runs.",
                checked = cgpuState.tileMemoryPooling,
                onCheckedChange = {
                    HapticFeedbackController.perform(context, haptics, TactilePulse.TAP_LIGHT)
                    onToggleTileMemoryPool()
                },
                testTag = "switch_tile_memory_pool"
            )
        }
    }
}

@Composable
private fun CgpuOptimizationToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (checked) BrightYellowSurface else SkyBlueSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (checked) DeepAmberInk else CobaltBluePrimary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = DeepNavyInk,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = SlateBlueMuted
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = DeepNavyInk,
                checkedTrackColor = SolarYellowAccent,
                uncheckedThumbColor = CobaltBluePrimary,
                uncheckedTrackColor = SkyBlueSoft
            )
        )
    }
}

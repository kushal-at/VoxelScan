package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ScanAssetEntity
import com.example.ui.components.GlossyStatusPill
import com.example.ui.components.GlossyTranslucentCard
import com.example.ui.components.TactilePrimaryButton
import com.example.ui.components.TelemetryStatTile
import com.example.ui.theme.BrightYellowSurface
import com.example.ui.theme.CobaltBluePrimary
import com.example.ui.theme.CrispBlueBorder
import com.example.ui.theme.DeepAmberInk
import com.example.ui.theme.DeepNavyInk
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyBlueSoft
import com.example.ui.theme.SlateBlueMuted
import com.example.ui.theme.SolarYellowAccent
import com.example.ui.theme.WarmYellowBorder
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. High-Volume Batch Pipeline Header & Execution Controls
        GlossyTranslucentCard(
            borderColor = if (batchState.isRunningBatch) WarmYellowBorder else CrispBlueBorder
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlossyStatusPill(
                    text = "HIGH-VOLUME ASSET PIPELINE",
                    icon = Icons.Default.AutoAwesomeMotion
                )
                GlossyStatusPill(
                    text = "${allAssets.size} ASSETS IN QUEUE",
                    isYellowHighlight = true
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Automated Multi-Video Deep Processing",
                style = MaterialTheme.typography.headlineMedium,
                color = DeepNavyInk
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Sequentially converts recorded video captures into quad-retopologized .fbx & .obj meshes with baked _albedo.png and _noise.png textures.",
                style = MaterialTheme.typography.bodyMedium,
                color = SlateBlueMuted
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (batchState.isRunningBatch) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = BrightYellowSurface,
                    border = BorderStroke(1.5.dp, WarmYellowBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Processing: ${batchState.currentAssetTitle}",
                            style = MaterialTheme.typography.titleMedium,
                            color = DeepNavyInk,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${batchState.currentStageName} (${batchState.completedAssetsCount}/${batchState.totalQueuedCount} completed)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DeepAmberInk
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { batchState.currentStageProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp)),
                            color = CobaltBluePrimary,
                            trackColor = PureWhite
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            } else if (batchState.lastBatchSummary.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = BrightYellowSurface,
                    border = BorderStroke(1.dp, WarmYellowBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Batch Complete",
                            tint = CobaltBluePrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = batchState.lastBatchSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = DeepNavyInk,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TactilePrimaryButton(
                    text = if (batchState.isRunningBatch) "Batch Running..." else "Run Batch Pipeline (${allAssets.size})",
                    icon = Icons.Default.PlayArrow,
                    enabled = !batchState.isRunningBatch,
                    onClick = onRunBatchPipeline,
                    pulseType = TactilePulse.BATCH_STEP_COMPLETE,
                    modifier = Modifier.weight(1.3f),
                    testTagName = "run_batch_pipeline_btn"
                )
                TactilePrimaryButton(
                    text = "+ Add Scan Job",
                    icon = Icons.Default.AddCircle,
                    isYellowAccent = true,
                    onClick = {
                        val profiles = listOf("VASE_AMPHORA", "MODULAR_CRATE", "ORGANIC_BOULDER", "CUSTOM_VIDEO_SCAN")
                        val chosen = profiles.random()
                        onEnqueueBatchAsset("Batch Prop Capture #${(100..999).random()}", chosen)
                    },
                    modifier = Modifier.weight(1f),
                    testTagName = "enqueue_batch_job_btn"
                )
            }
        }

        // 2. Queued & Processed Assets List
        GlossyTranslucentCard {
            Text(
                text = "Batch Asset Queue (Tap item to open in 3D Mesh Inspector)",
                style = MaterialTheme.typography.titleMedium,
                color = DeepNavyInk,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                allAssets.forEach { asset ->
                    Surface(
                        onClick = {
                            HapticFeedbackController.perform(context, haptics, TactilePulse.TAP_LIGHT)
                            onSelectAssetForInspection(asset)
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = PureWhite,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (asset.status == "READY") CrispBlueBorder else WarmYellowBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("batch_item_${asset.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = asset.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = DeepNavyInk,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${asset.retopoQuadCount} Quads • ${asset.capturedGuidepoints}/${asset.totalGuidepoints} Guidepoints • ${asset.targetEngine}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SlateBlueMuted
                                )
                            }
                            GlossyStatusPill(
                                text = asset.status,
                                isYellowHighlight = asset.status != "READY"
                            )
                        }
                    }
                }
            }
        }

        // 3. Mobile CGPU & Low-End Device Optimization Console
        GlossyTranslucentCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = "Mobile CGPU Engine",
                        tint = CobaltBluePrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Mobile CGPU & Low-End Optimizer",
                        style = MaterialTheme.typography.titleLarge,
                        color = DeepNavyInk
                    )
                }
                GlossyStatusPill(
                    text = if (cgpuState.lowEndTurboEnabled) "TURBO ON" else "STANDARD",
                    isYellowHighlight = cgpuState.lowEndTurboEnabled,
                    icon = Icons.Default.Speed
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = cgpuState.hardwareTierLabel,
                style = MaterialTheme.typography.labelMedium,
                color = CobaltBluePrimary
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TelemetryStatTile(
                    label = "CGPU Load",
                    value = "${cgpuState.cgpuComputeUtilization}%",
                    modifier = Modifier.weight(1f)
                )
                TelemetryStatTile(
                    label = "RAM Pool",
                    value = "${cgpuState.memoryFootprintMb} MB",
                    isHighlightedYellow = true,
                    modifier = Modifier.weight(1f)
                )
                TelemetryStatTile(
                    label = "Target FPS",
                    value = "${cgpuState.dynamicFramePacingFps} FPS",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            CgpuOptimizationToggleRow(
                title = "Low-End Device Turbo Mode",
                subtitle = "Prioritizes zero-copy ringbuffers and lightweight mesh decimation on 2GB-4GB RAM devices.",
                checked = cgpuState.lowEndTurboEnabled,
                onCheckedChange = {
                    HapticFeedbackController.perform(context, haptics, TactilePulse.TAP_LIGHT)
                    onToggleLowEndTurbo()
                },
                testTag = "switch_low_end_turbo"
            )

            Spacer(modifier = Modifier.height(10.dp))

            CgpuOptimizationToggleRow(
                title = "INT8 Quantized Depth Map Solver",
                subtitle = "Executes real-time depth guessing in 8-bit integer tensor blocks for 3.2x faster throughput.",
                checked = cgpuState.int8DepthQuantization,
                onCheckedChange = {
                    HapticFeedbackController.perform(context, haptics, TactilePulse.TAP_LIGHT)
                    onToggleInt8Depth()
                },
                testTag = "switch_int8_depth"
            )

            Spacer(modifier = Modifier.height(10.dp))

            CgpuOptimizationToggleRow(
                title = "FP16 Half-Precision Lightray Shaders",
                subtitle = "Uses mobile GPU half-float compute units for specular & normal vector reconstruction.",
                checked = cgpuState.fp16LightrayShaders,
                onCheckedChange = {
                    HapticFeedbackController.perform(context, haptics, TactilePulse.TAP_LIGHT)
                    onToggleFp16Shaders()
                },
                testTag = "switch_fp16_lightrays"
            )

            Spacer(modifier = Modifier.height(10.dp))

            CgpuOptimizationToggleRow(
                title = "Tile-Based Texture Memory Pooling",
                subtitle = "Streams 512px-1024px Albedo & Noise tiles to prevent OutOfMemoryError during batch runs.",
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
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = PureWhite,
        border = BorderStroke(1.dp, CrispBlueBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = DeepNavyInk,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
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
}

package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.GlossyStatusPill
import com.example.ui.screens.AiTextureStudioScreen
import com.example.ui.screens.BatchAndCgpuScreen
import com.example.ui.screens.MeshInspectorScreen
import com.example.ui.screens.ScannerScreen
import com.example.ui.theme.CobaltBluePrimary
import com.example.ui.theme.CrispBlueBorder
import com.example.ui.theme.DeepNavyInk
import com.example.ui.theme.PorcelainWhite
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SlateBlueMuted
import com.example.ui.theme.SolarYellowAccent
import com.example.ui.theme.TranslucentGlossWhite
import com.example.ui.theme.VoxelScanTheme
import com.example.ui.viewmodel.VoxelScanViewModel
import com.example.ui.viewmodel.VoxelTab
import com.example.util.HapticFeedbackController
import com.example.util.TactilePulse
import com.example.util.configureAppCheck

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory Firebase App Check configuration with launch intent before UI/ViewModels
        configureAppCheck(this, intent)

        // STEP 2: Enable Edge-to-Edge display
        enableEdgeToEdge()

        // STEP 3: Render Smooth Glossy Translucent Blue/Yellow/White UI
        setContent {
            VoxelScanTheme {
                VoxelScanAppRoot()
            }
        }
    }
}

private data class NavDestination(
    val tab: VoxelTab,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

@Composable
fun VoxelScanAppRoot(
    viewModel: VoxelScanViewModel = viewModel(
        factory = VoxelScanViewModel.provideFactory(LocalContext.current)
    )
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val scannerState by viewModel.scannerState.collectAsStateWithLifecycle()
    val cgpuState by viewModel.cgpuState.collectAsStateWithLifecycle()
    val batchState by viewModel.batchState.collectAsStateWithLifecycle()
    val allAssets by viewModel.allAssets.collectAsStateWithLifecycle()
    val selectedAssetId by viewModel.selectedAssetId.collectAsStateWithLifecycle()
    val renderMode by viewModel.renderMode.collectAsStateWithLifecycle()
    val lodPreset by viewModel.lodPreset.collectAsStateWithLifecycle()
    val targetEngine by viewModel.targetEngine.collectAsStateWithLifecycle()
    val currentMesh by viewModel.currentMesh.collectAsStateWithLifecycle()
    val currentAlbedo by viewModel.currentAlbedoBitmap.collectAsStateWithLifecycle()
    val currentNoise by viewModel.currentNoiseBitmap.collectAsStateWithLifecycle()
    val lastExportedBundle by viewModel.lastExportedBundle.collectAsStateWithLifecycle()
    val exportBannerMessage by viewModel.exportBannerMessage.collectAsStateWithLifecycle()
    val aiTextureState by viewModel.aiTextureState.collectAsStateWithLifecycle()

    // Mandatory BackHandler on non-home tabs when custom state switching is used
    if (currentTab != VoxelTab.SCANNER) {
        BackHandler {
            HapticFeedbackController.perform(context, haptics, TactilePulse.TAP_LIGHT)
            viewModel.selectTab(VoxelTab.SCANNER)
        }
    }

    val destinations = listOf(
        NavDestination(VoxelTab.SCANNER, "Scanner", Icons.Default.Videocam, "nav_tab_scanner"),
        NavDestination(VoxelTab.INSPECTOR, "3D Mesh", Icons.Default.ViewInAr, "nav_tab_inspector"),
        NavDestination(VoxelTab.BATCH_PIPELINE, "Batch & CGPU", Icons.Default.AutoAwesomeMotion, "nav_tab_batch"),
        NavDestination(VoxelTab.AI_TEXTURE_STUDIO, "AI Texture", Icons.Default.AutoFixHigh, "nav_tab_ai_texture")
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(PorcelainWhite)
    ) {
        val isWideScreen = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = PorcelainWhite,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                GlossyTopAppBar(
                    cgpuTurboEnabled = cgpuState.lowEndTurboEnabled,
                    cgpuLoadPercent = cgpuState.cgpuComputeUtilization
                )
            },
            bottomBar = {
                if (!isWideScreen) {
                    Surface(
                        color = TranslucentGlossWhite,
                        tonalElevation = 3.dp,
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, CrispBlueBorder)
                    ) {
                        NavigationBar(
                            containerColor = TranslucentGlossWhite,
                            tonalElevation = 0.dp
                        ) {
                            destinations.forEach { dest ->
                                val selected = currentTab == dest.tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        HapticFeedbackController.perform(context, haptics, TactilePulse.TAP_LIGHT)
                                        viewModel.selectTab(dest.tab)
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = dest.icon,
                                            contentDescription = dest.label
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = dest.label,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = DeepNavyInk,
                                        selectedTextColor = CobaltBluePrimary,
                                        indicatorColor = SolarYellowAccent,
                                        unselectedIconColor = SlateBlueMuted,
                                        unselectedTextColor = SlateBlueMuted
                                    ),
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideScreen) {
                    NavigationRail(
                        containerColor = TranslucentGlossWhite,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        destinations.forEach { dest ->
                            val selected = currentTab == dest.tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    HapticFeedbackController.perform(context, haptics, TactilePulse.TAP_LIGHT)
                                    viewModel.selectTab(dest.tab)
                                },
                                icon = {
                                    Icon(imageVector = dest.icon, contentDescription = dest.label)
                                },
                                label = {
                                    Text(text = dest.label, style = MaterialTheme.typography.labelLarge)
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = DeepNavyInk,
                                    selectedTextColor = CobaltBluePrimary,
                                    indicatorColor = SolarYellowAccent,
                                    unselectedIconColor = SlateBlueMuted,
                                    unselectedTextColor = SlateBlueMuted
                                ),
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (currentTab) {
                        VoxelTab.SCANNER -> ScannerScreen(
                            scannerState = scannerState,
                            onToggleRecording = { viewModel.toggleVideoRecording() },
                            onLockGuidepoint = { viewModel.lockNextGuidepointManually() },
                            onToggleDepthOverlay = { viewModel.toggleDepthOverlay() },
                            onToggleLightrayOverlay = { viewModel.toggleLightrayOverlay() },
                            onSelectCaptureProfile = { viewModel.selectCaptureProfile(it) },
                            onCameraFrameAnalyzed = { grid, az, el, lux ->
                                viewModel.onCameraFrameAnalyzed(grid, az, el, lux)
                            }
                        )

                        VoxelTab.INSPECTOR -> MeshInspectorScreen(
                            allAssets = allAssets,
                            selectedAssetId = selectedAssetId,
                            mesh = currentMesh,
                            renderMode = renderMode,
                            lodPreset = lodPreset,
                            targetEngine = targetEngine,
                            albedoBitmap = currentAlbedo,
                            noiseBitmap = currentNoise,
                            lastExportedBundle = lastExportedBundle,
                            exportBannerMessage = exportBannerMessage,
                            onSelectAsset = { viewModel.selectAsset(it) },
                            onSelectRenderMode = { viewModel.setRenderMode(it) },
                            onSelectLodPreset = { viewModel.setLodPreset(it) },
                            onSelectTargetEngine = { viewModel.setTargetEngine(it) },
                            onExportPackage = { shareNow ->
                                viewModel.exportCurrentAssetPackage(context, shareNow)
                            }
                        )

                        VoxelTab.BATCH_PIPELINE -> BatchAndCgpuScreen(
                            allAssets = allAssets,
                            batchState = batchState,
                            cgpuState = cgpuState,
                            onRunBatchPipeline = { viewModel.runHighVolumeBatchPipeline(context) },
                            onEnqueueBatchAsset = { title, profile ->
                                viewModel.enqueueNewBatchScanJob(title, profile)
                            },
                            onSelectAssetForInspection = { asset ->
                                viewModel.selectAsset(asset)
                                viewModel.selectTab(VoxelTab.INSPECTOR)
                            },
                            onToggleLowEndTurbo = { viewModel.toggleLowEndTurbo() },
                            onToggleInt8Depth = { viewModel.toggleInt8Depth() },
                            onToggleFp16Shaders = { viewModel.toggleFp16Shaders() },
                            onToggleTileMemoryPool = { viewModel.toggleTileMemoryPool() }
                        )

                        VoxelTab.AI_TEXTURE_STUDIO -> AiTextureStudioScreen(
                            aiTextureState = aiTextureState,
                            currentAlbedoBitmap = currentAlbedo,
                            currentNoiseBitmap = currentNoise,
                            onGenerateNewTexture = { prompt, isNoise ->
                                viewModel.generateTextureWithNanoBanana(prompt, isNoise)
                            },
                            onEditExistingTexture = { instruction, isNoise ->
                                viewModel.editActiveTextureWithNanoBanana(instruction, isNoise)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlossyTopAppBar(
    cgpuTurboEnabled: Boolean,
    cgpuLoadPercent: Int
) {
    Surface(
        color = TranslucentGlossWhite,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, CrispBlueBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CobaltBluePrimary,
                    border = BorderStroke(1.5.dp, SolarYellowAccent),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.ViewInAr,
                            contentDescription = "VoxelScan 3D Logo",
                            tint = PureWhite,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "VoxelScan 3D",
                        style = MaterialTheme.typography.titleLarge,
                        color = DeepNavyInk,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Depth • Lightrays • Retopology • FBX/OBJ",
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateBlueMuted
                    )
                }
            }

            GlossyStatusPill(
                text = if (cgpuTurboEnabled) "CGPU ${cgpuLoadPercent}% • TURBO" else "CGPU ${cgpuLoadPercent}%",
                isYellowHighlight = cgpuTurboEnabled,
                icon = Icons.Default.Memory
            )
        }
    }
}

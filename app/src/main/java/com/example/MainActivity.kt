package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesomeMotion
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CameraEnhance
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.CircularHudIconButton
import com.example.ui.components.GlossyStatusPill
import com.example.ui.components.TactileIconOrbButton
import com.example.ui.screens.AiTextureStudioScreen
import com.example.ui.screens.BatchAndCgpuScreen
import com.example.ui.screens.MeshInspectorScreen
import com.example.ui.screens.ScannerScreen
import com.example.ui.theme.BrightYellowSurface
import com.example.ui.theme.CobaltBluePrimary
import com.example.ui.theme.CrispBlueBorder
import com.example.ui.theme.DeepAmberInk
import com.example.ui.theme.DeepNavyInk
import com.example.ui.theme.PorcelainWhite
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyBlueSoft
import com.example.ui.theme.SlateBlueMuted
import com.example.ui.theme.SolarYellowAccent
import com.example.ui.theme.TranslucentGlossWhite
import com.example.ui.theme.VoxelScanTheme
import com.example.ui.theme.WarmYellowBorder
import com.example.ui.viewmodel.VoxelScanViewModel
import com.example.ui.viewmodel.VoxelTab
import com.example.util.HapticFeedbackController
import com.example.util.TactilePulse
import com.example.util.configureAppCheck
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory Firebase App Check configuration with launch intent before UI/ViewModels
        configureAppCheck(this, intent)

        // STEP 2: Enable Edge-to-Edge display
        enableEdgeToEdge()

        // STEP 3: Render Google Material 3 Expressive + Microsoft Fluent 2 Acrylic Blue/Yellow/White UI
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
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
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

    var showLaunchScreen by remember { mutableStateOf(true) }
    var launchProgressTarget by remember { mutableFloatStateOf(0.15f) }

    LaunchedEffect(showLaunchScreen) {
        if (showLaunchScreen) {
            launchProgressTarget = 1f
            delay(1150L)
            showLaunchScreen = false
        }
    }

    if (currentTab != VoxelTab.SCANNER) {
        BackHandler {
            HapticFeedbackController.perform(context, haptics, TactilePulse.TAP_LIGHT)
            viewModel.selectTab(VoxelTab.SCANNER)
        }
    }

    val destinations = listOf(
        NavDestination(
            tab = VoxelTab.SCANNER,
            label = "Scanner",
            selectedIcon = Icons.Rounded.Videocam,
            unselectedIcon = Icons.Outlined.Videocam,
            testTag = "nav_tab_scanner"
        ),
        NavDestination(
            tab = VoxelTab.INSPECTOR,
            label = "3D Studio",
            selectedIcon = Icons.Rounded.ViewInAr,
            unselectedIcon = Icons.Outlined.ViewInAr,
            testTag = "nav_tab_inspector"
        ),
        NavDestination(
            tab = VoxelTab.BATCH_PIPELINE,
            label = "Pipeline",
            selectedIcon = Icons.Rounded.AutoAwesomeMotion,
            unselectedIcon = Icons.Outlined.AutoAwesomeMotion,
            testTag = "nav_tab_batch"
        ),
        NavDestination(
            tab = VoxelTab.AI_TEXTURE_STUDIO,
            label = "AI Maps",
            selectedIcon = Icons.Rounded.AutoFixHigh,
            unselectedIcon = Icons.Outlined.AutoFixHigh,
            testTag = "nav_tab_ai_texture"
        )
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
                FluentGoogleTopBar(
                    cgpuTurboEnabled = cgpuState.lowEndTurboEnabled,
                    cgpuLoadPercent = cgpuState.cgpuComputeUtilization,
                    onReplayLaunchScreen = {
                        launchProgressTarget = 0.2f
                        showLaunchScreen = true
                    }
                )
            },
            bottomBar = {
                if (!isWideScreen) {
                    Surface(
                        color = TranslucentGlossWhite,
                        tonalElevation = 2.dp,
                        shadowElevation = 10.dp,
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
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
                                        showLaunchScreen = false
                                        HapticFeedbackController.perform(context, haptics, TactilePulse.TAP_LIGHT)
                                        viewModel.selectTab(dest.tab)
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = dest.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
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
                                    showLaunchScreen = false
                                    HapticFeedbackController.perform(context, haptics, TactilePulse.TAP_LIGHT)
                                    viewModel.selectTab(dest.tab)
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
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

                Crossfade(
                    targetState = currentTab,
                    modifier = Modifier.weight(1f),
                    label = "tab_crossfade"
                ) { tab ->
                    when (tab) {
                        VoxelTab.SCANNER -> ScannerScreen(
                            scannerState = scannerState,
                            onToggleRecording = { viewModel.toggleVideoRecording(context) },
                            onCaptureGuidepointManually = { viewModel.captureAndLockGuidepointManually() },
                            onResetScan = { viewModel.clearCapturedKeyframes() },
                            onToggleAutoImuLock = { viewModel.toggleAutoImuLock() },
                            onToggleDepthOverlay = { viewModel.toggleDepthOverlay() },
                            onToggleLightrayOverlay = { viewModel.toggleLightrayOverlay() },
                            onAeAfLockChanged = { viewModel.setAeAfLockedState(it) },
                            onImuPoseUpdated = { viewModel.onImuPoseUpdated(it) },
                            onManualHudOrbitDrag = { dAz, dPitch ->
                                viewModel.onManualHudOrbitDrag(dAz, dPitch)
                            },
                            onSynchronizedCameraPacket = { packet ->
                                viewModel.onSynchronizedCameraPacket(packet)
                            },
                            onHighResKeyframeCaptured = { bmp, pose, sharpness ->
                                viewModel.onHighResKeyframeCaptured(bmp, pose, sharpness)
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

        // Redesigned Animated Launch Screen Overlay (v1.2)
        AnimatedVisibility(
            visible = showLaunchScreen,
            enter = fadeIn(animationSpec = tween(250)),
            exit = fadeOut(animationSpec = tween(320)) + scaleOut(targetScale = 1.04f, animationSpec = tween(320))
        ) {
            VoxelScanLaunchScreenOverlay(
                progressTarget = launchProgressTarget,
                onDismiss = { showLaunchScreen = false }
            )
        }
    }
}

@Composable
private fun FluentGoogleTopBar(
    cgpuTurboEnabled: Boolean,
    cgpuLoadPercent: Int,
    onReplayLaunchScreen: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PorcelainWhite)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CobaltBluePrimary,
                border = BorderStroke(1.5.dp, SolarYellowAccent),
                shadowElevation = 3.dp,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("top_bar_brand_logo")
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_voxelscan_logo_v12_1794072150003),
                    contentDescription = "VoxelScan 3D Redesigned Logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "VoxelScan 3D",
                        style = MaterialTheme.typography.titleMedium,
                        color = DeepNavyInk,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = CircleShape,
                        color = SolarYellowAccent
                    ) {
                        Text(
                            text = "v1.2",
                            style = MaterialTheme.typography.labelSmall,
                            color = DeepNavyInk,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "Spatial Photogrammetry & Quad Studio",
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateBlueMuted
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlossyStatusPill(
                text = if (cgpuTurboEnabled) "CGPU ${cgpuLoadPercent}% • TURBO" else "CGPU ${cgpuLoadPercent}%",
                isYellowHighlight = cgpuTurboEnabled,
                icon = Icons.Rounded.Memory
            )
            CircularHudIconButton(
                icon = Icons.Rounded.AutoAwesome,
                label = "Launch Studio Splash",
                active = false,
                onClick = onReplayLaunchScreen,
                testTagName = "replay_launch_screen_btn"
            )
        }
    }
}

@Composable
private fun VoxelScanLaunchScreenOverlay(
    progressTarget: Float,
    onDismiss: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "launch_progress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PorcelainWhite.copy(alpha = 0.97f))
            .clickable { onDismiss() }
            .testTag("redesigned_launch_screen_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(36.dp),
            color = PureWhite,
            shadowElevation = 14.dp,
            border = BorderStroke(1.2.dp, CrispBlueBorder),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .padding(horizontal = 20.dp)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Hero 3D Photogrammetry Studio Banner + FloatingRedesigned Emblem
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_launch_hero_v12_1794072150623),
                        contentDescription = "VoxelScan 3D Launch Studio Hero",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(178.dp)
                            .align(Alignment.TopCenter)
                            .clip(RoundedCornerShape(26.dp))
                    )

                    // Version 1.2 Badge on Top-Right of Hero
                    Surface(
                        shape = CircleShape,
                        color = SolarYellowAccent,
                        border = BorderStroke(1.5.dp, CobaltBluePrimary),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "RELEASE v1.2",
                            style = MaterialTheme.typography.labelSmall,
                            color = DeepNavyInk,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }

                    // Redesigned App Logo Emblem
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = CobaltBluePrimary,
                        border = BorderStroke(3.dp, PureWhite),
                        shadowElevation = 8.dp,
                        modifier = Modifier.size(76.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_voxelscan_logo_v12_1794072150003),
                            contentDescription = "VoxelScan 3D Emblem",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "VoxelScan 3D",
                    style = MaterialTheme.typography.headlineMedium,
                    color = DeepNavyInk,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "CameraX Photogrammetry • 6-DoF IMU Pose • Quad Retopology",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateBlueMuted,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 4 Icon-First Feature Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    LaunchFeatureIconBadge(icon = Icons.Rounded.CameraEnhance, label = "CameraX")
                    LaunchFeatureIconBadge(icon = Icons.Rounded.Sensors, label = "6-DoF IMU")
                    LaunchFeatureIconBadge(icon = Icons.Rounded.ViewInAr, label = "FBX / OBJ")
                    LaunchFeatureIconBadge(icon = Icons.Rounded.Speed, label = "CGPU Turbo", highlight = true)
                }

                Spacer(modifier = Modifier.height(18.dp))

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = CobaltBluePrimary,
                    trackColor = SkyBlueSoft
                )

                Spacer(modifier = Modifier.height(16.dp))

                TactileIconOrbButton(
                    icon = Icons.Rounded.ArrowForward,
                    contentDesc = "Enter VoxelScan 3D Studio",
                    badgeText = "ENTER STUDIO",
                    orbSize = 56.dp,
                    iconSize = 24.dp,
                    isYellowAccent = true,
                    onClick = onDismiss,
                    testTagName = "launch_screen_enter_btn"
                )
            }
        }
    }
}

@Composable
private fun LaunchFeatureIconBadge(
    icon: ImageVector,
    label: String,
    highlight: Boolean = false
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = if (highlight) BrightYellowSurface else SkyBlueSoft,
            border = BorderStroke(1.dp, if (highlight) WarmYellowBorder else CrispBlueBorder),
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (highlight) DeepAmberInk else CobaltBluePrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = DeepNavyInk,
            fontWeight = FontWeight.Bold
        )
    }
}

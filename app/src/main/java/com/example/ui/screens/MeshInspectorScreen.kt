package com.example.ui.screens

import android.graphics.Bitmap
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
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Texture
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ScanAssetEntity
import com.example.domain.ExportedAssetBundle
import com.example.domain.Mesh3DData
import com.example.domain.Vec3
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
import com.example.ui.theme.SkyBlueSoft
import com.example.ui.theme.SlateBlueMuted
import com.example.ui.theme.SolarYellowAccent
import com.example.ui.theme.TranslucentGlossWhite
import com.example.ui.theme.TranslucentHudOverlay
import com.example.ui.theme.WarmYellowBorder
import com.example.ui.viewmodel.LodPreset
import com.example.ui.viewmodel.ViewportRenderMode
import com.example.util.TactilePulse
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MeshInspectorScreen(
    allAssets: List<ScanAssetEntity>,
    selectedAssetId: Int?,
    mesh: Mesh3DData,
    renderMode: ViewportRenderMode,
    lodPreset: LodPreset,
    targetEngine: String,
    albedoBitmap: Bitmap,
    noiseBitmap: Bitmap,
    lastExportedBundle: ExportedAssetBundle?,
    exportBannerMessage: String?,
    onSelectAsset: (ScanAssetEntity) -> Unit,
    onSelectRenderMode: (ViewportRenderMode) -> Unit,
    onSelectLodPreset: (LodPreset) -> Unit,
    onSelectTargetEngine: (String) -> Unit,
    onExportPackage: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var yawRad by remember { mutableFloatStateOf(0.55f) }
    var pitchRad by remember { mutableFloatStateOf(0.32f) }
    val activeAsset = allAssets.find { it.id == selectedAssetId } ?: allAssets.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PorcelainWhite)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // 1. Icon-Led Horizontal Asset Selector Ribbon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            allAssets.forEach { asset ->
                val isSelected = asset.id == activeAsset?.id
                TactileFilterChip(
                    label = asset.title,
                    selected = isSelected,
                    leadingIcon = Icons.Rounded.ViewInAr,
                    onClick = { onSelectAsset(asset) },
                    testTagName = "asset_select_chip_${asset.id}"
                )
            }
        }

        // 2. Expansive 3D Interactive Stage with Floating Icon-First Render Dock
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .height(370.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(PureWhite)
                .testTag("interactive_3d_mesh_viewport")
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        yawRad += dragAmount.x * 0.01f
                        pitchRad = (pitchRad + dragAmount.y * 0.01f).coerceIn(-1.1f, 1.1f)
                    }
                }
        ) {
            Interactive3DMeshCanvas(
                mesh = mesh,
                renderMode = renderMode,
                yawRad = yawRad,
                pitchRad = pitchRad,
                modifier = Modifier.fillMaxSize()
            )

            // Floating Top Bar on 3D Stage
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlossyStatusPill(
                    text = renderMode.label.uppercase(),
                    icon = Icons.Rounded.Explore
                )

                GlossyStatusPill(
                    text = "${mesh.quads.size}Q • ${mesh.vertices.size}V",
                    isYellowHighlight = true,
                    icon = Icons.Rounded.ViewInAr
                )
            }

            // Floating Bottom Icon-First Render-Mode Dock inside 3D Stage
            Surface(
                shape = CircleShape,
                color = TranslucentHudOverlay,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ViewportRenderMode.entries.forEach { mode ->
                        val isSelected = renderMode == mode
                        val modeIcon = when (mode) {
                            ViewportRenderMode.SHADED_PBR -> Icons.Rounded.Texture
                            ViewportRenderMode.QUAD_WIREFRAME -> Icons.Rounded.GridOn
                            ViewportRenderMode.DEPTH_MAP -> Icons.Rounded.Gradient
                            ViewportRenderMode.LIGHTRAY_NORMALS -> Icons.Rounded.WbSunny
                            ViewportRenderMode.UV_CHECKER -> Icons.Rounded.Apps
                        }
                        TactileFilterChip(
                            label = mode.label,
                            selected = isSelected,
                            leadingIcon = modeIcon,
                            iconOnly = !isSelected,
                            onClick = { onSelectRenderMode(mode) },
                            testTagName = "render_mode_${mode.name}"
                        )
                    }
                }
            }
        }

        // 3. Seamless Microsoft Fluent 2 / Google Material 3 Inspector Sheet
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = TranslucentGlossWhite,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Icon-Enhanced 3-Column Topology Telemetry Ribbon
                FluentMetricStrip(
                    metrics = listOf(
                        Triple("Raw Tris", "${(activeAsset?.rawTriangleCount ?: 148000) / 1000}K", false),
                        Triple("Quads", "${mesh.quads.size}", true),
                        Triple("UV Pack", "${activeAsset?.uvEfficiencyPercent ?: 96}%", false)
                    ),
                    icons = listOf(
                        Icons.Rounded.Category,
                        Icons.Rounded.GridOn,
                        Icons.Rounded.CropFree
                    )
                )

                FluentHairlineDivider()

                // Icon-Led LOD Density & Game Engine Selector
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LodPreset.entries.forEach { preset ->
                            val lodIcon = when (preset) {
                                LodPreset.LOW_END_MOBILE -> Icons.Rounded.Speed
                                LodPreset.GAME_READY -> Icons.Rounded.Tune
                                LodPreset.HIGH_ACCURACY -> Icons.Rounded.HighQuality
                            }
                            TactileFilterChip(
                                label = preset.label,
                                selected = lodPreset == preset,
                                leadingIcon = lodIcon,
                                onClick = { onSelectLodPreset(preset) },
                                testTagName = "lod_preset_${preset.name}"
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TactileFilterChip(
                            label = "UE5 (Z-Up)",
                            selected = targetEngine == "UNREAL_ENGINE_5",
                            leadingIcon = Icons.Rounded.SportsEsports,
                            onClick = { onSelectTargetEngine("UNREAL_ENGINE_5") },
                            modifier = Modifier.weight(1f),
                            testTagName = "engine_ue5_chip"
                        )
                        TactileFilterChip(
                            label = "Unity (Y-Up)",
                            selected = targetEngine == "UNITY_URP",
                            leadingIcon = Icons.Rounded.ViewInAr,
                            onClick = { onSelectTargetEngine("UNITY_URP") },
                            modifier = Modifier.weight(1f),
                            testTagName = "engine_unity_chip"
                        )
                    }
                }

                FluentHairlineDivider()

                // Projected Camera UV Swatches + Icon-First Export & Share Orb Dock
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Compact Dual UV Swatches (Albedo & Noise)
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(SkyBlueSoft.copy(alpha = 0.55f))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                bitmap = albedoBitmap.asImageBitmap(),
                                contentDescription = "Albedo Image Texture Map",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Palette,
                                        contentDescription = null,
                                        tint = CobaltBluePrimary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Albedo",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = DeepNavyInk,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "_albedo.png",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SlateBlueMuted
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(BrightYellowSurface.copy(alpha = 0.7f))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                bitmap = noiseBitmap.asImageBitmap(),
                                contentDescription = "Noise Roughness Texture Map",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Grain,
                                        contentDescription = null,
                                        tint = DeepAmberInk,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Noise",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = DeepNavyInk,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "_noise.png",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DeepAmberInk
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Icon-First Export & Share Orbs
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TactileIconOrbButton(
                            icon = Icons.Rounded.Download,
                            contentDesc = "Export FBX + OBJ",
                            badgeText = "FBX/OBJ",
                            orbSize = 54.dp,
                            iconSize = 24.dp,
                            pulseType = TactilePulse.EXPORT_SUCCESS,
                            onClick = { onExportPackage(false) },
                            testTagName = "export_fbx_obj_btn"
                        )
                        TactileIconOrbButton(
                            icon = Icons.Rounded.Share,
                            contentDesc = "Share Bundle",
                            badgeText = "SHARE",
                            orbSize = 54.dp,
                            iconSize = 22.dp,
                            isYellowAccent = true,
                            pulseType = TactilePulse.EXPORT_SUCCESS,
                            onClick = { onExportPackage(true) },
                            testTagName = "share_export_bundle_btn"
                        )
                    }
                }

                if (!exportBannerMessage.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = BrightYellowSurface,
                        border = BorderStroke(1.dp, WarmYellowBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Export Complete",
                                    tint = CobaltBluePrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = exportBannerMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DeepNavyInk,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (lastExportedBundle != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${lastExportedBundle.fbxFile.name} • ${lastExportedBundle.objFile.name} • ${lastExportedBundle.mtlFile.name}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DeepAmberInk
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Interactive3DMeshCanvas(
    mesh: Mesh3DData,
    renderMode: ViewportRenderMode,
    yawRad: Float,
    pitchRad: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f
        val cy = h * 0.48f
        val scale = minOf(w, h) * 0.35f

        val gridHalf = 5
        for (g in -gridHalf..gridHalf) {
            val gx = g * 0.28f
            val pStart = projectPoint(Vec3(gx, -0.92f, -1.4f), yawRad, pitchRad, cx, cy, scale)
            val pEnd = projectPoint(Vec3(gx, -0.92f, 1.4f), yawRad, pitchRad, cx, cy, scale)
            drawLine(
                color = CrispBlueBorder,
                start = Offset(pStart.x, pStart.y),
                end = Offset(pEnd.x, pEnd.y),
                strokeWidth = 1.2f
            )
        }

        val cosY = cos(yawRad)
        val sinY = sin(yawRad)
        val cosP = cos(pitchRad)
        val sinP = sin(pitchRad)

        val transformedVerts = mesh.vertices.map { v ->
            val rx = v.x * cosY + v.z * sinY
            val rz1 = -v.x * sinY + v.z * cosY
            val ry = v.y * cosP - rz1 * sinP
            val rz = v.y * sinP + rz1 * cosP
            val perspective = 3.2f / (3.2f - rz.coerceIn(-2.0f, 2.0f))
            Vec3(cx + rx * scale * perspective, cy - ry * scale * perspective, rz)
        }

        val sortedQuads = mesh.quads.sortedBy { q ->
            (transformedVerts[q.v0].z + transformedVerts[q.v1].z +
                transformedVerts[q.v2].z + transformedVerts[q.v3].z) * 0.25f
        }

        val lightDir = Vec3(0.45f, 0.65f, 0.61f)

        for ((qIdx, q) in sortedQuads.withIndex()) {
            val p0 = transformedVerts[q.v0]
            val p1 = transformedVerts[q.v1]
            val p2 = transformedVerts[q.v2]
            val p3 = transformedVerts[q.v3]

            val path = Path().apply {
                moveTo(p0.x, p0.y)
                lineTo(p1.x, p1.y)
                lineTo(p2.x, p2.y)
                lineTo(p3.x, p3.y)
                close()
            }

            val n = q.normal
            val nrx = n.x * cosY + n.z * sinY
            val nrz1 = -n.x * sinY + n.z * cosY
            val nry = n.y * cosP - nrz1 * sinP
            val nrz = n.y * sinP + nrz1 * cosP
            val lambert = (nrx * lightDir.x + nry * lightDir.y + nrz * lightDir.z).coerceIn(0.28f, 1.08f)

            val fillColor = when (renderMode) {
                ViewportRenderMode.SHADED_PBR -> {
                    val argb = q.sampledColorArgb
                    val rNorm = (((argb shr 16) and 0xFF) / 255f * lambert).coerceIn(0f, 1f)
                    val gNorm = (((argb shr 8) and 0xFF) / 255f * lambert).coerceIn(0f, 1f)
                    val bNorm = ((argb and 0xFF) / 255f * lambert).coerceIn(0f, 1f)
                    Color(red = rNorm, green = gNorm, blue = bNorm, alpha = 0.98f)
                }
                ViewportRenderMode.QUAD_WIREFRAME -> PorcelainWhite.copy(alpha = 0.88f)
                ViewportRenderMode.DEPTH_MAP -> {
                    val d = ((p0.z + 1.1f) / 2.2f).coerceIn(0f, 1f)
                    if (d > 0.58f) SolarYellowAccent else CobaltBluePrimary.copy(alpha = 0.35f + 0.6f * d)
                }
                ViewportRenderMode.LIGHTRAY_NORMALS -> {
                    Color(
                        red = ((nrx + 1f) * 0.45f).coerceIn(0.1f, 0.95f),
                        green = ((nry + 1f) * 0.48f).coerceIn(0.2f, 0.95f),
                        blue = ((nrz + 1f) * 0.5f).coerceIn(0.4f, 1f),
                        alpha = 0.95f
                    )
                }
                ViewportRenderMode.UV_CHECKER -> {
                    if (qIdx % 2 == 0) CobaltBluePrimary else SolarYellowAccent
                }
            }

            drawPath(path = path, color = fillColor, style = Fill)

            val strokeColor = when (renderMode) {
                ViewportRenderMode.QUAD_WIREFRAME -> CobaltBluePrimary
                ViewportRenderMode.SHADED_PBR -> PureWhite.copy(alpha = 0.22f)
                else -> DeepNavyInk.copy(alpha = 0.35f)
            }
            drawPath(
                path = path,
                color = strokeColor,
                style = Stroke(width = if (renderMode == ViewportRenderMode.QUAD_WIREFRAME) 2.2f else 0.9f)
            )
        }

        for (gp in mesh.guidepoints) {
            val proj = projectPoint(gp.position, yawRad, pitchRad, cx, cy, scale)
            drawCircle(
                color = if (gp.isLocked) SolarYellowAccent else SkyBlueSoft,
                radius = 6.5f,
                center = Offset(proj.x, proj.y)
            )
            drawCircle(
                color = CobaltBluePrimary,
                radius = 6.5f,
                center = Offset(proj.x, proj.y),
                style = Stroke(width = 2f)
            )
        }
    }
}

private fun projectPoint(
    v: Vec3,
    yawRad: Float,
    pitchRad: Float,
    cx: Float,
    cy: Float,
    scale: Float
): Vec3 {
    val cosY = cos(yawRad)
    val sinY = sin(yawRad)
    val cosP = cos(pitchRad)
    val sinP = sin(pitchRad)
    val rx = v.x * cosY + v.z * sinY
    val rz1 = -v.x * sinY + v.z * cosY
    val ry = v.y * cosP - rz1 * sinP
    val rz = v.y * sinP + rz1 * cosP
    val perspective = 3.2f / (3.2f - rz.coerceIn(-2.0f, 2.0f))
    return Vec3(cx + rx * scale * perspective, cy - ry * scale * perspective, rz)
}

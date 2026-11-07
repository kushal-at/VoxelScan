package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewInAr
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ScanAssetEntity
import com.example.domain.ExportedAssetBundle
import com.example.domain.Mesh3DData
import com.example.domain.Vec3
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
import com.example.ui.theme.PorcelainWhite
import com.example.ui.theme.PureWhite
import com.example.ui.theme.RoyalBlueAccent
import com.example.ui.theme.SkyBlueSoft
import com.example.ui.theme.SlateBlueMuted
import com.example.ui.theme.SolarYellowAccent
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. Captured Asset Switcher Strip
        GlossyTranslucentCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Retopologized 3D Models",
                    style = MaterialTheme.typography.titleLarge,
                    color = DeepNavyInk
                )
                GlossyStatusPill(
                    text = "${mesh.quads.size} QUADS • ${mesh.vertices.size} VERTS",
                    isYellowHighlight = true,
                    icon = Icons.Default.ViewInAr
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                allAssets.forEach { asset ->
                    TactileFilterChip(
                        label = asset.title,
                        selected = asset.id == activeAsset?.id,
                        onClick = { onSelectAsset(asset) },
                        testTagName = "asset_select_chip_${asset.id}"
                    )
                }
            }
        }

        // 2. Interactive 3D Viewport Canvas (Touch-Orbit 360° + Render Mode Switcher)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(330.dp)
                .testTag("interactive_3d_mesh_viewport"),
            shape = RoundedCornerShape(28.dp),
            color = PureWhite,
            shadowElevation = 6.dp,
            border = BorderStroke(2.dp, CobaltBluePrimary)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
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

                // Top-left Viewport Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = TranslucentHudOverlay,
                    border = BorderStroke(1.dp, CrispBlueBorder),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp)
                ) {
                    Text(
                        text = "DRAG TO ORBIT 3D • ${renderMode.label.uppercase()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = CobaltBluePrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                // Bottom Render Mode Bar
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ViewportRenderMode.entries.forEach { mode ->
                        TactileFilterChip(
                            label = mode.label,
                            selected = renderMode == mode,
                            onClick = { onSelectRenderMode(mode) },
                            testTagName = "render_mode_${mode.name}"
                        )
                    }
                }
            }
        }

        // 3. Topology Optimization & Target Game Engine Configuration
        GlossyTranslucentCard {
            Text(
                text = "Retopology Density & Game Engine Target",
                style = MaterialTheme.typography.titleMedium,
                color = DeepNavyInk,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LodPreset.entries.forEach { preset ->
                    TactileFilterChip(
                        label = preset.label,
                        selected = lodPreset == preset,
                        onClick = { onSelectLodPreset(preset) },
                        testTagName = "lod_preset_${preset.name}"
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TactileFilterChip(
                    label = "Unreal Engine 5 (Z-Up FBX)",
                    selected = targetEngine == "UNREAL_ENGINE_5",
                    onClick = { onSelectTargetEngine("UNREAL_ENGINE_5") },
                    modifier = Modifier.weight(1f),
                    testTagName = "engine_ue5_chip"
                )
                TactileFilterChip(
                    label = "Unity URP/HDRP (Y-Up FBX)",
                    selected = targetEngine == "UNITY_URP",
                    onClick = { onSelectTargetEngine("UNITY_URP") },
                    modifier = Modifier.weight(1f),
                    testTagName = "engine_unity_chip"
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TelemetryStatTile(
                    label = "Raw Triangles",
                    value = "${(activeAsset?.rawTriangleCount ?: 148000) / 1000}K",
                    modifier = Modifier.weight(1f)
                )
                TelemetryStatTile(
                    label = "Retopo Quads",
                    value = "${mesh.quads.size}",
                    isHighlightedYellow = true,
                    modifier = Modifier.weight(1f)
                )
                TelemetryStatTile(
                    label = "UV Packing",
                    value = "${activeAsset?.uvEfficiencyPercent ?: 95}%",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 4. Captured Image Texture & Procedural Noise Texture Preview Cards
        GlossyTranslucentCard {
            Text(
                text = "Captured Image Texture & Micro-Surface Noise Texture",
                style = MaterialTheme.typography.titleMedium,
                color = DeepNavyInk,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Automatically bundled alongside .fbx and .obj for one-click Unreal Engine & Unity material import.",
                style = MaterialTheme.typography.bodyMedium,
                color = SlateBlueMuted
            )
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.5.dp, CobaltBluePrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                    ) {
                        Image(
                            bitmap = albedoBitmap.asImageBitmap(),
                            contentDescription = "Albedo Image Texture Map",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Image Texture (_albedo.png)",
                        style = MaterialTheme.typography.labelLarge,
                        color = DeepNavyInk
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.5.dp, SolarYellowAccent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                    ) {
                        Image(
                            bitmap = noiseBitmap.asImageBitmap(),
                            contentDescription = "Noise Roughness Texture Map",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Noise Texture (_noise.png)",
                        style = MaterialTheme.typography.labelLarge,
                        color = DeepNavyInk
                    )
                }
            }
        }

        // 5. Export FBX + OBJ + Albedo Texture + Noise Texture Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TactilePrimaryButton(
                text = "Export FBX + OBJ + Textures",
                icon = Icons.Default.Download,
                onClick = { onExportPackage(false) },
                pulseType = TactilePulse.EXPORT_SUCCESS,
                modifier = Modifier.weight(1.3f),
                testTagName = "export_fbx_obj_btn"
            )
            TactilePrimaryButton(
                text = "Share Bundle",
                icon = Icons.Default.Share,
                onClick = { onExportPackage(true) },
                isYellowAccent = true,
                pulseType = TactilePulse.EXPORT_SUCCESS,
                modifier = Modifier.weight(1f),
                testTagName = "share_export_bundle_btn"
            )
        }

        if (!exportBannerMessage.isNullOrBlank()) {
            GlossyTranslucentCard(
                containerColor = BrightYellowSurface,
                borderColor = WarmYellowBorder
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Export Complete",
                        tint = CobaltBluePrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = exportBannerMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = DeepNavyInk,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (lastExportedBundle != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Files written: ${lastExportedBundle.fbxFile.name} • ${lastExportedBundle.objFile.name} • ${lastExportedBundle.mtlFile.name} • ${lastExportedBundle.albedoTextureFile.name} • ${lastExportedBundle.noiseTextureFile.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = DeepAmberInk
                    )
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
        val cy = h * 0.46f
        val scale = minOf(w, h) * 0.34f

        // Subtle studio floor grid
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

        // Rotate all vertices first
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

        // Painter's algorithm sort back-to-front by average Z
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

            // Rotate normal for directional studio lighting
            val n = q.normal
            val nrx = n.x * cosY + n.z * sinY
            val nrz1 = -n.x * sinY + n.z * cosY
            val nry = n.y * cosP - nrz1 * sinP
            val nrz = n.y * sinP + nrz1 * cosP
            val lambert = (nrx * lightDir.x + nry * lightDir.y + nrz * lightDir.z).coerceIn(0.18f, 1f)

            val fillColor = when (renderMode) {
                ViewportRenderMode.SHADED_PBR -> {
                    val isTrimRow = (qIdx % 11 == 0)
                    if (isTrimRow) {
                        SolarYellowAccent.copy(alpha = (0.75f + 0.25f * lambert).coerceIn(0f, 1f))
                    } else {
                        Color(
                            red = (0.08f + 0.72f * lambert).coerceIn(0f, 1f),
                            green = (0.32f + 0.56f * lambert).coerceIn(0f, 1f),
                            blue = (0.64f + 0.34f * lambert).coerceIn(0f, 1f),
                            alpha = 0.96f
                        )
                    }
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
                ViewportRenderMode.SHADED_PBR -> PureWhite.copy(alpha = 0.28f)
                else -> DeepNavyInk.copy(alpha = 0.35f)
            }
            drawPath(
                path = path,
                color = strokeColor,
                style = Stroke(width = if (renderMode == ViewportRenderMode.QUAD_WIREFRAME) 2.2f else 1f)
            )
        }

        // Draw 12 Orbital Guidepoints & Lightray Vectors around the 3D Mesh
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

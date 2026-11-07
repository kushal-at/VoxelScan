package com.example.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Vec3(val x: Float, val y: Float, val z: Float)
data class Vec2(val u: Float, val v: Float)

data class QuadFace(
    val v0: Int,
    val v1: Int,
    val v2: Int,
    val v3: Int,
    val normal: Vec3,
    val depthNormalized: Float,
    val sampledColorArgb: Int = 0xFF1565C0.toInt()
)

data class Guidepoint3D(
    val index: Int,
    val label: String,
    val targetAzimuthDeg: Int,
    val targetPitchDeg: Int,
    val position: Vec3,
    val lightrayDir: Vec3,
    val isLocked: Boolean
)

data class Mesh3DData(
    val vertices: List<Vec3>,
    val normals: List<Vec3>,
    val uvs: List<Vec2>,
    val quads: List<QuadFace>,
    val guidepoints: List<Guidepoint3D>
)

/**
 * Represents one real captured orbital camera keyframe with its IMU pose,
 * foreground silhouette profile, monocular Sobel/contrast depth slice, and RGB thumbnail.
 */
data class CapturedScanKeyframe(
    val guidepointIndex: Int, // 1..12
    val azimuthDeg: Int,
    val pitchDeg: Int,
    val rgbBitmap: Bitmap,
    val radialSilhouette16: FloatArray, // 16 vertical slices of subject radius (0.22..1.05)
    val depthRelief16: FloatArray,      // 16 vertical slices of surface depth relief (-0.25..+0.25)
    val dominantLightAzimuth: Int,
    val dominantLightElevation: Int,
    val averageLux: Int,
    val timestampMs: Long = System.currentTimeMillis()
)

data class LiveFrameAnalysisResult(
    val depthGrid8x8: List<Float>,
    val radialSilhouette16: FloatArray,
    val depthRelief16: FloatArray,
    val lightrayAzimuthDeg: Int,
    val lightrayElevationDeg: Int,
    val estimatedLux: Int,
    val foregroundCoveragePercent: Int
)

data class ExportedAssetBundle(
    val assetTitle: String,
    val directory: File,
    val objFile: File,
    val mtlFile: File,
    val fbxFile: File,
    val albedoTextureFile: File,
    val noiseTextureFile: File,
    val vertexCount: Int,
    val quadCount: Int,
    val totalBytes: Long
)

object MeshRetopologyEngine {

    /**
     * Target orbital angles for the 12 Geodesic Guidepoints (every 30° around the object,
     * alternating upper +20° and lower -10° elevation rings for full 3D coverage).
     */
    fun getTargetAzimuthForGuidepoint(gpIndex: Int): Int = ((gpIndex - 1) * 30) % 360
    fun getTargetPitchForGuidepoint(gpIndex: Int): Int = if (gpIndex % 2 == 1) 20 else -10

    /**
     * Analyzes a real upright Bitmap from CameraX ImageAnalysis to extract:
     * 1. An 8x8 monocular depth & Sobel gradient confidence grid for the live HUD
     * 2. A 16-slice vertical foreground silhouette profile of the scanned object
     * 3. A 16-slice surface depth relief vector
     * 4. Dominant incident lightray direction (azimuth, elevation, lux)
     */
    fun analyzeCameraBitmap(bitmap: Bitmap): LiveFrameAnalysisResult {
        val w = bitmap.width.coerceAtLeast(16)
        val h = bitmap.height.coerceAtLeast(16)

        // Sample border pixels to estimate background color & luma
        var bgR = 0f
        var bgG = 0f
        var bgB = 0f
        var borderSamples = 0
        for (i in 0 until 8) {
            val sx = (i * (w - 1)) / 7
            val topPx = bitmap.getPixel(sx, 2.coerceAtMost(h - 1))
            val botPx = bitmap.getPixel(sx, (h - 3).coerceAtLeast(0))
            bgR += Color.red(topPx) + Color.red(botPx)
            bgG += Color.green(topPx) + Color.green(botPx)
            bgB += Color.blue(topPx) + Color.blue(botPx)
            borderSamples += 2
        }
        bgR /= borderSamples
        bgG /= borderSamples
        bgB /= borderSamples

        // 16x16 sampling grid across the central ROI of the camera frame
        val luma16 = FloatArray(16 * 16)
        val fgMask16 = FloatArray(16 * 16)
        var lumaSum = 0f
        var brightestX = 8
        var brightestY = 4
        var maxLuma = -1f
        var fgCount = 0

        for (ry in 0 until 16) {
            val py = ((ry + 0.5f) * h / 16f).toInt().coerceIn(1, h - 2)
            for (rx in 0 until 16) {
                val px = ((rx + 0.5f) * w / 16f).toInt().coerceIn(1, w - 2)
                val c = bitmap.getPixel(px, py)
                val r = Color.red(c).toFloat()
                val g = Color.green(c).toFloat()
                val b = Color.blue(c).toFloat()
                val luma = 0.299f * r + 0.587f * g + 0.114f * b
                luma16[ry * 16 + rx] = luma
                lumaSum += luma

                if (luma > maxLuma) {
                    maxLuma = luma
                    brightestX = rx
                    brightestY = ry
                }

                // Sobel local gradient + background color distance for foreground object segmentation
                val cRight = bitmap.getPixel((px + 2).coerceAtMost(w - 1), py)
                val cDown = bitmap.getPixel(px, (py + 2).coerceAtMost(h - 1))
                val gx = abs(r - Color.red(cRight)) + abs(g - Color.green(cRight)) + abs(b - Color.blue(cRight))
                val gy = abs(r - Color.red(cDown)) + abs(g - Color.green(cDown)) + abs(b - Color.blue(cDown))
                val edgeMag = (gx + gy) / 765f

                val bgDist = sqrt(
                    (r - bgR) * (r - bgR) +
                        (g - bgG) * (g - bgG) +
                        (b - bgB) * (b - bgB)
                ) / 441f

                // Center-weighted subject probability combined with real color contrast & edge gradient
                val normX = (rx - 7.5f) / 7.5f
                val normY = (ry - 7.5f) / 7.5f
                val centerPrior = (1f - 0.45f * (normX * normX + normY * normY)).coerceIn(0.2f, 1f)
                val score = (bgDist * 1.35f + edgeMag * 1.6f) * centerPrior
                val isFg = if (score > 0.18f) 1f else (score / 0.18f).coerceIn(0.15f, 0.95f)
                fgMask16[ry * 16 + rx] = isFg
                if (score > 0.18f) fgCount++
            }
        }

        // Compute 16 vertical slices of radial silhouette width & depth relief from the real frame
        val radialSilhouette16 = FloatArray(16)
        val depthRelief16 = FloatArray(16)
        for (ry in 0 until 16) {
            var leftEdge = 7f
            var rightEdge = 8f
            var rowDepthAcc = 0f
            for (rx in 0 until 16) {
                val fg = fgMask16[ry * 16 + rx]
                val lumaNorm = luma16[ry * 16 + rx] / 255f
                rowDepthAcc += (fg * 0.6f + lumaNorm * 0.4f)
                if (fg > 0.48f) {
                    if (rx < leftEdge) leftEdge = rx.toFloat()
                    if (rx > rightEdge) rightEdge = rx.toFloat()
                }
            }
            val measuredHalfSpan = ((rightEdge - leftEdge + 1f) / 16f).coerceIn(0.26f, 0.98f)
            radialSilhouette16[ry] = measuredHalfSpan
            depthRelief16[ry] = ((rowDepthAcc / 16f) - 0.5f).coerceIn(-0.22f, 0.22f)
        }

        // Downsample 16x16 to 8x8 for the real-time HUD depth contour display
        val depthGrid8x8 = ArrayList<Float>(64)
        for (r8 in 0 until 8) {
            for (c8 in 0 until 8) {
                val r16 = r8 * 2
                val c16 = c8 * 2
                val v = 0.25f * (
                    fgMask16[r16 * 16 + c16] +
                        fgMask16[r16 * 16 + c16 + 1] +
                        fgMask16[(r16 + 1) * 16 + c16] +
                        fgMask16[(r16 + 1) * 16 + c16 + 1]
                    )
                depthGrid8x8.add(v.coerceIn(0.12f, 0.99f))
            }
        }

        val avgLuma = lumaSum / 256f
        val estLux = (avgLuma * 4.2f).toInt().coerceIn(90, 1400)
        val lightAzimuth = ((brightestX / 15f) * 360f).toInt().coerceIn(0, 359)
        val lightElevation = (20 + ((15 - brightestY) / 15f) * 65f).toInt().coerceIn(15, 85)
        val coveragePct = ((fgCount / 256f) * 100f).toInt().coerceIn(18, 99)

        return LiveFrameAnalysisResult(
            depthGrid8x8 = depthGrid8x8,
            radialSilhouette16 = radialSilhouette16,
            depthRelief16 = depthRelief16,
            lightrayAzimuthDeg = lightAzimuth,
            lightrayElevationDeg = lightElevation,
            estimatedLux = estLux,
            foregroundCoveragePercent = coveragePct
        )
    }

    /**
     * Reconstructs a 3D quad-dominant mesh directly from the user's captured multi-angle
     * camera keyframes (radial silhouettes + depth relief maps) and samples real camera colors
     * from the stitched `albedoBitmap` onto every quad face!
     */
    fun buildRetopologizedMesh(
        shapeProfile: String,
        subdivisionsU: Int = 22,
        subdivisionsV: Int = 14,
        lockedGuidepoints: Int = 12,
        capturedKeyframes: List<CapturedScanKeyframe> = emptyList(),
        savedRadialCsv: String = "",
        savedDepthCsv: String = "",
        albedoBitmap: Bitmap? = null
    ): Mesh3DData {
        val vertices = ArrayList<Vec3>((subdivisionsU + 1) * (subdivisionsV + 1))
        val normals = ArrayList<Vec3>((subdivisionsU + 1) * (subdivisionsV + 1))
        val uvs = ArrayList<Vec2>((subdivisionsU + 1) * (subdivisionsV + 1))
        val quads = ArrayList<QuadFace>(subdivisionsU * subdivisionsV)

        // Parse persisted radial & depth matrices if loaded from Room DB
        val parsedRadial12x16 = parseMatrix12x16(savedRadialCsv)
        val parsedDepth12x16 = parseMatrix12x16(savedDepthCsv)
        val hasRealKeyframes = capturedKeyframes.isNotEmpty() || parsedRadial12x16 != null

        for (iv in 0..subdivisionsV) {
            val vRatio = iv.toFloat() / subdivisionsV.toFloat() // 0 (bottom) .. 1 (top)
            val y = (vRatio - 0.5f) * 2.05f
            for (iu in 0..subdivisionsU) {
                val uRatio = iu.toFloat() / subdivisionsU.toFloat()
                val theta = uRatio * 2f * PI.toFloat()

                val pos = if (hasRealKeyframes) {
                    computeRealScannedVertex(
                        uRatio = uRatio,
                        vRatio = vRatio,
                        theta = theta,
                        y = y,
                        keyframes = capturedKeyframes,
                        savedRadial12x16 = parsedRadial12x16,
                        savedDepth12x16 = parsedDepth12x16
                    )
                } else {
                    computeProfileVertex(shapeProfile, uRatio, vRatio, theta, y)
                }

                vertices.add(pos)

                val len = sqrt(pos.x * pos.x + pos.y * pos.y + pos.z * pos.z).coerceAtLeast(0.001f)
                normals.add(Vec3(pos.x / len, pos.y / len, pos.z / len))
                uvs.add(Vec2(uRatio, 1f - vRatio))
            }
        }

        val stride = subdivisionsU + 1
        val bmpW = albedoBitmap?.width ?: 0
        val bmpH = albedoBitmap?.height ?: 0

        for (iv in 0 until subdivisionsV) {
            for (iu in 0 until subdivisionsU) {
                val i0 = iv * stride + iu
                val i1 = i0 + 1
                val i2 = (iv + 1) * stride + iu + 1
                val i3 = (iv + 1) * stride + iu

                val p0 = vertices[i0]
                val p1 = vertices[i1]
                val p2 = vertices[i2]
                val p3 = vertices[i3]

                val cz = (p0.z + p1.z + p2.z + p3.z) * 0.25f

                // Compute face normal via cross product
                val ux = p1.x - p0.x
                val uy = p1.y - p0.y
                val uz = p1.z - p0.z
                val vx = p3.x - p0.x
                val vy = p3.y - p0.y
                val vz = p3.z - p0.z

                var nx = uy * vz - uz * vy
                var ny = uz * vx - ux * vz
                var nz = ux * vy - uy * vx
                val nLen = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.0001f)
                nx /= nLen
                ny /= nLen
                nz /= nLen

                // Sample real RGB pixel color from the UV Albedo Bitmap at this quad's center
                val centerU = (iu + 0.5f) / subdivisionsU.toFloat()
                val centerV = 1f - ((iv + 0.5f) / subdivisionsV.toFloat())
                val sampledArgb = if (albedoBitmap != null && bmpW > 0 && bmpH > 0) {
                    val tx = (centerU * (bmpW - 1)).toInt().coerceIn(0, bmpW - 1)
                    val ty = (centerV * (bmpH - 1)).toInt().coerceIn(0, bmpH - 1)
                    albedoBitmap.getPixel(tx, ty)
                } else {
                    0xFF1565C0.toInt()
                }

                val depthNorm = ((cz + 1.2f) / 2.4f).coerceIn(0f, 1f)
                quads.add(
                    QuadFace(
                        v0 = i0,
                        v1 = i1,
                        v2 = i2,
                        v3 = i3,
                        normal = Vec3(nx, ny, nz),
                        depthNormalized = depthNorm,
                        sampledColorArgb = sampledArgb
                    )
                )
            }
        }

        // Generate 12 orbital geodesic guidepoints around the mesh
        val lockedIndices = capturedKeyframes.map { it.guidepointIndex }.toSet()
        val guidepoints = (0 until 12).map { idx ->
            val gpNumber = idx + 1
            val targetAz = getTargetAzimuthForGuidepoint(gpNumber)
            val targetPitch = getTargetPitchForGuidepoint(gpNumber)
            val angle = (targetAz / 180f) * PI.toFloat()
            val ringY = if (idx % 2 == 0) 0.32f else -0.24f
            val radius = 1.38f
            val gx = cos(angle) * radius
            val gz = sin(angle) * radius
            val dirLen = sqrt(gx * gx + ringY * ringY + gz * gz).coerceAtLeast(0.001f)
            val isGpLocked = if (lockedIndices.isNotEmpty()) {
                gpNumber in lockedIndices
            } else {
                idx < lockedGuidepoints
            }
            Guidepoint3D(
                index = gpNumber,
                label = "GP-${String.format(Locale.US, "%02d", gpNumber)}",
                targetAzimuthDeg = targetAz,
                targetPitchDeg = targetPitch,
                position = Vec3(gx, ringY, gz),
                lightrayDir = Vec3(-gx / dirLen, -ringY / dirLen, -gz / dirLen),
                isLocked = isGpLocked
            )
        }

        return Mesh3DData(
            vertices = vertices,
            normals = normals,
            uvs = uvs,
            quads = quads,
            guidepoints = guidepoints
        )
    }

    /**
     * Multi-View Silhouette + Monocular Depth Carving solver:
     * Interpolates the measured foreground radius and surface relief across the 12 orbital angles
     * and 16 vertical slices captured from the user's real camera keyframes.
     */
    private fun computeRealScannedVertex(
        uRatio: Float,
        vRatio: Float,
        theta: Float,
        y: Float,
        keyframes: List<CapturedScanKeyframe>,
        savedRadial12x16: Array<FloatArray>?,
        savedDepth12x16: Array<FloatArray>?
    ): Vec3 {
        val sliceF = ((1f - vRatio) * 15f).coerceIn(0f, 15f)
        val s0 = sliceF.toInt().coerceIn(0, 15)
        val s1 = (s0 + 1).coerceIn(0, 15)
        val sf = sliceF - s0

        val sectorF = (uRatio * 12f) % 12f
        val k0 = sectorF.toInt().coerceIn(0, 11)
        val k1 = (k0 + 1) % 12
        val kf = sectorF - k0

        val r0: Float
        val r1: Float
        val d0: Float
        val d1: Float

        if (savedRadial12x16 != null && savedDepth12x16 != null) {
            r0 = savedRadial12x16[k0][s0] * (1f - sf) + savedRadial12x16[k0][s1] * sf
            r1 = savedRadial12x16[k1][s0] * (1f - sf) + savedRadial12x16[k1][s1] * sf
            d0 = savedDepth12x16[k0][s0] * (1f - sf) + savedDepth12x16[k0][s1] * sf
            d1 = savedDepth12x16[k1][s0] * (1f - sf) + savedDepth12x16[k1][s1] * sf
        } else {
            val kf0 = findClosestKeyframe(keyframes, k0 * 30)
            val kf1 = findClosestKeyframe(keyframes, k1 * 30)
            r0 = kf0.radialSilhouette16[s0] * (1f - sf) + kf0.radialSilhouette16[s1] * sf
            r1 = kf1.radialSilhouette16[s0] * (1f - sf) + kf1.radialSilhouette16[s1] * sf
            d0 = kf0.depthRelief16[s0] * (1f - sf) + kf0.depthRelief16[s1] * sf
            d1 = kf1.depthRelief16[s0] * (1f - sf) + kf1.depthRelief16[s1] * sf
        }

        // Smooth cosine interpolation between adjacent orbital views
        val smoothT = (1f - cos(kf * PI.toFloat())) * 0.5f
        val interpolatedRadius = r0 * (1f - smoothT) + r1 * smoothT
        val interpolatedRelief = d0 * (1f - smoothT) + d1 * smoothT

        // Taper top/bottom poles smoothly to form a watertight closed manifold
        val poleEnvelope = sin(vRatio * PI.toFloat()).coerceAtLeast(0.18f)
        val finalRadius = ((interpolatedRadius + interpolatedRelief * 0.45f) * (0.35f + 0.65f * poleEnvelope))
            .coerceIn(0.18f, 1.08f)

        return Vec3(
            x = cos(theta) * finalRadius,
            y = y * 0.92f,
            z = sin(theta) * finalRadius
        )
    }

    private fun findClosestKeyframe(
        keyframes: List<CapturedScanKeyframe>,
        targetAzimuthDeg: Int
    ): CapturedScanKeyframe {
        return keyframes.minByOrNull { kf ->
            val diff = abs(kf.azimuthDeg - targetAzimuthDeg) % 360
            if (diff > 180) 360 - diff else diff
        } ?: keyframes.first()
    }

    fun encodeKeyframesRadialCsv(keyframes: List<CapturedScanKeyframe>): String {
        if (keyframes.isEmpty()) return ""
        return (0 until 12).joinToString(";") { sector ->
            val kf = findClosestKeyframe(keyframes, sector * 30)
            kf.radialSilhouette16.joinToString(",") { String.format(Locale.US, "%.3f", it) }
        }
    }

    fun encodeKeyframesDepthCsv(keyframes: List<CapturedScanKeyframe>): String {
        if (keyframes.isEmpty()) return ""
        return (0 until 12).joinToString(";") { sector ->
            val kf = findClosestKeyframe(keyframes, sector * 30)
            kf.depthRelief16.joinToString(",") { String.format(Locale.US, "%.3f", it) }
        }
    }

    private fun parseMatrix12x16(csv: String): Array<FloatArray>? {
        if (csv.isBlank()) return null
        val rows = csv.split(";")
        if (rows.size < 12) return null
        return Array(12) { r ->
            val cols = rows[r].split(",")
            FloatArray(16) { c ->
                cols.getOrNull(c)?.toFloatOrNull() ?: 0.65f
            }
        }
    }

    /**
     * Stitches the user's real captured camera RGB keyframes across the 512x512 cylindrical UV
     * map (`_albedo.png`) with horizontal feathering between orbital views!
     */
    fun stitchCameraKeyframesToAlbedo(
        keyframes: List<CapturedScanKeyframe>,
        size: Int = 512
    ): Bitmap {
        if (keyframes.isEmpty()) {
            return generateAlbedoTextureBitmap("#0A4D9C", "#F5B800", size)
        }
        val atlas = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val sorted = keyframes.sortedBy { it.azimuthDeg }
        val numViews = sorted.size
        val stripWidth = size.toFloat() / numViews.toFloat()
        val canvas = Canvas(atlas)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // Draw each captured camera frame into its corresponding UV azimuth column
        for ((idx, kf) in sorted.withIndex()) {
            val src = kf.rgbBitmap
            // Crop central 75% of the camera frame where the scanned subject sits
            val cropMarginX = (src.width * 0.12f).toInt()
            val cropMarginY = (src.height * 0.12f).toInt()
            val srcRect = Rect(
                cropMarginX,
                cropMarginY,
                (src.width - cropMarginX).coerceAtLeast(cropMarginX + 1),
                (src.height - cropMarginY).coerceAtLeast(cropMarginY + 1)
            )
            val dstLeft = (idx * stripWidth).toInt()
            val dstRight = ((idx + 1) * stripWidth + 2f).toInt().coerceAtMost(size)
            val dstRect = Rect(dstLeft, 0, dstRight, size)
            canvas.drawBitmap(src, srcRect, dstRect, paint)
        }

        // Smooth horizontal seam blending across strip boundaries
        val blendRadius = 8
        for (idx in 1 until numViews) {
            val seamX = (idx * stripWidth).toInt().coerceIn(blendRadius, size - blendRadius - 1)
            for (y in 0 until size) {
                val leftColor = atlas.getPixel(seamX - blendRadius, y)
                val rightColor = atlas.getPixel(seamX + blendRadius, y)
                for (dx in -blendRadius..blendRadius) {
                    val t = (dx + blendRadius).toFloat() / (2f * blendRadius)
                    val r = (Color.red(leftColor) * (1f - t) + Color.red(rightColor) * t).toInt()
                    val g = (Color.green(leftColor) * (1f - t) + Color.green(rightColor) * t).toInt()
                    val b = (Color.blue(leftColor) * (1f - t) + Color.blue(rightColor) * t).toInt()
                    atlas.setPixel(seamX + dx, y, Color.rgb(r, g, b))
                }
            }
        }

        return atlas
    }

    /**
     * Extracts a real 512x512 high-frequency Micro-Surface Noise & Roughness Map (`_noise.png`)
     * directly from the captured camera Albedo Bitmap using a 3x3 Laplacian high-pass filter
     * blended with multi-octave micro-grain!
     */
    fun extractHighFrequencyNoiseFromAlbedo(
        albedoBitmap: Bitmap,
        noiseScale: Float = 1.25f,
        seed: Int = 42,
        size: Int = 512
    ): Bitmap {
        val scaledSource = if (albedoBitmap.width == size && albedoBitmap.height == size) {
            albedoBitmap
        } else {
            Bitmap.createScaledBitmap(albedoBitmap, size, size, true)
        }

        val srcPixels = IntArray(size * size)
        scaledSource.getPixels(srcPixels, 0, size, 0, 0, size, size)

        val luma = FloatArray(size * size)
        for (i in srcPixels.indices) {
            val px = srcPixels[i]
            luma[i] = (0.299f * ((px shr 16) and 0xFF) +
                0.587f * ((px shr 8) and 0xFF) +
                0.114f * (px and 0xFF)) / 255f
        }

        val outPixels = IntArray(size * size)
        val freq = 0.05f * noiseScale.coerceIn(0.5f, 3.0f)

        for (y in 0 until size) {
            val ym = (y - 1).coerceAtLeast(0)
            val yp = (y + 1).coerceAtMost(size - 1)
            val ny = y * freq
            for (x in 0 until size) {
                val xm = (x - 1).coerceAtLeast(0)
                val xp = (x + 1).coerceAtMost(size - 1)
                val center = luma[y * size + x]
                val neighbors = 0.25f * (
                    luma[y * size + xm] +
                        luma[y * size + xp] +
                        luma[ym * size + x] +
                        luma[yp * size + x]
                    )
                // High-pass surface detail from the real camera photograph
                val highPass = ((center - neighbors) * 3.8f + 0.5f).coerceIn(0f, 1f)
                val microGrain = valueNoise(x * freq, ny, seed)
                val combined = (highPass * 0.72f + microGrain * 0.28f).coerceIn(0f, 1f)
                val v = (combined * 255f).toInt().coerceIn(0, 255)
                outPixels[y * size + x] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
        }

        val noiseBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        noiseBmp.setPixels(outPixels, 0, size, 0, 0, size, size)
        return noiseBmp
    }

    fun saveScanTexturesToInternalStorage(
        context: Context,
        scanIdSlug: String,
        albedoBitmap: Bitmap,
        noiseBitmap: Bitmap
    ): Pair<String, String> {
        val dir = File(context.filesDir, "scans").apply { if (!exists()) mkdirs() }
        val albedoFile = File(dir, "${scanIdSlug}_albedo.png")
        val noiseFile = File(dir, "${scanIdSlug}_noise.png")
        FileOutputStream(albedoFile).use { albedoBitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileOutputStream(noiseFile).use { noiseBitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return albedoFile.absolutePath to noiseFile.absolutePath
    }

    fun loadBitmapOrNull(path: String): Bitmap? {
        if (path.isBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    private fun computeProfileVertex(
        shapeProfile: String,
        uRatio: Float,
        vRatio: Float,
        theta: Float,
        y: Float
    ): Vec3 {
        return when (shapeProfile) {
            "MODULAR_CRATE" -> {
                val cosT = cos(theta)
                val sinT = sin(theta)
                val rBase = 0.78f / (abs(cosT).coerceAtLeast(0.55f).let {
                    maxOf(abs(cosT), abs(sinT)).coerceAtLeast(0.65f)
                })
                val rib = if ((vRatio * 10f).toInt() % 3 == 0) 1.05f else 0.96f
                val taper = 1f - 0.12f * abs(vRatio - 0.5f)
                val r = rBase * rib * taper
                Vec3(cosT * r, y * 0.85f, sinT * r)
            }
            "ORGANIC_BOULDER" -> {
                val radiusProfile = sin(vRatio * PI.toFloat()).coerceAtLeast(0.18f)
                val facetNoise = 1f +
                    0.16f * sin(theta * 3f + vRatio * 4f) +
                    0.09f * cos(theta * 5f - vRatio * 6f)
                val r = 0.88f * radiusProfile * facetNoise
                Vec3(cos(theta) * r, y * 0.82f, sin(theta) * r)
            }
            "CUSTOM_VIDEO_SCAN" -> {
                val flange = 0.68f + 0.24f * cos(vRatio * 2.5f * PI.toFloat()) +
                    0.08f * cos(theta * 6f)
                val capScale = sin(vRatio * PI.toFloat()).coerceAtLeast(0.28f)
                val r = flange * (0.45f + 0.55f * capScale)
                Vec3(cos(theta) * r, y * 0.9f, sin(theta) * r)
            }
            else -> {
                val belly = 0.52f + 0.38f * sin(vRatio * PI.toFloat()) -
                    0.22f * sin(vRatio * 2f * PI.toFloat()) +
                    0.12f * cos((vRatio - 0.88f) * 4f * PI.toFloat()).coerceAtLeast(0f)
                val fluting = 1f + 0.03f * cos(theta * 12f)
                val r = belly.coerceIn(0.26f, 0.96f) * fluting
                Vec3(cos(theta) * r, y * 0.95f, sin(theta) * r)
            }
        }
    }

    fun generateAlbedoTextureBitmap(
        primaryHex: String = "#0A4D9C",
        secondaryHex: String = "#F5B800",
        size: Int = 512
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val primaryColor = runCatching { Color.parseColor(primaryHex) }.getOrDefault(0xFF0A4D9C.toInt())
        val secondaryColor = runCatching { Color.parseColor(secondaryHex) }.getOrDefault(0xFFF5B800.toInt())

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.rgb(244, 248, 254)
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

        val bandHeight = size / 8f
        for (i in 0 until 8) {
            paint.color = if (i % 2 == 0) primaryColor else Color.rgb(228, 238, 252)
            canvas.drawRect(0f, i * bandHeight, size.toFloat(), (i + 0.65f) * bandHeight, paint)
        }

        paint.color = secondaryColor
        canvas.drawRect(0f, size * 0.18f, size.toFloat(), size * 0.24f, paint)
        canvas.drawRect(0f, size * 0.72f, size.toFloat(), size * 0.78f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.argb(70, 10, 77, 156)
        val step = size / 8f
        for (g in 1..7) {
            canvas.drawLine(g * step, 0f, g * step, size.toFloat(), paint)
            canvas.drawLine(0f, g * step, size.toFloat(), g * step, paint)
        }

        return bitmap
    }

    fun generateNoiseTextureBitmap(
        noiseScale: Float = 1.25f,
        seed: Int = 42,
        size: Int = 512
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size)
        val freq = 0.045f * noiseScale.coerceIn(0.4f, 3.0f)

        for (y in 0 until size) {
            val ny = y * freq
            for (x in 0 until size) {
                val nx = x * freq
                val o1 = valueNoise(nx, ny, seed)
                val o2 = valueNoise(nx * 2.1f, ny * 2.1f, seed + 101) * 0.5f
                val o3 = valueNoise(nx * 4.3f, ny * 4.3f, seed + 307) * 0.25f
                val combined = ((o1 + o2 + o3) / 1.75f).coerceIn(0f, 1f)
                val luma = (combined * 255f).toInt().coerceIn(0, 255)
                val r = luma
                val g = (luma * 0.97f + 6f).toInt().coerceIn(0, 255)
                val b = (luma * 0.94f + 14f).toInt().coerceIn(0, 255)
                pixels[y * size + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }

    private fun valueNoise(x: Float, y: Float, seed: Int): Float {
        val xi = x.toInt()
        val yi = y.toInt()
        val xf = x - xi
        val yf = y - yi
        val u = xf * xf * (3f - 2f * xf)
        val v = yf * yf * (3f - 2f * yf)

        val n00 = hash2D(xi, yi, seed)
        val n10 = hash2D(xi + 1, yi, seed)
        val n01 = hash2D(xi, yi + 1, seed)
        val n11 = hash2D(xi + 1, yi + 1, seed)

        val nx0 = n00 + u * (n10 - n00)
        val nx1 = n01 + u * (n11 - n01)
        return nx0 + v * (nx1 - nx0)
    }

    private fun hash2D(x: Int, y: Int, seed: Int): Float {
        var n = x * 374761393 + y * 668265263 + seed * 1442695041
        n = (n xor (n ushr 13)) * 1274126177
        n = n xor (n ushr 16)
        return (n and 0x7fffffff).toFloat() / Int.MAX_VALUE.toFloat()
    }

    fun exportGameEnginePackage(
        context: Context,
        assetTitle: String,
        targetEngine: String,
        mesh: Mesh3DData,
        albedoBitmap: Bitmap,
        noiseBitmap: Bitmap
    ): ExportedAssetBundle {
        return com.example.util.MeshExportFileService.exportGameEnginePackage(
            context = context,
            assetTitle = assetTitle,
            targetEngine = targetEngine,
            mesh = mesh,
            albedoBitmap = albedoBitmap,
            noiseBitmap = noiseBitmap
        )
    }
}

package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.core.content.FileProvider
import com.example.domain.ExportedAssetBundle
import com.example.domain.Mesh3DData
import com.example.domain.Vec2
import com.example.domain.Vec3
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.math.sqrt

/**
 * Dedicated File Utility Service for exporting retopologized 3D meshes and captured camera textures
 * into game-engine-ready Autodesk `.fbx` (ASCII 7.4.0) and Wavefront `.obj` + `.mtl` formats,
 * bundled with PNG Albedo (`_albedo.png`) and Laplacian Micro-Surface Noise (`_noise.png`) maps.
 *
 * Preserves:
 * - 3D vertex positions transformed for the target engine's coordinate system (UE5 Z-Up vs Unity Y-Up)
 * - Quad-dominant polygon topology (`f v/vt/vn` in OBJ, bitwise-negated 4th quad index in FBX)
 * - Indexed UV mapping (`LayerElementUV` with `IndexToDirect` `UV` + `UVIndex` arrays in FBX and `vt` in OBJ)
 * - Vertex normals (`LayerElementNormal`) and UV-aligned tangents (`LayerElementTangent`)
 * - Baked vertex colors (`LayerElementColor`) sampled from the projected camera Albedo texture
 */
object MeshExportFileService {

    private const val TAG = "MeshExportFileService"

    /**
     * Sanitizes a human-readable asset title into a filesystem- and shader-safe slug.
     */
    fun sanitizeAssetSlug(assetTitle: String): String {
        return assetTitle
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifEmpty { "voxelscan_model" }
    }

    /**
     * Exports a complete 5-file game engine package (`.fbx`, `.obj`, `.mtl`, `_albedo.png`, `_noise.png`)
     * into `context.filesDir/exports/<sanitized_slug>/`.
     */
    fun exportGameEnginePackage(
        context: Context,
        assetTitle: String,
        targetEngine: String,
        mesh: Mesh3DData,
        albedoBitmap: Bitmap,
        noiseBitmap: Bitmap
    ): ExportedAssetBundle {
        val sanitizedName = sanitizeAssetSlug(assetTitle)
        val exportDir = File(context.filesDir, "exports/$sanitizedName").apply {
            if (!exists()) mkdirs()
        }

        val albedoFile = File(exportDir, "${sanitizedName}_albedo.png")
        val noiseFile = File(exportDir, "${sanitizedName}_noise.png")
        val mtlFile = File(exportDir, "${sanitizedName}.mtl")
        val objFile = File(exportDir, "${sanitizedName}.obj")
        val fbxFile = File(exportDir, "${sanitizedName}.fbx")

        // 1. Write lossless PNG Albedo & Noise/Roughness textures
        FileOutputStream(albedoFile).use { out ->
            albedoBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        FileOutputStream(noiseFile).use { out ->
            noiseBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        // 2. Write Wavefront MTL material library
        val mtlContent = buildMtlContent(
            sanitizedName = sanitizedName,
            targetEngine = targetEngine,
            albedoFileName = albedoFile.name,
            noiseFileName = noiseFile.name
        )
        writeUtf8File(mtlFile, mtlContent)

        // 3. Write Wavefront OBJ quad mesh with preserved vertex, UV, and normal indices
        val objContent = buildObjContent(
            sanitizedName = sanitizedName,
            targetEngine = targetEngine,
            mesh = mesh,
            mtlFileName = mtlFile.name
        )
        writeUtf8File(objFile, objContent)

        // 4. Write Autodesk FBX 7.4.0 ASCII with full UVIndex, Normals, Tangents, Vertex Colors & Material Connections
        val fbxContent = buildFbxAscii74Content(
            sanitizedName = sanitizedName,
            targetEngine = targetEngine,
            mesh = mesh,
            albedoBitmap = albedoBitmap,
            albedoFileName = albedoFile.name,
            noiseFileName = noiseFile.name
        )
        writeUtf8File(fbxFile, fbxContent)

        val totalSize = albedoFile.length() +
            noiseFile.length() +
            mtlFile.length() +
            objFile.length() +
            fbxFile.length()

        return ExportedAssetBundle(
            assetTitle = assetTitle,
            directory = exportDir,
            objFile = objFile,
            mtlFile = mtlFile,
            fbxFile = fbxFile,
            albedoTextureFile = albedoFile,
            noiseTextureFile = noiseFile,
            vertexCount = mesh.vertices.size,
            quadCount = mesh.quads.size,
            totalBytes = totalSize
        )
    }

    /**
     * Generates a Wavefront `.mtl` PBR material definition referencing both the captured
     * camera Albedo texture (`map_Kd`) and the high-pass Noise/Roughness/Bump map (`map_Ns`, `map_Pr`, `map_Bump`).
     */
    fun buildMtlContent(
        sanitizedName: String,
        targetEngine: String,
        albedoFileName: String,
        noiseFileName: String
    ): String = buildString {
        appendLine("# VoxelScan 3D Material Library ($targetEngine Preset)")
        appendLine("newmtl ${sanitizedName}_PBR_Mat")
        appendLine("Ka 1.000 1.000 1.000")
        appendLine("Kd 0.960 0.960 0.960")
        appendLine("Ks 0.220 0.220 0.220")
        appendLine("Ns 64.000")
        appendLine("d 1.000")
        appendLine("illum 2")
        appendLine("Pr 0.450")
        appendLine("Pm 0.050")
        appendLine("map_Kd $albedoFileName")
        appendLine("map_Ns $noiseFileName")
        appendLine("map_Pr $noiseFileName")
        appendLine("map_Bump -bm 0.35 $noiseFileName")
    }

    /**
     * Generates a Wavefront `.obj` file preserving 3D vertex coordinates (`v`),
     * normalized UV coordinates (`vt`), unit vertex normals (`vn`), and 1-based quad faces
     * (`f v1/vt1/vn1 v2/vt2/vn2 v3/vt3/vn3 v4/vt4/vn4`).
     */
    fun buildObjContent(
        sanitizedName: String,
        targetEngine: String,
        mesh: Mesh3DData,
        mtlFileName: String
    ): String {
        val isUnrealZUp = targetEngine == "UNREAL_ENGINE_5"
        val vertexCount = mesh.vertices.size
        val uvCount = mesh.uvs.size.coerceAtLeast(1)
        val normalCount = mesh.normals.size.coerceAtLeast(1)

        return buildString(capacity = (vertexCount * 80) + (mesh.quads.size * 48) + 512) {
            appendLine("# VoxelScan 3D Retopologized Quad-Dominant Mesh")
            appendLine("# Target Game Engine: $targetEngine (${if (isUnrealZUp) "Z-Up Coordinate Space" else "Y-Up Coordinate Space"})")
            appendLine("# Vertices: ${mesh.vertices.size} | UVs: ${mesh.uvs.size} | Normals: ${mesh.normals.size} | Quads: ${mesh.quads.size}")
            appendLine("mtllib $mtlFileName")
            appendLine("o $sanitizedName")
            appendLine("g ${sanitizedName}_LOD0")

            // 1. Vertex Positions (transformed to Z-Up if targeting Unreal Engine 5)
            for (v in mesh.vertices) {
                val tv = transformPositionForEngine(v, isUnrealZUp)
                appendLine(String.format(Locale.US, "v %.5f %.5f %.5f", tv.x, tv.y, tv.z))
            }

            // 2. Texture UV Coordinates (clamped to [0.0, 1.0] UV space)
            for (uv in mesh.uvs) {
                val clampedU = uv.u.coerceIn(0f, 1f)
                val clampedV = uv.v.coerceIn(0f, 1f)
                appendLine(String.format(Locale.US, "vt %.5f %.5f", clampedU, clampedV))
            }

            // 3. Vertex Normals (unit length, transformed to match engine up-axis)
            for (n in mesh.normals) {
                val tn = normalizeVec3(transformDirectionForEngine(n, isUnrealZUp))
                appendLine(String.format(Locale.US, "vn %.5f %.5f %.5f", tn.x, tn.y, tn.z))
            }

            appendLine("usemtl ${sanitizedName}_PBR_Mat")
            appendLine("s 1")

            // 4. Quad Faces with explicit v/vt/vn 1-based indexing
            for (q in mesh.quads) {
                val v0 = q.v0.coerceIn(0, vertexCount - 1) + 1
                val v1 = q.v1.coerceIn(0, vertexCount - 1) + 1
                val v2 = q.v2.coerceIn(0, vertexCount - 1) + 1
                val v3 = q.v3.coerceIn(0, vertexCount - 1) + 1

                val vt0 = q.v0.coerceIn(0, uvCount - 1) + 1
                val vt1 = q.v1.coerceIn(0, uvCount - 1) + 1
                val vt2 = q.v2.coerceIn(0, uvCount - 1) + 1
                val vt3 = q.v3.coerceIn(0, uvCount - 1) + 1

                val vn0 = q.v0.coerceIn(0, normalCount - 1) + 1
                val vn1 = q.v1.coerceIn(0, normalCount - 1) + 1
                val vn2 = q.v2.coerceIn(0, normalCount - 1) + 1
                val vn3 = q.v3.coerceIn(0, normalCount - 1) + 1

                appendLine("f $v0/$vt0/$vn0 $v1/$vt1/$vn1 $v2/$vt2/$vn2 $v3/$vt3/$vn3")
            }
        }
    }

    /**
     * Generates an Autodesk `.fbx` (ASCII 7.4.0) file containing:
     * - Engine-specific `GlobalSettings` (`UpAxis`, `FrontAxis`, `UnitScaleFactor`)
     * - `Definitions` for `Model`, `Geometry`, `Material`, `Texture`, and `Video`
     * - `Geometry` with `Vertices`, `PolygonVertexIndex` (bitwise-negated 4th quad index),
     *   `LayerElementNormal`, `LayerElementTangent` (for normal/bump map compatibility),
     *   `LayerElementUV` (`ByPolygonVertex` `IndexToDirect` with explicit `UV` and `UVIndex`),
     *   `LayerElementColor` (sampled from the Albedo bitmap), and `LayerElementMaterial`
     * - `Connections` wiring geometry, PBR material, and Albedo/Noise texture clips.
     */
    fun buildFbxAscii74Content(
        sanitizedName: String,
        targetEngine: String,
        mesh: Mesh3DData,
        albedoBitmap: Bitmap? = null,
        albedoFileName: String,
        noiseFileName: String
    ): String {
        val isUnrealZUp = targetEngine == "UNREAL_ENGINE_5"
        val upAxis = if (isUnrealZUp) 2 else 1
        val frontAxis = if (isUnrealZUp) 1 else 2
        val unitScale = if (isUnrealZUp) 100.0 else 1.0

        val vertexFloats = mesh.vertices.joinToString(",") { v ->
            val tv = transformPositionForEngine(v, isUnrealZUp)
            String.format(Locale.US, "%.5f,%.5f,%.5f", tv.x, tv.y, tv.z)
        }

        // In FBX PolygonVertexIndex, the last vertex of each polygon is encoded as (-index - 1)
        val polyIndices = mesh.quads.joinToString(",") { q ->
            "${q.v0},${q.v1},${q.v2},${-(q.v3) - 1}"
        }

        val normalFloats = mesh.normals.joinToString(",") { n ->
            val tn = normalizeVec3(transformDirectionForEngine(n, isUnrealZUp))
            String.format(Locale.US, "%.5f,%.5f,%.5f", tn.x, tn.y, tn.z)
        }

        // Compute UV-aligned per-vertex tangents so Unreal Engine 5 & Unity import normal/bump maps cleanly
        val tangents = computeVertexTangents(mesh.vertices, mesh.normals, mesh.uvs, isUnrealZUp)
        val tangentFloats = tangents.joinToString(",") { t ->
            String.format(Locale.US, "%.5f,%.5f,%.5f", t.x, t.y, t.z)
        }

        val uvFloats = mesh.uvs.joinToString(",") { uv ->
            String.format(Locale.US, "%.5f,%.5f", uv.u.coerceIn(0f, 1f), uv.v.coerceIn(0f, 1f))
        }

        // ByPolygonVertex + IndexToDirect UVIndex guarantees exact UV mapping across every quad corner
        val uvCount = mesh.uvs.size.coerceAtLeast(1)
        val uvIndices = mesh.quads.joinToString(",") { q ->
            val u0 = q.v0.coerceIn(0, uvCount - 1)
            val u1 = q.v1.coerceIn(0, uvCount - 1)
            val u2 = q.v2.coerceIn(0, uvCount - 1)
            val u3 = q.v3.coerceIn(0, uvCount - 1)
            "$u0,$u1,$u2,$u3"
        }

        // Sample per-vertex RGBA color from the projected camera Albedo bitmap
        val vertexColorsFloats = buildVertexColorFloatList(mesh.uvs, albedoBitmap)

        return """
; FBX 7.4.0 project file
; Generated by VoxelScan 3D Android CGPU Photogrammetry Pipeline
; Target Engine Profile: $targetEngine
FBXHeaderExtension:  {
    FBXHeaderVersion: 1003
    FBXVersion: 7400
    Creator: "VoxelScan 3D Android CGPU Pipeline"
}
GlobalSettings:  {
    Version: 1000
    Properties70:  {
        P: "UpAxis", "int", "Integer", "",$upAxis
        P: "UpAxisSign", "int", "Integer", "",1
        P: "FrontAxis", "int", "Integer", "",$frontAxis
        P: "FrontAxisSign", "int", "Integer", "",1
        P: "CoordAxis", "int", "Integer", "",0
        P: "CoordAxisSign", "int", "Integer", "",1
        P: "UnitScaleFactor", "double", "Number", "",$unitScale
        P: "OriginalUnitScaleFactor", "double", "Number", "",$unitScale
    }
}
Documents:  {
    Count: 1
    Document: 800001, "$sanitizedName", "Scene" {
        RootNode: 0
    }
}
Definitions:  {
    Version: 100
    Count: 6
    ObjectType: "GlobalSettings" {
        Count: 1
    }
    ObjectType: "Model" {
        Count: 1
    }
    ObjectType: "Geometry" {
        Count: 1
    }
    ObjectType: "Material" {
        Count: 1
    }
    ObjectType: "Texture" {
        Count: 2
    }
    ObjectType: "Video" {
        Count: 2
    }
}
Objects:  {
    Geometry: 100001, "Geometry::$sanitizedName", "Mesh" {
        GeometryVersion: 124
        Vertices: *${mesh.vertices.size * 3} {
            a: $vertexFloats
        }
        PolygonVertexIndex: *${mesh.quads.size * 4} {
            a: $polyIndices
        }
        LayerElementNormal: 0 {
            Version: 101
            Name: "Normals"
            MappingInformationType: "ByVertice"
            ReferenceInformationType: "Direct"
            Normals: *${mesh.normals.size * 3} {
                a: $normalFloats
            }
        }
        LayerElementTangent: 0 {
            Version: 101
            Name: "Tangents"
            MappingInformationType: "ByVertice"
            ReferenceInformationType: "Direct"
            Tangents: *${tangents.size * 3} {
                a: $tangentFloats
            }
        }
        LayerElementUV: 0 {
            Version: 101
            Name: "UVChannel_1"
            MappingInformationType: "ByPolygonVertex"
            ReferenceInformationType: "IndexToDirect"
            UV: *${mesh.uvs.size * 2} {
                a: $uvFloats
            }
            UVIndex: *${mesh.quads.size * 4} {
                a: $uvIndices
            }
        }
        LayerElementColor: 0 {
            Version: 101
            Name: "VertexColors"
            MappingInformationType: "ByVertice"
            ReferenceInformationType: "Direct"
            Colors: *${mesh.uvs.size * 4} {
                a: $vertexColorsFloats
            }
        }
        LayerElementMaterial: 0 {
            Version: 101
            Name: "MaterialAssignment"
            MappingInformationType: "AllSame"
            ReferenceInformationType: "IndexToDirect"
            Materials: *1 {
                a: 0
            }
        }
        Layer: 0 {
            Version: 100
            LayerElement:  {
                Type: "LayerElementNormal"
                TypedIndex: 0
            }
            LayerElement:  {
                Type: "LayerElementTangent"
                TypedIndex: 0
            }
            LayerElement:  {
                Type: "LayerElementUV"
                TypedIndex: 0
            }
            LayerElement:  {
                Type: "LayerElementColor"
                TypedIndex: 0
            }
            LayerElement:  {
                Type: "LayerElementMaterial"
                TypedIndex: 0
            }
        }
    }
    Model: 200001, "Model::$sanitizedName", "Mesh" {
        Version: 232
        Properties70:  {
            P: "RotationActive", "bool", "", "",1
            P: "InheritType", "enum", "", "",1
            P: "ScalingMax", "Vector3D", "Vector", "",0,0,0
            P: "DefaultAttributeIndex", "int", "Integer", "",0
        }
        Shading: Y
        Culling: "CullingOff"
    }
    Material: 300001, "Material::${sanitizedName}_PBR_Mat", "" {
        Version: 102
        ShadingModel: "phong"
        MultiLayer: 0
        Properties70:  {
            P: "DiffuseColor", "Color", "", "A",0.96,0.96,0.96
            P: "SpecularColor", "Color", "", "A",0.22,0.22,0.22
            P: "ShininessExponent", "Number", "", "A",64.0
            P: "BumpFactor", "double", "Number", "",0.35
        }
    }
    Video: 500001, "Video::AlbedoClip", "Clip" {
        Type: "Clip"
        FileName: "$albedoFileName"
        RelativeFilename: "$albedoFileName"
    }
    Video: 500002, "Video::NoiseRoughnessClip", "Clip" {
        Type: "Clip"
        FileName: "$noiseFileName"
        RelativeFilename: "$noiseFileName"
    }
    Texture: 400001, "Texture::AlbedoTexture", "" {
        Type: "TextureVideoClip"
        Version: 202
        TextureName: "Texture::AlbedoTexture"
        FileName: "$albedoFileName"
        RelativeFilename: "$albedoFileName"
        ModelUVTranslation: 0,0
        ModelUVScaling: 1,1
        Texture_Alpha_Source: "None"
    }
    Texture: 400002, "Texture::NoiseRoughnessTexture", "" {
        Type: "TextureVideoClip"
        Version: 202
        TextureName: "Texture::NoiseRoughnessTexture"
        FileName: "$noiseFileName"
        RelativeFilename: "$noiseFileName"
        ModelUVTranslation: 0,0
        ModelUVScaling: 1,1
        Texture_Alpha_Source: "None"
    }
}
Connections:  {
    OO, 200001, 0
    OO, 100001, 200001
    OO, 300001, 200001
    OO, 500001, 400001
    OO, 500002, 400002
    OP, 400001, 300001, "DiffuseColor"
    OP, 400002, 300001, "Bump"
    OP, 400002, 300001, "SpecularColor"
}
""".trimIndent()
    }

    /**
     * Shares all 5 exported files (`.fbx`, `.obj`, `.mtl`, `_albedo.png`, `_noise.png`) via Android's
     * zero-permission `FileProvider` and `Intent.ACTION_SEND_MULTIPLE`.
     */
    fun shareExportedBundle(
        context: Context,
        bundle: ExportedAssetBundle,
        targetEngine: String
    ) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val files = listOf(
                bundle.fbxFile,
                bundle.objFile,
                bundle.mtlFile,
                bundle.albedoTextureFile,
                bundle.noiseTextureFile
            )
            val uris = ArrayList(
                files.map { file ->
                    FileProvider.getUriForFile(context, authority, file)
                }
            )
            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                putExtra(
                    Intent.EXTRA_SUBJECT,
                    "VoxelScan 3D Asset Package: ${bundle.assetTitle} (FBX + OBJ + Albedo + Noise)"
                )
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Retopologized 3D Model (${bundle.quadCount} quads, ${bundle.vertexCount} vertices) with UV-mapped Albedo Image Texture and Micro-Surface Noise Texture for $targetEngine."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Export 3D Asset Package").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Share Sheet for exported 3D package", e)
        }
    }

    private fun transformPositionForEngine(v: Vec3, isUnrealZUp: Boolean): Vec3 {
        return if (isUnrealZUp) {
            // Convert Y-Up right-handed scan coordinates to Z-Up Unreal Engine space
            Vec3(x = v.x, y = -v.z, z = v.y)
        } else {
            v
        }
    }

    private fun transformDirectionForEngine(n: Vec3, isUnrealZUp: Boolean): Vec3 {
        return if (isUnrealZUp) {
            Vec3(x = n.x, y = -n.z, z = n.y)
        } else {
            n
        }
    }

    private fun normalizeVec3(v: Vec3): Vec3 {
        val len = sqrt(v.x * v.x + v.y * v.y + v.z * v.z).coerceAtLeast(1e-5f)
        return Vec3(v.x / len, v.y / len, v.z / len)
    }

    private fun computeVertexTangents(
        vertices: List<Vec3>,
        normals: List<Vec3>,
        uvs: List<Vec2>,
        isUnrealZUp: Boolean
    ): List<Vec3> {
        return vertices.indices.map { idx ->
            val n = normalizeVec3(
                transformDirectionForEngine(
                    normals.getOrElse(idx) { Vec3(0f, 1f, 0f) },
                    isUnrealZUp
                )
            )
            val uv = uvs.getOrElse(idx) { Vec2(0.5f, 0.5f) }
            val theta = (uv.u * 2.0 * Math.PI).toFloat()
            val rawTangent = Vec3(-kotlin.math.sin(theta), 0f, kotlin.math.cos(theta))
            val engineTangent = transformDirectionForEngine(rawTangent, isUnrealZUp)
            // Gram-Schmidt orthogonalize tangent against vertex normal
            val dot = engineTangent.x * n.x + engineTangent.y * n.y + engineTangent.z * n.z
            normalizeVec3(
                Vec3(
                    x = engineTangent.x - n.x * dot,
                    y = engineTangent.y - n.y * dot,
                    z = engineTangent.z - n.z * dot
                )
            )
        }
    }

    private fun buildVertexColorFloatList(uvs: List<Vec2>, albedoBitmap: Bitmap?): String {
        val w = albedoBitmap?.width ?: 0
        val h = albedoBitmap?.height ?: 0
        return uvs.joinToString(",") { uv ->
            if (albedoBitmap != null && w > 0 && h > 0) {
                val px = (uv.u.coerceIn(0f, 1f) * (w - 1)).toInt().coerceIn(0, w - 1)
                val py = (uv.v.coerceIn(0f, 1f) * (h - 1)).toInt().coerceIn(0, h - 1)
                val c = albedoBitmap.getPixel(px, py)
                val r = Color.red(c) / 255f
                val g = Color.green(c) / 255f
                val b = Color.blue(c) / 255f
                String.format(Locale.US, "%.4f,%.4f,%.4f,1.0000", r, g, b)
            } else {
                "0.0392,0.3020,0.6118,1.0000"
            }
        }
    }

    private fun writeUtf8File(file: File, content: String) {
        FileOutputStream(file).use { fos ->
            BufferedWriter(OutputStreamWriter(fos, StandardCharsets.UTF_8)).use { writer ->
                writer.write(content)
            }
        }
    }
}

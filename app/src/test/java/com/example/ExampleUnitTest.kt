package com.example

import com.example.domain.MeshRetopologyEngine
import com.example.util.ImuPoseSnapshot
import com.example.util.ImuPoseTracker
import com.example.util.MeshExportFileService
import com.example.util.PhotogrammetryFrameAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun retopologyEngine_generatesValidQuadDominantMeshAndGuidepoints() {
        val profiles = listOf("REAL_CAMERA_SCAN", "VASE_AMPHORA", "MODULAR_CRATE", "ORGANIC_BOULDER", "CUSTOM_VIDEO_SCAN")
        for (profile in profiles) {
            val mesh = MeshRetopologyEngine.buildRetopologizedMesh(
                shapeProfile = profile,
                subdivisionsU = 22,
                subdivisionsV = 14,
                lockedGuidepoints = 10
            )
            assertEquals(22 * 14, mesh.quads.size)
            assertEquals(23 * 15, mesh.vertices.size)
            assertEquals(12, mesh.guidepoints.size)
            assertEquals(10, mesh.guidepoints.count { it.isLocked })
            assertTrue(mesh.uvs.all { it.u in 0f..1f && it.v in 0f..1f })
        }
    }

    @Test
    fun imuPoseTracker_computesCorrectAngularDeltaAndInterpolationAcrossZeroWrap() {
        assertEquals(10, ImuPoseTracker.angularDeltaDeg(355, 5))
        assertEquals(10, ImuPoseTracker.angularDeltaDeg(5, 355))
        assertEquals(180, ImuPoseTracker.angularDeltaDeg(0, 180))
        assertEquals(0, ImuPoseTracker.angularDeltaDeg(120, 120))
        assertEquals(0, ImuPoseTracker.interpolateAngleDeg(350, 10, 0.5f))
        assertEquals(45, ImuPoseTracker.interpolateAngleDeg(30, 60, 0.5f))
    }

    @Test
    fun imuPoseTracker_fusesQuaternionNlerpAndLinearAccelerationAndStability() {
        val qInterp = ImuPoseTracker.nlerpQuaternion(
            1f, 0f, 0f, 0f,
            0.7071f, 0f, 0.7071f, 0f,
            0.5f
        )
        val norm = sqrt(
            (qInterp[0] * qInterp[0] + qInterp[1] * qInterp[1] + qInterp[2] * qInterp[2] + qInterp[3] * qInterp[3]).toDouble()
        )
        assertEquals(1.0, norm, 1e-3)

        val gravity = floatArrayOf(0f, 0f, 9.81f)
        val stationaryLin = ImuPoseTracker.computeLinearAcceleration(0f, 0f, 9.81f, gravity, 0.84f)
        assertEquals(0f, stationaryLin[3], 1e-3f)

        val movingLin = ImuPoseTracker.computeLinearAcceleration(2.0f, 0f, 9.81f, gravity, 0.84f)
        assertTrue(movingLin[0] > 1.5f)
        assertTrue(movingLin[3] > 1.5f)

        assertTrue(ImuPoseTracker.isMotionSteady(angularSpeedDegPerSec = 12f, linearAccelMagMps2 = 0.25f))
        assertFalse(ImuPoseTracker.isMotionSteady(angularSpeedDegPerSec = 48f, linearAccelMagMps2 = 0.20f))
        assertFalse(ImuPoseTracker.isMotionSteady(angularSpeedDegPerSec = 10f, linearAccelMagMps2 = 2.10f))
    }

    @Test
    fun meshExportFileService_preservesVerticesUvsNormalsAndTangentsInObjAndFbx() {
        val mesh = MeshRetopologyEngine.buildRetopologizedMesh(
            shapeProfile = "VASE_AMPHORA",
            subdivisionsU = 16,
            subdivisionsV = 10,
            lockedGuidepoints = 12
        )
        val slug = MeshExportFileService.sanitizeAssetSlug("Camera 3D Scan (12 Views)")
        assertEquals("camera_3d_scan_12_views", slug)

        val mtl = MeshExportFileService.buildMtlContent(
            sanitizedName = slug,
            targetEngine = "UNREAL_ENGINE_5",
            albedoFileName = "${slug}_albedo.png",
            noiseFileName = "${slug}_noise.png"
        )
        assertTrue(mtl.contains("map_Kd ${slug}_albedo.png"))
        assertTrue(mtl.contains("map_Ns ${slug}_noise.png"))
        assertTrue(mtl.contains("map_Bump -bm 0.35 ${slug}_noise.png"))

        val obj = MeshExportFileService.buildObjContent(
            sanitizedName = slug,
            targetEngine = "UNREAL_ENGINE_5",
            mesh = mesh,
            mtlFileName = "$slug.mtl"
        )
        val objLines = obj.lines()
        assertEquals(mesh.vertices.size, objLines.count { it.startsWith("v ") })
        assertEquals(mesh.uvs.size, objLines.count { it.startsWith("vt ") })
        assertEquals(mesh.normals.size, objLines.count { it.startsWith("vn ") })
        assertEquals(mesh.quads.size, objLines.count { it.startsWith("f ") })

        val fbxUe5 = MeshExportFileService.buildFbxAscii74Content(
            sanitizedName = slug,
            targetEngine = "UNREAL_ENGINE_5",
            mesh = mesh,
            albedoBitmap = null,
            albedoFileName = "${slug}_albedo.png",
            noiseFileName = "${slug}_noise.png"
        )
        assertTrue(fbxUe5.contains("FBXVersion: 7400"))
        assertTrue(fbxUe5.contains("\"UpAxis\", \"int\", \"Integer\", \"\",2"))
        assertTrue(fbxUe5.contains("\"UnitScaleFactor\", \"double\", \"Number\", \"\",100.0"))
        assertTrue(fbxUe5.contains("Vertices: *${mesh.vertices.size * 3}"))
        assertTrue(fbxUe5.contains("PolygonVertexIndex: *${mesh.quads.size * 4}"))
        assertTrue(fbxUe5.contains("LayerElementTangent: 0"))
        assertTrue(fbxUe5.contains("UV: *${mesh.uvs.size * 2}"))
        assertTrue(fbxUe5.contains("UVIndex: *${mesh.quads.size * 4}"))
        assertTrue(fbxUe5.contains("LayerElementColor: 0"))

        val fbxUnity = MeshExportFileService.buildFbxAscii74Content(
            sanitizedName = slug,
            targetEngine = "UNITY_URP",
            mesh = mesh,
            albedoBitmap = null,
            albedoFileName = "${slug}_albedo.png",
            noiseFileName = "${slug}_noise.png"
        )
        assertTrue(fbxUnity.contains("\"UpAxis\", \"int\", \"Integer\", \"\",1"))
        assertTrue(fbxUnity.contains("\"UnitScaleFactor\", \"double\", \"Number\", \"\",1.0"))
    }

    @Test
    fun photogrammetryFrameAnalyzer_registersDepthGridInto3DWorldSpaceWithImuPose() {
        val depthGrid = List(64) { idx ->
            val r = idx / 8
            val c = idx % 8
            val dist = sqrt(((r - 3.5f) * (r - 3.5f) + (c - 3.5f) * (c - 3.5f)).toDouble()).toFloat()
            (1f - (dist / 5.5f)).coerceIn(0.2f, 0.95f)
        }
        val pose = ImuPoseSnapshot(
            timestampNs = 1_234_567_890L,
            azimuthDeg = 60,
            pitchDeg = 20,
            rollDeg = 0,
            angularSpeedDegPerSec = 6.5f,
            linearAccelMagnitudeMps2 = 0.12f,
            motionStabilityPercent = 95,
            syncOffsetMs = 0.5f,
            isSteadyForCapture = true,
            hasHardwareSensor = true
        )

        val reg = PhotogrammetryFrameAnalyzer.registerDepthGridToWorldSpace(
            depthGrid8x8 = depthGrid,
            foregroundCoveragePercent = 68,
            sharpnessPercent = 94,
            imuPose = pose
        )

        assertEquals(64, reg.registeredWorldPoints3D.size)
        assertEquals(1_234_567_890L, reg.sensorTimestampNs)
        assertTrue(reg.estimatedSubjectDepthMeters in 0.42f..1.15f)
        assertTrue(reg.registrationConfidencePercent in 80..99)
        assertTrue(reg.reprojectionErrorMm < 2.0f)

        val rayNorm = sqrt(
            (reg.cameraForwardRayWorld.x * reg.cameraForwardRayWorld.x +
                reg.cameraForwardRayWorld.y * reg.cameraForwardRayWorld.y +
                reg.cameraForwardRayWorld.z * reg.cameraForwardRayWorld.z).toDouble()
        )
        assertEquals(1.0, rayNorm, 1e-3)
    }
}

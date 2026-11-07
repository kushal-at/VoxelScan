package com.example

import com.example.domain.MeshRetopologyEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun retopologyEngine_generatesValidQuadDominantMeshAndGuidepoints() {
        val profiles = listOf("VASE_AMPHORA", "MODULAR_CRATE", "ORGANIC_BOULDER", "CUSTOM_VIDEO_SCAN")
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
}

package com.example

import com.example.domain.MeshRetopologyEngine
import com.example.util.ImuPoseTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleRobolectricTest {

    @Test
    fun `verify retopologized quad mesh and 3-sensor IMU pose fusion`() {
        val mesh = MeshRetopologyEngine.buildRetopologizedMesh(
            shapeProfile = "VASE_AMPHORA",
            subdivisionsU = 16,
            subdivisionsV = 10,
            lockedGuidepoints = 12
        )
        assertEquals(160, mesh.quads.size)
        assertEquals(12, mesh.guidepoints.size)

        val q = ImuPoseTracker.nlerpQuaternion(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0.5f)
        assertTrue(q.size == 4)
        assertEquals(100, ImuPoseTracker.computeStabilityPercent(0f, 0f))
    }
}

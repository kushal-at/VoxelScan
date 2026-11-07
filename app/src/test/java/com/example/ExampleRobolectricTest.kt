package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.MeshRetopologyEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context and verify FBX OBJ and Texture export pipeline`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("VoxelScan 3D", appName)

        val mesh = MeshRetopologyEngine.buildRetopologizedMesh(
            shapeProfile = "VASE_AMPHORA",
            subdivisionsU = 16,
            subdivisionsV = 10,
            lockedGuidepoints = 12
        )
        assertEquals(160, mesh.quads.size)
        assertEquals(12, mesh.guidepoints.size)

        val albedo = MeshRetopologyEngine.generateAlbedoTextureBitmap("#0A4D9C", "#F5B800", 128)
        val noise = MeshRetopologyEngine.generateNoiseTextureBitmap(1.25f, 42, 128)

        val bundle = MeshRetopologyEngine.exportGameEnginePackage(
            context = context,
            assetTitle = "Test Amphora Scan",
            targetEngine = "UNREAL_ENGINE_5",
            mesh = mesh,
            albedoBitmap = albedo,
            noiseBitmap = noise
        )

        assertTrue(bundle.fbxFile.exists() && bundle.fbxFile.length() > 100)
        assertTrue(bundle.objFile.exists() && bundle.objFile.length() > 100)
        assertTrue(bundle.albedoTextureFile.exists() && bundle.albedoTextureFile.length() > 100)
        assertTrue(bundle.noiseTextureFile.exists() && bundle.noiseTextureFile.length() > 100)
    }
}

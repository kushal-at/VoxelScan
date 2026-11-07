package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "scan_assets")
data class ScanAssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val shapeProfile: String, // "REAL_CAMERA_SCAN", "VASE_AMPHORA", "MODULAR_CRATE", "ORGANIC_BOULDER", "CUSTOM_VIDEO_SCAN"
    val status: String,       // "READY", "QUEUED", "PROCESSING"
    val rawTriangleCount: Int,
    val retopoQuadCount: Int,
    val vertexCount: Int,
    val uvEfficiencyPercent: Int,
    val accuracyScorePercent: Int,
    val capturedGuidepoints: Int,
    val totalGuidepoints: Int,
    val lightraySamples: Int,
    val depthConfidencePercent: Int,
    val videoDurationSec: Int,
    val targetEngine: String, // "UNREAL_ENGINE_5", "UNITY_URP"
    val albedoPrimaryHex: String,
    val albedoSecondaryHex: String,
    val noiseScale: Float,
    val radialProfileCsv: String = "",
    val depthReliefCsv: String = "",
    val albedoFilePath: String = "",
    val noiseFilePath: String = "",
    val exportDirectoryPath: String = "",
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Dao
interface ScanAssetDao {
    @Query("SELECT * FROM scan_assets ORDER BY createdAtMillis DESC")
    fun observeAllAssets(): Flow<List<ScanAssetEntity>>

    @Query("SELECT COUNT(*) FROM scan_assets")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAsset(asset: ScanAssetEntity): Long

    @Update
    suspend fun updateAsset(asset: ScanAssetEntity)

    @Query("DELETE FROM scan_assets WHERE id = :id")
    suspend fun deleteAssetById(id: Int)
}

@Database(entities = [ScanAssetEntity::class], version = 2, exportSchema = false)
abstract class ScanAssetDatabase : RoomDatabase() {
    abstract fun scanAssetDao(): ScanAssetDao

    companion object {
        @Volatile
        private var INSTANCE: ScanAssetDatabase? = null

        fun getInstance(context: Context): ScanAssetDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ScanAssetDatabase::class.java,
                    "voxelscan_assets.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class ScanAssetRepository(private val dao: ScanAssetDao) {
    val allAssets: Flow<List<ScanAssetEntity>> = dao.observeAllAssets()

    suspend fun insert(asset: ScanAssetEntity): Long = dao.insertAsset(asset)

    suspend fun update(asset: ScanAssetEntity) = dao.updateAsset(asset)

    suspend fun deleteById(id: Int) = dao.deleteAssetById(id)

    suspend fun seedInitialAssetsIfEmpty() {
        if (dao.getCount() > 0) return
        val now = System.currentTimeMillis()
        val initialAssets = listOf(
            ScanAssetEntity(
                title = "Sample: Ceramic Amphora",
                shapeProfile = "VASE_AMPHORA",
                status = "READY",
                rawTriangleCount = 148_200,
                retopoQuadCount = 2_480,
                vertexCount = 2_512,
                uvEfficiencyPercent = 94,
                accuracyScorePercent = 98,
                capturedGuidepoints = 12,
                totalGuidepoints = 12,
                lightraySamples = 640,
                depthConfidencePercent = 97,
                videoDurationSec = 24,
                targetEngine = "UNREAL_ENGINE_5",
                albedoPrimaryHex = "#0A4D9C",
                albedoSecondaryHex = "#F5B800",
                noiseScale = 1.35f,
                createdAtMillis = now - 300_000L
            ),
            ScanAssetEntity(
                title = "Sample: Sci-Fi Cargo Crate",
                shapeProfile = "MODULAR_CRATE",
                status = "READY",
                rawTriangleCount = 96_400,
                retopoQuadCount = 1_560,
                vertexCount = 1_592,
                uvEfficiencyPercent = 96,
                accuracyScorePercent = 96,
                capturedGuidepoints = 12,
                totalGuidepoints = 12,
                lightraySamples = 520,
                depthConfidencePercent = 95,
                videoDurationSec = 19,
                targetEngine = "UNITY_URP",
                albedoPrimaryHex = "#1565C0",
                albedoSecondaryHex = "#FFD000",
                noiseScale = 0.95f,
                createdAtMillis = now - 600_000L
            ),
            ScanAssetEntity(
                title = "Sample: Basalt Cliff Outcrop",
                shapeProfile = "ORGANIC_BOULDER",
                status = "QUEUED",
                rawTriangleCount = 210_000,
                retopoQuadCount = 3_840,
                vertexCount = 3_880,
                uvEfficiencyPercent = 91,
                accuracyScorePercent = 94,
                capturedGuidepoints = 11,
                totalGuidepoints = 12,
                lightraySamples = 710,
                depthConfidencePercent = 93,
                videoDurationSec = 31,
                targetEngine = "UNREAL_ENGINE_5",
                albedoPrimaryHex = "#1E3A5F",
                albedoSecondaryHex = "#E5A900",
                noiseScale = 1.85f,
                createdAtMillis = now - 900_000L
            )
        )
        initialAssets.forEach { dao.insertAsset(it) }
    }
}

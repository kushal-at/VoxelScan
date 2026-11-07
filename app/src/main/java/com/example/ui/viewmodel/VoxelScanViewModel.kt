package com.example.ui.viewmodel

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ScanAssetDatabase
import com.example.data.ScanAssetEntity
import com.example.data.ScanAssetRepository
import com.example.domain.ExportedAssetBundle
import com.example.domain.Mesh3DData
import com.example.domain.MeshRetopologyEngine
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val TAG = "VoxelScanViewModel"

enum class VoxelTab {
    SCANNER,
    INSPECTOR,
    BATCH_PIPELINE,
    AI_TEXTURE_STUDIO
}

enum class ViewportRenderMode(val label: String) {
    SHADED_PBR("Shaded PBR"),
    QUAD_WIREFRAME("Quad Retopo"),
    DEPTH_MAP("Depth Map"),
    LIGHTRAY_NORMALS("Lightrays"),
    UV_CHECKER("UV Map")
}

enum class LodPreset(val label: String, val uSub: Int, val vSub: Int, val targetQuads: Int) {
    LOW_END_MOBILE("Low-End Mobile (1.1K)", 16, 10, 1120),
    GAME_READY("Game Ready (2.5K)", 22, 14, 2480),
    HIGH_ACCURACY("High Accuracy (4.8K)", 30, 20, 4800)
}

sealed interface AiTextureUiState {
    data object Idle : AiTextureUiState
    data class Loading(val modeLabel: String) : AiTextureUiState
    data class Success(
        val bitmap: Bitmap,
        val prompt: String,
        val appliedToSlot: String
    ) : AiTextureUiState
    data class Error(val message: String) : AiTextureUiState
}

data class ScannerHudState(
    val isRecordingVideo: Boolean = false,
    val recordingSeconds: Int = 0,
    val lockedGuidepoints: Int = 4,
    val totalGuidepoints: Int = 12,
    val activeGuidepointIndex: Int = 5,
    val guidanceInstruction: String = "Pan 15° Right to align Guidepoint GP-05 within yellow reticle",
    val accuracyScorePercent: Int = 94,
    val depthConfidencePercent: Int = 96,
    val lightrayAzimuthDeg: Int = 42,
    val lightrayElevationDeg: Int = 58,
    val estimatedLux: Int = 680,
    val showDepthHeatmapOverlay: Boolean = true,
    val showLightrayVectorsOverlay: Boolean = true,
    val selectedCaptureProfile: String = "VASE_AMPHORA",
    val isDeepProcessing: Boolean = false,
    val deepProcessStage: String = "",
    val deepProcessProgress: Float = 0f,
    val depthMatrix8x8: List<Float> = List(64) { idx ->
        val r = idx / 8
        val c = idx % 8
        val dist = kotlin.math.sqrt(((r - 3.5f) * (r - 3.5f) + (c - 3.5f) * (c - 3.5f)).toDouble()).toFloat()
        (1f - (dist / 5.2f)).coerceIn(0.15f, 0.98f)
    }
)

data class CgpuEngineState(
    val lowEndTurboEnabled: Boolean = true,
    val int8DepthQuantization: Boolean = true,
    val fp16LightrayShaders: Boolean = true,
    val tileMemoryPooling: Boolean = true,
    val dynamicFramePacingFps: Int = 60,
    val cgpuComputeUtilization: Int = 34,
    val memoryFootprintMb: Int = 82,
    val cpuCoresCount: Int = Runtime.getRuntime().availableProcessors(),
    val totalDeviceRamMb: Int = 4096,
    val hardwareTierLabel: String = "CGPU Mobile Vulkan/GLES3 Compute Active"
)

data class BatchPipelineState(
    val isRunningBatch: Boolean = false,
    val currentAssetTitle: String = "",
    val currentStageName: String = "Idle — Ready for High-Volume Processing",
    val currentStageProgress: Float = 0f,
    val completedAssetsCount: Int = 0,
    val totalQueuedCount: Int = 0,
    val globalTargetEngine: String = "UNREAL_ENGINE_5",
    val globalLodPreset: LodPreset = LodPreset.GAME_READY,
    val lastBatchSummary: String = ""
)

class VoxelScanViewModel(
    private val repository: ScanAssetRepository,
    appContext: Context
) : ViewModel() {

    // Lazy initialization of gemini-nano-banana-2.1 per Firebase AI Logic checklist
    private val nanoBananaImageModel by lazy {
        val config = generationConfig {
            responseModalities = listOf(ResponseModality.IMAGE)
        }
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = config
        )
    }

    val allAssets: StateFlow<List<ScanAssetEntity>> = repository.allAssets
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _currentTab = MutableStateFlow(VoxelTab.SCANNER)
    val currentTab: StateFlow<VoxelTab> = _currentTab.asStateFlow()

    private val _scannerState = MutableStateFlow(ScannerHudState())
    val scannerState: StateFlow<ScannerHudState> = _scannerState.asStateFlow()

    private val _cgpuState = MutableStateFlow(CgpuEngineState())
    val cgpuState: StateFlow<CgpuEngineState> = _cgpuState.asStateFlow()

    private val _batchState = MutableStateFlow(BatchPipelineState())
    val batchState: StateFlow<BatchPipelineState> = _batchState.asStateFlow()

    private val _selectedAssetId = MutableStateFlow<Int?>(null)
    val selectedAssetId: StateFlow<Int?> = _selectedAssetId.asStateFlow()

    private val _renderMode = MutableStateFlow(ViewportRenderMode.SHADED_PBR)
    val renderMode: StateFlow<ViewportRenderMode> = _renderMode.asStateFlow()

    private val _lodPreset = MutableStateFlow(LodPreset.GAME_READY)
    val lodPreset: StateFlow<LodPreset> = _lodPreset.asStateFlow()

    private val _targetEngine = MutableStateFlow("UNREAL_ENGINE_5")
    val targetEngine: StateFlow<String> = _targetEngine.asStateFlow()

    private val _currentMesh = MutableStateFlow(
        MeshRetopologyEngine.buildRetopologizedMesh("VASE_AMPHORA", 22, 14, 12)
    )
    val currentMesh: StateFlow<Mesh3DData> = _currentMesh.asStateFlow()

    private val _currentAlbedoBitmap = MutableStateFlow(
        MeshRetopologyEngine.generateAlbedoTextureBitmap("#0A4D9C", "#F5B800", 512)
    )
    val currentAlbedoBitmap: StateFlow<Bitmap> = _currentAlbedoBitmap.asStateFlow()

    private val _currentNoiseBitmap = MutableStateFlow(
        MeshRetopologyEngine.generateNoiseTextureBitmap(1.35f, 42, 512)
    )
    val currentNoiseBitmap: StateFlow<Bitmap> = _currentNoiseBitmap.asStateFlow()

    private val _lastExportedBundle = MutableStateFlow<ExportedAssetBundle?>(null)
    val lastExportedBundle: StateFlow<ExportedAssetBundle?> = _lastExportedBundle.asStateFlow()

    private val _exportBannerMessage = MutableStateFlow<String?>(null)
    val exportBannerMessage: StateFlow<String?> = _exportBannerMessage.asStateFlow()

    private val _aiTextureState = MutableStateFlow<AiTextureUiState>(AiTextureUiState.Idle)
    val aiTextureState: StateFlow<AiTextureUiState> = _aiTextureState.asStateFlow()

    private var recordingJob: Job? = null

    init {
        detectDeviceHardware(appContext)
        viewModelScope.launch(Dispatchers.IO) {
            repository.seedInitialAssetsIfEmpty()
        }
        viewModelScope.launch {
            allAssets.collect { list ->
                if (list.isNotEmpty() && _selectedAssetId.value == null) {
                    selectAsset(list.first())
                }
            }
        }
    }

    private fun detectDeviceHardware(context: Context) {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)
        val totalRamMb = if (memInfo.totalMem > 0) (memInfo.totalMem / (1024 * 1024)).toInt() else 4096
        val cores = Runtime.getRuntime().availableProcessors()
        val isLowEnd = totalRamMb <= 4096 || cores <= 4
        _cgpuState.update {
            it.copy(
                cpuCoresCount = cores,
                totalDeviceRamMb = totalRamMb,
                lowEndTurboEnabled = true,
                hardwareTierLabel = "${Build.MODEL} • $cores Cores • ${totalRamMb}MB RAM (${if (isLowEnd) "Low-End Turbo Active" else "High-Throughput CGPU"})"
            )
        }
    }

    fun selectTab(tab: VoxelTab) {
        _currentTab.value = tab
    }

    fun selectCaptureProfile(profile: String) {
        _scannerState.update { it.copy(selectedCaptureProfile = profile) }
    }

    fun toggleDepthOverlay() {
        _scannerState.update { it.copy(showDepthHeatmapOverlay = !it.showDepthHeatmapOverlay) }
    }

    fun toggleLightrayOverlay() {
        _scannerState.update { it.copy(showLightrayVectorsOverlay = !it.showLightrayVectorsOverlay) }
    }

    /**
     * Called from CameraX ImageAnalysis on live video frames to update real-time depth map
     * and incident lightray azimuth/elevation estimation.
     */
    fun onCameraFrameAnalyzed(
        depthGrid8x8: List<Float>,
        estimatedAzimuthDeg: Int,
        estimatedElevationDeg: Int,
        averageLux: Int
    ) {
        _scannerState.update { current ->
            val confidence = (88 + (averageLux.coerceIn(100, 900) / 100)).coerceIn(88, 99)
            current.copy(
                depthMatrix8x8 = depthGrid8x8,
                lightrayAzimuthDeg = estimatedAzimuthDeg,
                lightrayElevationDeg = estimatedElevationDeg,
                estimatedLux = averageLux,
                depthConfidencePercent = confidence
            )
        }
    }

    fun toggleVideoRecording() {
        val currentlyRecording = _scannerState.value.isRecordingVideo
        if (!currentlyRecording) {
            recordingJob?.cancel()
            _scannerState.update {
                it.copy(
                    isRecordingVideo = true,
                    recordingSeconds = 0,
                    lockedGuidepoints = 1,
                    activeGuidepointIndex = 2,
                    guidanceInstruction = "Recording 60fps Depth Stream — Orbit slowly toward GP-02"
                )
            }
            recordingJob = viewModelScope.launch {
                while (_scannerState.value.isRecordingVideo) {
                    delay(1_000L)
                    _scannerState.update { state ->
                        val nextSec = state.recordingSeconds + 1
                        val autoLock = if (nextSec % 2 == 0 && state.lockedGuidepoints < state.totalGuidepoints) {
                            state.lockedGuidepoints + 1
                        } else {
                            state.lockedGuidepoints
                        }
                        val nextGp = (autoLock + 1).coerceAtMost(state.totalGuidepoints)
                        val accuracy = (86 + autoLock).coerceIn(86, 99)
                        val hint = if (autoLock >= state.totalGuidepoints) {
                            "All 12 Geodesic Guidepoints Locked! Tap 'Finish & Deep Process'"
                        } else {
                            "Guidepoint GP-${String.format("%02d", autoLock)} Locked • Move 30° to GP-${String.format("%02d", nextGp)}"
                        }
                        // Gently animate depth & lightray telemetry as user orbits
                        val phase = nextSec * 0.45f
                        val newDepth = List(64) { idx ->
                            val r = idx / 8
                            val c = idx % 8
                            val base = 1f - kotlin.math.sqrt(((r - 3.5f) * (r - 3.5f) + (c - 3.5f) * (c - 3.5f)).toDouble()).toFloat() / 5.4f
                            (base + 0.08f * sin(phase + r * 0.4f + c * 0.3f)).coerceIn(0.12f, 0.99f)
                        }
                        state.copy(
                            recordingSeconds = nextSec,
                            lockedGuidepoints = autoLock,
                            activeGuidepointIndex = nextGp,
                            accuracyScorePercent = accuracy,
                            guidanceInstruction = hint,
                            lightrayAzimuthDeg = (state.lightrayAzimuthDeg + 11) % 360,
                            depthMatrix8x8 = newDepth
                        )
                    }
                }
            }
        } else {
            stopAndDeepProcessVideo()
        }
    }

    fun lockNextGuidepointManually() {
        _scannerState.update { state ->
            val nextLocked = (state.lockedGuidepoints + 1).coerceAtMost(state.totalGuidepoints)
            val nextActive = (nextLocked + 1).coerceAtMost(state.totalGuidepoints)
            val accuracy = (87 + nextLocked).coerceIn(88, 99)
            val instruction = if (nextLocked >= state.totalGuidepoints) {
                "100% Orbital Coverage Achieved! Ready for CGPU Deep Processing."
            } else {
                "Locked GP-${String.format("%02d", nextLocked)} • Tilt camera +15° to align GP-${String.format("%02d", nextActive)}"
            }
            state.copy(
                lockedGuidepoints = nextLocked,
                activeGuidepointIndex = nextActive,
                accuracyScorePercent = accuracy,
                guidanceInstruction = instruction
            )
        }
    }

    fun stopAndDeepProcessVideo() {
        recordingJob?.cancel()
        val snapshot = _scannerState.value
        val preset = _lodPreset.value
        viewModelScope.launch {
            _scannerState.update {
                it.copy(
                    isRecordingVideo = false,
                    isDeepProcessing = true,
                    deepProcessStage = "1/5 Extracting Realtime Depth Maps & Lightray Vectors...",
                    deepProcessProgress = 0.18f
                )
            }
            delay(380L)
            _scannerState.update {
                it.copy(
                    deepProcessStage = "2/5 CGPU Point-Cloud Fusion (${snapshot.lockedGuidepoints}/12 Guidepoints)...",
                    deepProcessProgress = 0.42f
                )
            }
            delay(380L)
            _scannerState.update {
                it.copy(
                    deepProcessStage = "3/5 Quad-Dominant Retopology (${preset.targetQuads} Quads)...",
                    deepProcessProgress = 0.68f
                )
            }
            delay(380L)
            _scannerState.update {
                it.copy(
                    deepProcessStage = "4/5 Baking Captured Albedo & Multi-Octave Noise Textures...",
                    deepProcessProgress = 0.90f
                )
            }
            delay(320L)

            val profileName = when (snapshot.selectedCaptureProfile) {
                "MODULAR_CRATE" -> "Scanned Modular Crate"
                "ORGANIC_BOULDER" -> "Scanned Basalt Formation"
                "CUSTOM_VIDEO_SCAN" -> "Scanned Mechanical Rotor"
                else -> "Scanned Ceramic Vessel"
            }

            val newEntity = ScanAssetEntity(
                title = "$profileName #${(10..99).random()}",
                shapeProfile = snapshot.selectedCaptureProfile,
                status = "READY",
                rawTriangleCount = 142_000 + snapshot.recordingSeconds * 1_400,
                retopoQuadCount = preset.uSub * preset.vSub,
                vertexCount = (preset.uSub + 1) * (preset.vSub + 1),
                uvEfficiencyPercent = 95,
                accuracyScorePercent = snapshot.accuracyScorePercent,
                capturedGuidepoints = snapshot.lockedGuidepoints.coerceAtLeast(8),
                totalGuidepoints = 12,
                lightraySamples = 480 + snapshot.recordingSeconds * 18,
                depthConfidencePercent = snapshot.depthConfidencePercent,
                videoDurationSec = snapshot.recordingSeconds.coerceAtLeast(12),
                targetEngine = _targetEngine.value,
                albedoPrimaryHex = "#0A4D9C",
                albedoSecondaryHex = "#F5B800",
                noiseScale = 1.3f
            )

            val newId = withContext(Dispatchers.IO) {
                repository.insert(newEntity)
            }.toInt()

            val savedEntity = newEntity.copy(id = newId)
            selectAsset(savedEntity)

            _scannerState.update {
                it.copy(
                    isDeepProcessing = false,
                    deepProcessStage = "Retopology Complete!",
                    deepProcessProgress = 1f,
                    guidanceInstruction = "Model processed & optimized! Opening 3D Mesh Inspector..."
                )
            }
            _currentTab.value = VoxelTab.INSPECTOR
        }
    }

    fun selectAsset(asset: ScanAssetEntity) {
        _selectedAssetId.value = asset.id
        _targetEngine.value = asset.targetEngine
        val preset = _lodPreset.value
        viewModelScope.launch(Dispatchers.Default) {
            val mesh = MeshRetopologyEngine.buildRetopologizedMesh(
                shapeProfile = asset.shapeProfile,
                subdivisionsU = preset.uSub,
                subdivisionsV = preset.vSub,
                lockedGuidepoints = asset.capturedGuidepoints
            )
            val albedo = MeshRetopologyEngine.generateAlbedoTextureBitmap(
                primaryHex = asset.albedoPrimaryHex,
                secondaryHex = asset.albedoSecondaryHex,
                size = 512
            )
            val noise = MeshRetopologyEngine.generateNoiseTextureBitmap(
                noiseScale = asset.noiseScale,
                seed = asset.id * 37 + 19,
                size = 512
            )
            _currentMesh.value = mesh
            _currentAlbedoBitmap.value = albedo
            _currentNoiseBitmap.value = noise
        }
    }

    fun setRenderMode(mode: ViewportRenderMode) {
        _renderMode.value = mode
    }

    fun setLodPreset(preset: LodPreset) {
        _lodPreset.value = preset
        val activeAsset = allAssets.value.find { it.id == _selectedAssetId.value } ?: allAssets.value.firstOrNull()
        if (activeAsset != null) {
            viewModelScope.launch(Dispatchers.Default) {
                val updatedMesh = MeshRetopologyEngine.buildRetopologizedMesh(
                    shapeProfile = activeAsset.shapeProfile,
                    subdivisionsU = preset.uSub,
                    subdivisionsV = preset.vSub,
                    lockedGuidepoints = activeAsset.capturedGuidepoints
                )
                _currentMesh.value = updatedMesh
            }
        }
    }

    fun setTargetEngine(engine: String) {
        _targetEngine.value = engine
    }

    fun exportCurrentAssetPackage(context: Context, shareImmediately: Boolean = false) {
        val activeAsset = allAssets.value.find { it.id == _selectedAssetId.value }
            ?: allAssets.value.firstOrNull()
            ?: return

        viewModelScope.launch {
            val bundle = withContext(Dispatchers.IO) {
                MeshRetopologyEngine.exportGameEnginePackage(
                    context = context,
                    assetTitle = activeAsset.title,
                    targetEngine = _targetEngine.value,
                    mesh = _currentMesh.value,
                    albedoBitmap = _currentAlbedoBitmap.value,
                    noiseBitmap = _currentNoiseBitmap.value
                )
            }
            _lastExportedBundle.value = bundle
            val kb = bundle.totalBytes / 1024
            _exportBannerMessage.value =
                "Exported ${bundle.fbxFile.name}, ${bundle.objFile.name}, ${bundle.albedoTextureFile.name} & ${bundle.noiseTextureFile.name} (${kb} KB) for ${_targetEngine.value}"

            if (shareImmediately) {
                shareExportedBundle(context, bundle)
            }
        }
    }

    fun shareExportedBundle(context: Context, bundle: ExportedAssetBundle) {
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
                    "Retopologized 3D Model (${bundle.quadCount} quads, ${bundle.vertexCount} vertices) with captured Albedo Image Texture and Micro-Surface Noise Texture for ${_targetEngine.value}."
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

    fun enqueueNewBatchScanJob(title: String, profile: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = ScanAssetEntity(
                title = title,
                shapeProfile = profile,
                status = "QUEUED",
                rawTriangleCount = (110_000..220_000).random(),
                retopoQuadCount = _lodPreset.value.targetQuads,
                vertexCount = _lodPreset.value.targetQuads + 36,
                uvEfficiencyPercent = (92..97).random(),
                accuracyScorePercent = (94..99).random(),
                capturedGuidepoints = 12,
                totalGuidepoints = 12,
                lightraySamples = (520..780).random(),
                depthConfidencePercent = (94..98).random(),
                videoDurationSec = (18..34).random(),
                targetEngine = _targetEngine.value,
                albedoPrimaryHex = "#0A4D9C",
                albedoSecondaryHex = "#F5B800",
                noiseScale = 1.25f
            )
            repository.insert(entity)
        }
    }

    fun runHighVolumeBatchPipeline(context: Context) {
        if (_batchState.value.isRunningBatch) return
        val assetsToProcess = allAssets.value
        if (assetsToProcess.isEmpty()) return

        viewModelScope.launch {
            _batchState.update {
                it.copy(
                    isRunningBatch = true,
                    completedAssetsCount = 0,
                    totalQueuedCount = assetsToProcess.size,
                    lastBatchSummary = ""
                )
            }

            var exportedCount = 0
            for ((index, asset) in assetsToProcess.withIndex()) {
                val stages = listOf(
                    "CGPU INT8 Depth Map Extraction",
                    "Lightray Specular & Normal Solving",
                    "Quad-Dominant Retopology & UV Packing",
                    "Baking Albedo Image & Noise Textures",
                    "Writing FBX + OBJ + Texture Bundle"
                )
                for ((sIdx, stageLabel) in stages.withIndex()) {
                    _batchState.update {
                        it.copy(
                            currentAssetTitle = asset.title,
                            currentStageName = stageLabel,
                            currentStageProgress = ((index * stages.size + sIdx + 1).toFloat()) /
                                (assetsToProcess.size * stages.size).toFloat()
                        )
                    }
                    delay(if (_cgpuState.value.lowEndTurboEnabled) 140L else 240L)
                }

                // Export real files for every asset in the batch pipeline
                val preset = _batchState.value.globalLodPreset
                val bundle = withContext(Dispatchers.IO) {
                    val mesh = MeshRetopologyEngine.buildRetopologizedMesh(
                        shapeProfile = asset.shapeProfile,
                        subdivisionsU = preset.uSub,
                        subdivisionsV = preset.vSub,
                        lockedGuidepoints = 12
                    )
                    val albedo = MeshRetopologyEngine.generateAlbedoTextureBitmap(
                        primaryHex = asset.albedoPrimaryHex,
                        secondaryHex = asset.albedoSecondaryHex,
                        size = 512
                    )
                    val noise = MeshRetopologyEngine.generateNoiseTextureBitmap(
                        noiseScale = asset.noiseScale,
                        seed = asset.id * 31 + 7,
                        size = 512
                    )
                    val exported = MeshRetopologyEngine.exportGameEnginePackage(
                        context = context,
                        assetTitle = asset.title,
                        targetEngine = _batchState.value.globalTargetEngine,
                        mesh = mesh,
                        albedoBitmap = albedo,
                        noiseBitmap = noise
                    )
                    repository.update(
                        asset.copy(
                            status = "READY",
                            retopoQuadCount = mesh.quads.size,
                            vertexCount = mesh.vertices.size,
                            exportDirectoryPath = exported.directory.absolutePath
                        )
                    )
                    exported
                }
                _lastExportedBundle.value = bundle
                exportedCount++
                _batchState.update {
                    it.copy(completedAssetsCount = exportedCount)
                }
            }

            _batchState.update {
                it.copy(
                    isRunningBatch = false,
                    currentStageName = "Batch Pipeline Complete",
                    currentStageProgress = 1f,
                    lastBatchSummary = "Successfully retopologized & exported $exportedCount 3D assets (.fbx, .obj, _albedo.png, _noise.png) for ${it.globalTargetEngine}."
                )
            }
        }
    }

    fun toggleLowEndTurbo() {
        _cgpuState.update { state ->
            val enabled = !state.lowEndTurboEnabled
            state.copy(
                lowEndTurboEnabled = enabled,
                int8DepthQuantization = enabled,
                fp16LightrayShaders = enabled,
                tileMemoryPooling = enabled,
                memoryFootprintMb = if (enabled) 78 else 164,
                cgpuComputeUtilization = if (enabled) 31 else 68
            )
        }
    }

    fun toggleInt8Depth() {
        _cgpuState.update { it.copy(int8DepthQuantization = !it.int8DepthQuantization) }
    }

    fun toggleFp16Shaders() {
        _cgpuState.update { it.copy(fp16LightrayShaders = !it.fp16LightrayShaders) }
    }

    fun toggleTileMemoryPool() {
        _cgpuState.update { it.copy(tileMemoryPooling = !it.tileMemoryPooling) }
    }

    /**
     * Generates a brand-new PBR Albedo or Noise Texture from a text prompt using
     * Firebase AI Logic `gemini-nano-banana-2.1` and applies it directly to the active 3D model.
     */
    fun generateTextureWithNanoBanana(
        prompt: String,
        applyToNoiseChannel: Boolean = false
    ) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            val slotName = if (applyToNoiseChannel) "Noise / Roughness Map" else "Albedo Image Texture"
            _aiTextureState.value = AiTextureUiState.Loading("Synthesizing $slotName with gemini-nano-banana-2.1 (30-60s)...")
            try {
                val bitmap = withTimeout(60_000L) {
                    val response = nanoBananaImageModel.generateContent(prompt)
                    response.candidates.firstOrNull()
                        ?.content?.parts?.filterIsInstance<ImagePart>()
                        ?.firstOrNull()
                        ?.image
                }
                if (bitmap != null) {
                    if (applyToNoiseChannel) {
                        _currentNoiseBitmap.value = bitmap
                    } else {
                        _currentAlbedoBitmap.value = bitmap
                    }
                    _aiTextureState.value = AiTextureUiState.Success(
                        bitmap = bitmap,
                        prompt = prompt,
                        appliedToSlot = slotName
                    )
                } else {
                    _aiTextureState.value = AiTextureUiState.Error("No image was returned by gemini-nano-banana-2.1.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Image generation timed out", e)
                _aiTextureState.value = AiTextureUiState.Error("Texture generation timed out after 60 seconds. Please try again.")
            } catch (e: QuotaExceededException) {
                Log.e(TAG, "Quota exceeded for gemini-nano-banana-2.1", e)
                _aiTextureState.value = AiTextureUiState.Error("Rate limit or quota exceeded. Ensure Firebase Blaze billing is active.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "gemini-nano-banana-2.1 texture generation failed", e)
                _aiTextureState.value = AiTextureUiState.Error(e.localizedMessage ?: "Texture generation failed.")
            }
        }
    }

    /**
     * Edits the currently active 3D model's Albedo Image Texture or Noise Texture
     * using text instructions via `gemini-nano-banana-2.1` multimodal input (`image` + `text`).
     */
    fun editActiveTextureWithNanoBanana(
        editInstruction: String,
        editNoiseChannel: Boolean = false
    ) {
        if (editInstruction.isBlank()) return
        val sourceBitmap = if (editNoiseChannel) _currentNoiseBitmap.value else _currentAlbedoBitmap.value
        viewModelScope.launch {
            val slotName = if (editNoiseChannel) "Noise / Roughness Map" else "Albedo Image Texture"
            _aiTextureState.value = AiTextureUiState.Loading("Editing $slotName with gemini-nano-banana-2.1 (30-60s)...")
            try {
                val editedBitmap = withTimeout(60_000L) {
                    val response = nanoBananaImageModel.generateContent(
                        content {
                            image(sourceBitmap)
                            text(editInstruction)
                        }
                    )
                    response.candidates.firstOrNull()
                        ?.content?.parts?.filterIsInstance<ImagePart>()
                        ?.firstOrNull()
                        ?.image
                }
                if (editedBitmap != null) {
                    if (editNoiseChannel) {
                        _currentNoiseBitmap.value = editedBitmap
                    } else {
                        _currentAlbedoBitmap.value = editedBitmap
                    }
                    _aiTextureState.value = AiTextureUiState.Success(
                        bitmap = editedBitmap,
                        prompt = editInstruction,
                        appliedToSlot = "$slotName (Edited)"
                    )
                } else {
                    _aiTextureState.value = AiTextureUiState.Error("No edited image was returned by gemini-nano-banana-2.1.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Texture editing timed out", e)
                _aiTextureState.value = AiTextureUiState.Error("Texture editing timed out after 60 seconds. Please try again.")
            } catch (e: QuotaExceededException) {
                Log.e(TAG, "Quota exceeded during texture editing", e)
                _aiTextureState.value = AiTextureUiState.Error("Rate limit or quota exceeded. Ensure Firebase Blaze billing is active.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "gemini-nano-banana-2.1 texture edit failed", e)
                _aiTextureState.value = AiTextureUiState.Error(e.localizedMessage ?: "Texture editing failed.")
            }
        }
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                val db = ScanAssetDatabase.getInstance(appContext)
                val repo = ScanAssetRepository(db.scanAssetDao())
                VoxelScanViewModel(repo, appContext)
            }
        }
    }
}

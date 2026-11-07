# VoxelScan 3D — Real Camera & IMU Photogrammetry Re-Implementation

Re-architects **VoxelScan 3D** from a simulated profile generator into a genuine **CameraX + IMU Sensor 3D Photogrammetry & Retopology Pipeline** that automatically activates the device camera, tracks real phone orientation (`TYPE_ROTATION_VECTOR` / gyroscope / compass) to lock 12 orbital guidepoints, extracts real multi-angle RGB keyframes and Sobel/gradient depth maps from the video stream, reconstructs the scanned object's actual 3D silhouette and surface relief, and projects the captured camera pixels directly onto the exported `.fbx`, `.obj`, `_albedo.png`, and `_noise.png` assets.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following core behaviors have been confirmed from your answers and will replace the previous static geometry presets:

- **Confirmed Decision 1 — Real Camera Frame Capture with IMU Sensor Pose Tracking**: The scanner immediately requests camera access on launch, streams live frames via CameraX `ImageAnalysis` (`ImageProxy.toBitmap()`), and pairs each captured keyframe with its real 3D camera pose (`azimuth`, `pitch`, `roll` from Android `SensorManager` `TYPE_ROTATION_VECTOR` / `TYPE_GAME_ROTATION_VECTOR`).
- **Confirmed Decision 2 — Automatic Gyroscope & Compass Angle Tracking with Manual Override**: Moving or orbiting your phone around the subject automatically rotates the live 3D geodesic HUD dome and snaps/locks each of the 12 orbital guidepoints (`GP-01` to `GP-12` every 30° of azimuth across upper/lower rings) as your camera aligns with the target angle, while preserving a manual **Lock Keyframe Now** button for stationary/turntable or emulator use.
- **Confirmed Decision 3 — Real Camera Pixel UV Projection & High-Frequency Noise Extraction**: Instead of drawing synthetic blue/yellow stripes, the pipeline stitches your captured multi-angle camera `Bitmap` keyframes into a cylindrical UV **Albedo Image Texture (`_albedo.png`)**, extracts high-frequency luminance micro-detail from your real camera frames into the **Noise / Roughness Texture (`_noise.png`)**, and renders those real captured camera colors directly onto the 3D mesh quads in the interactive 3D viewport.

---

## 1. Overview & Core Concept

- **What It Does**: Records a real-time multi-angle video scan of any physical object while guiding the user with a live IMU-driven 3D geodesic HUD, real-time Sobel edge + monocular depth heatmap, and directional lightray vectors. During deep processing, it carves and displaces a quad-dominant retopologized 3D mesh from the captured keyframes' foreground silhouettes and depth maps, projects the real camera pixels onto the UV unwrap, and exports a game-ready package (`.fbx`, `.obj`, `.mtl`, `_albedo.png`, `_noise.png`) for Unreal Engine and Unity.
- **Target Audience / Persona**: Game developers, technical artists, and 3D creators capturing real-world props on Android devices (including budget/low-end hardware via CGPU INT8/FP16 optimizations).
- **Key Value**: Every scan produces a unique 3D model and real photographic texture derived directly from what the camera actually recorded.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Zero-Friction Camera & IMU Launch**: Opening the **Scanner** tab immediately requests `CAMERA` permission if not yet granted and binds both `PreviewView` and a high-speed `ImageAnalysis` pipeline alongside `SensorManager` orientation listeners.
  2. **Live IMU-Guided Orbital Capture**: As the user tilts and orbits their phone around the object, the HUD dome rotates in real time to match the device's physical orientation. A live alignment reticle shows the exact angular delta (e.g., *"Rotate 8° Right, Tilt +4° Up to lock GP-03"*). When angular error drops below `< 8°` with low motion blur, the app triggers a crisp haptic pulse (`GUIDEPOINT_LOCK`), captures a full-color RGB `Bitmap` + 32×32 depth/silhouette slice for that angle, and marks the guidepoint locked with a thumbnail badge in the bottom keyframe strip.
  3. **Real Multi-View 3D Reconstruction & Retopology**: Tapping **Finish & Deep Process** runs the multi-view reconstruction solver:
     - *Stage 1 (Silhouette & Depth Hull Carving)*: Computes foreground object radius and depth displacement per azimuth/elevation sector from the captured RGB keyframes.
     - *Stage 2 (Lightray Specular & Normal Compensation)*: Estimates incident light direction from frame highlight gradients to de-light harsh shadows and compute accurate vertex normals.
     - *Stage 3 (Quad-Dominant Retopology)*: Builds a clean, manifold quad mesh (`1.1K`, `2.5K`, or `4.8K` quads) matching the scanned object's true proportions and surface contours.
     - *Stage 4 (Photographic UV Projection & Noise Extraction)*: Projects the captured camera keyframes across the UV atlas (`_albedo.png`) and extracts a high-pass micro-surface detail map (`_noise.png`).
  4. **True Texture-Mapped 3D Viewport & Export**: In the **3D Mesh** tab, each quad on the 3D canvas samples its actual UV color from the captured `_albedo.png` bitmap shaded by directional lighting, allowing the user to inspect their real scanned object in 3D before exporting `.fbx`, `.obj`, `_albedo.png`, and `_noise.png`.
- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Smooth Glossy Translucent Industrial Studio UI (`Simplicity > Design`), strictly non-gradient and non-dark-themed.
  - *Color Palette*: Porcelain & Glossy Translucent White (`#FFFFFF` / `#F8FAFE`), Royal Cobalt Blue (`#0A4D9C` / `#1565C0`), and Solar Calibration Yellow (`#F5B800` / `#FFF6D6`).
  - *Typography*: Locally bundled **Space Grotesk** (headers), **Plus Jakarta Sans** (clean body copy), and **JetBrains Mono** (live IMU angles, depth confidence, captured keyframe count, and polycount telemetry).
- **Interactive Feedback & Motion**:
  - Live strip of captured camera keyframe thumbnails under the scanner viewport so the user can visually see every angle captured in real time.
  - Distinct tactile haptic feedback pulses on guidepoint lock, frame capture, retopology completion, batch execution, and file export.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1 — Multi-View Silhouette + Monocular Depth Displacement from Real Camera Frames**:
  - *Chosen Approach*: For each captured orbital keyframe (`GP-01`..`GP-12`), analyze a 32×32 luminance, chroma-contrast, and Sobel-gradient depth buffer from the live `ImageProxy.toBitmap()` frame to segment the central subject's horizontal/vertical profile and radial depth relief, then fuse all captured angles into a 3D cylindrical/spherical voxel-hull mesh.
  - *Why*: Runs deterministically on 100% of Android phones (even low-end devices without hardware ToF/LiDAR sensors) while ensuring the 3D geometry genuinely reflects the scanned object's shape and proportions.
  - *Alternatives Considered*: Requiring ARCore Depth API exclusively (fails on devices without ARCore certification or emulators).
- **Decision 2 — Real-Time IMU Sensor Fusion with Fallback Touch/Manual Orbit**:
  - *Chosen Approach*: Register `Sensor.TYPE_ROTATION_VECTOR` (falling back to `TYPE_GAME_ROTATION_VECTOR` or `TYPE_ACCELEROMETER` + `TYPE_MAGNETIC_FIELD`) via `SensorManager` to drive live `azimuthDeg` (0–360°) and `pitchDeg` (-90–+90°). When angular distance to an unlocked guidepoint is `< 10°`, automatically capture and lock that angle's RGB frame; also allow tapping **Capture Angle Keyframe** manually if scanning an object on a rotating turntable.
  - *Why*: Gives a true physical AR-style scanning experience when moving the phone around an object, while remaining 100% operable on a desk or turntable.
- **Decision 3 — Real Photographic UV Atlas + High-Pass Luminance Noise Map**:
  - *Chosen Approach*: Stitch the captured camera keyframes horizontally and vertically across cylindrical UV coordinates `(u, v)` with cosine feathering between adjacent orbital views to produce `_albedo.png`, and apply a 3×3 Laplacian high-pass filter on the captured camera luminance to produce the micro-surface `_noise.png` texture.
  - *Why*: Guarantees both exported textures and the 3D viewport reflect the real visual appearance and surface grain of the scanned object.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

```
┌────────────────────────────────────────────────────────────────────────────┐
│                        MainActivity (Edge-to-Edge UI)                      │
│          configureAppCheck(this, intent) + Auto Camera Permission          │
└─────────────────────────────────────┬──────────────────────────────────────┘
                                      │
                                      ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                           VoxelScanViewModel                               │
│  • Live IMU Pose State (azimuth, pitch, roll, angularVelocity, stability)  │
│  • Captured Keyframe Ringbuffer (Up to 12 RGB Bitmaps + 32x32 Depth Maps)  │
│  • Retopology & Export State (Mesh3DData, UV Albedo Bitmap, Noise Bitmap)  │
└──────┬──────────────────────────────┼───────────────────────────────┬──────┘
       │                              │                               │
       ▼                              ▼                               ▼
┌──────────────────────┐   ┌──────────────────────────┐   ┌──────────────────┐
│ ImuPoseTracker &     │   │ RealPhotogrammetryEngine │   │ Firebase.ai      │
│ CameraX Analyzer     │   │ • Multi-View Hull Carver │   │ (Nano Banana 2.1)│
│ • RotationVector IMU │   │ • Sobel Depth Displacer  │   │ • Prompt Create  │
│ • ImageProxy->Bitmap │   │ • Quad Retopo UV Unwrap  │   │ • Active UV Map  │
│ • Auto GP Lock (<10°)│   │ • Real Camera UV Stitcher│   │   Image Editing  │
│ • Lightray Solver    │   │ • High-Pass Noise Baker  │   └──────────────────┘
└──────────────────────┘   │ • FBX 7.4 & OBJ Exporter │
                           └────────────┬─────────────┘
                                        │
                                        ▼
                           ┌──────────────────────────┐
                           │ Room ScanAssetDatabase   │
                           │ • Persists Scanned Meshes│
                           │ • Stores Saved PNG Paths │
                           │   for Albedo & Noise Maps│
                           └──────────────────────────┘
```

### Interactive Component & State Mapping
- **Automatic Camera Permission & Sensor Lifecycle (`ScannerScreen`)**:
  - `LaunchedEffect(Unit)` immediately launches the `Manifest.permission.CAMERA` request on first entry if not granted.
  - `DisposableEffect(lifecycleOwner)` binds `SensorManager` (`TYPE_ROTATION_VECTOR`, `SENSOR_DELAY_GAME`) to stream real-time device `azimuthDeg`, `pitchDeg`, and `angularSpeedDegPerSec` into `VoxelScanViewModel`.
- **Live CameraX Frame Capture & Auto-Lock (`RealtimeCameraAnalyzer`)**:
  - Converts each `ImageProxy` frame to an upright `Bitmap` (applying `imageProxy.imageInfo.rotationDegrees`), computes a 16×16 live depth/edge confidence grid and dominant lightray vector, and checks if the device's current `(azimuthDeg, pitchDeg)` aligns with the next unlocked orbital guidepoint while device motion is steady (`angularSpeed < 25°/s`).
  - When aligned (or when the user taps **Lock GP / Capture Keyframe**), stores a `CapturedScanKeyframe(guidepointIndex, azimuth, pitch, rgbBitmap, depthSlice32x32, radialProfile)` and displays its live thumbnail in the **Captured Keyframes Strip**.
- **True Multi-View 3D Reconstruction & Texture Baking (`MeshRetopologyEngine`)**:
  - `reconstructMeshFromKeyframes(keyframes, lodPreset)` builds the 3D vertex rings by interpolating the radial foreground silhouette and Sobel depth relief across all captured keyframes, computing smooth vertex normals and cylindrical `(u, v)` coordinates.
  - `stitchCameraAlbedoTexture(keyframes)` blends the real captured camera bitmaps into a seamless 512×512 `_albedo.png` and samples each quad's `(u, v)` center color directly into `QuadFace.sampledAlbedoColor` so the interactive 3D viewport renders the real scanned object appearance.
  - `extractHighFrequencyNoiseTexture(albedoBitmap)` runs a multi-scale high-pass detail extractor on the captured camera texture to produce a genuine `_noise.png` roughness/bump map for Unreal Engine and Unity.

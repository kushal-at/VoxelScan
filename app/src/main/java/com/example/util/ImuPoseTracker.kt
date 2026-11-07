package com.example.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Immutable 6-DoF / 3D pose snapshot combining fused orientation (Rotation Vector),
 * 3-axis angular velocity (Gyroscope), and 3-axis linear acceleration & gravity (Accelerometer).
 */
data class ImuPoseSnapshot(
    val timestampNs: Long = SystemClock.elapsedRealtimeNanos(),
    val azimuthDeg: Int = 0,
    val pitchDeg: Int = 15,
    val rollDeg: Int = 0,
    val quaternionW: Float = 1f,
    val quaternionX: Float = 0f,
    val quaternionY: Float = 0f,
    val quaternionZ: Float = 0f,
    val rotationMatrix: List<Float> = IDENTITY_MATRIX_9,
    val gyroXRadPerSec: Float = 0f,
    val gyroYRadPerSec: Float = 0f,
    val gyroZRadPerSec: Float = 0f,
    val angularSpeedDegPerSec: Float = 0f,
    val accelXMps2: Float = 0f,
    val accelYMps2: Float = 0f,
    val accelZMps2: Float = 9.81f,
    val linearAccelXMps2: Float = 0f,
    val linearAccelYMps2: Float = 0f,
    val linearAccelZMps2: Float = 0f,
    val linearAccelMagnitudeMps2: Float = 0f,
    val translationOffsetMetersX: Float = 0f,
    val translationOffsetMetersY: Float = 0f,
    val translationOffsetMetersZ: Float = 0f,
    val motionStabilityPercent: Int = 98,
    val syncOffsetMs: Float = 0.6f,
    val isSteadyForCapture: Boolean = true,
    val hasHardwareSensor: Boolean = false,
    val hasRotationVectorSensor: Boolean = false,
    val hasGyroscopeSensor: Boolean = false,
    val hasAccelerometerSensor: Boolean = false,
    val activeSensorsLabel: String = "ROT_VEC + GYRO + ACCEL",
    val sensorAccuracy: Int = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
) {
    companion object {
        val IDENTITY_MATRIX_9: List<Float> = listOf(
            1f, 0f, 0f,
            0f, 1f, 0f,
            0f, 0f, 1f
        )
    }
}

/**
 * Real-time `SensorManager` implementation that simultaneously captures and fuses hardware data from:
 * 1. `Sensor.TYPE_ROTATION_VECTOR` (with fallback to `TYPE_GAME_ROTATION_VECTOR`) for drift-free 3D quaternion & rotation matrix orientation.
 * 2. `Sensor.TYPE_GYROSCOPE` for high-frequency 3-axis angular velocity (`wx, wy, wz`), between-sample orientation integration, and rotational blur gating.
 * 3. `Sensor.TYPE_ACCELEROMETER` for 3-axis gravity extraction, high-pass linear acceleration (`ax, ay, az`), ZUPT-clamped 3D translation estimation, and translational shake detection.
 *
 * Maintains a 128-slot timestamped ringbuffer (`elapsedRealtimeNanos`) for sub-millisecond interpolation
 * against CameraX `ImageProxy.imageInfo.timestamp`.
 */
class ImuPoseTracker(
    context: Context,
    private val onPoseUpdated: (ImuPoseSnapshot) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val rotationVectorSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val gyroscopeSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accelerometerSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val rawRotationMatrix = FloatArray(9).apply {
        this[0] = 1f; this[4] = 1f; this[8] = 1f
    }
    private val remappedRotationMatrix = FloatArray(9).apply {
        this[0] = 1f; this[4] = 1f; this[8] = 1f
    }
    private val orientationAngles = FloatArray(3)
    private val quaternion = FloatArray(4).apply { this[0] = 1f }

    // Orientation state (degrees & unit quaternion)
    private var lastAzimuthDeg: Int = 0
    private var lastPitchDeg: Int = 15
    private var lastRollDeg: Int = 0
    private var continuousYawDeg: Float = 0f
    private var continuousPitchDeg: Float = 15f
    private var continuousRollDeg: Float = 0f
    private var lastQw: Float = 1f
    private var lastQx: Float = 0f
    private var lastQy: Float = 0f
    private var lastQz: Float = 0f

    // Gyroscope state (rad/s & deg/s)
    private var lastGyroXRad: Float = 0f
    private var lastGyroYRad: Float = 0f
    private var lastGyroZRad: Float = 0f
    private var currentAngularSpeedDegPerSec: Float = 0f
    private var lastGyroTimestampNs: Long = 0L
    private var lastRotationTimestampNs: Long = 0L

    // Accelerometer & Gravity / Linear Acceleration / ZUPT 3D Motion state
    private val gravity = FloatArray(3) { idx -> if (idx == 2) 9.81f else 0f }
    private var isGravityInitialized = false
    private var lastAccelX: Float = 0f
    private var lastAccelY: Float = 0f
    private var lastAccelZ: Float = 9.81f
    private var lastLinearAccelX: Float = 0f
    private var lastLinearAccelY: Float = 0f
    private var lastLinearAccelZ: Float = 0f
    private var currentLinearAccelMagMps2: Float = 0f
    private var lastAccelTimestampNs: Long = 0L

    // Damped Zero-Velocity-Updated (ZUPT) 3D camera translation offset (in meters)
    private var velocityX: Float = 0f
    private var velocityY: Float = 0f
    private var velocityZ: Float = 0f
    private var posOffsetX: Float = 0f
    private var posOffsetY: Float = 0f
    private var posOffsetZ: Float = 0f

    private var currentAccuracy: Int = SensorManager.SENSOR_STATUS_ACCURACY_HIGH

    // 128-slot circular buffer for CameraX <-> IMU hardware timestamp interpolation
    private val bufferCapacity = 128
    private val poseHistory = arrayOfNulls<ImuPoseSnapshot>(bufferCapacity)
    private var writeIndex = 0
    private val bufferLock = Any()

    val hasSensor: Boolean
        get() = rotationVectorSensor != null || gyroscopeSensor != null || accelerometerSensor != null

    val activeSensorsDescription: String
        get() {
            val parts = mutableListOf<String>()
            if (rotationVectorSensor != null) parts.add("ROT_VEC")
            if (gyroscopeSensor != null) parts.add("GYRO")
            if (accelerometerSensor != null) parts.add("ACCEL")
            return if (parts.isEmpty()) "TOUCH_ORBIT_FALLBACK" else parts.joinToString(" + ")
        }

    /**
     * Registers listeners for `TYPE_ROTATION_VECTOR`, `TYPE_GYROSCOPE`, and `TYPE_ACCELEROMETER`
     * simultaneously so all three sensor streams contribute to 3D pose and motion estimation.
     */
    fun start() {
        val sm = sensorManager ?: return
        rotationVectorSensor?.let { sensor ->
            sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscopeSensor?.let { sensor ->
            sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        accelerometerSensor?.let { sensor ->
            sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    /**
     * Unregisters all active sensor listeners and resets transient velocity accumulators.
     */
    fun stop() {
        sensorManager?.unregisterListener(this)
        lastGyroTimestampNs = 0L
        lastAccelTimestampNs = 0L
        lastRotationTimestampNs = 0L
        velocityX = 0f
        velocityY = 0f
        velocityZ = 0f
    }

    /**
     * Resets the relative 3D translation origin back to (0, 0, 0) when starting a new scan.
     */
    fun resetTranslationOrigin() {
        synchronized(bufferLock) {
            velocityX = 0f
            velocityY = 0f
            velocityZ = 0f
            posOffsetX = 0f
            posOffsetY = 0f
            posOffsetZ = 0f
        }
    }

    /**
     * Queries the IMU ringbuffer for the closest pose (or linearly/NLERP interpolated pose)
     * matching the CameraX `imageProxy.imageInfo.timestamp` (in nanoseconds).
     */
    fun getInterpolatedPoseAtTimestamp(cameraTimestampNs: Long): ImuPoseSnapshot {
        synchronized(bufferLock) {
            var bestBefore: ImuPoseSnapshot? = null
            var bestAfter: ImuPoseSnapshot? = null
            var closestSingle: ImuPoseSnapshot? = null
            var minAbsDiff = Long.MAX_VALUE

            for (i in 0 until bufferCapacity) {
                val sample = poseHistory[i] ?: continue
                val diff = abs(sample.timestampNs - cameraTimestampNs)
                if (diff < minAbsDiff) {
                    minAbsDiff = diff
                    closestSingle = sample
                }
                if (sample.timestampNs <= cameraTimestampNs) {
                    if (bestBefore == null || sample.timestampNs > bestBefore.timestampNs) {
                        bestBefore = sample
                    }
                } else {
                    if (bestAfter == null || sample.timestampNs < bestAfter.timestampNs) {
                        bestAfter = sample
                    }
                }
            }

            if (bestBefore != null && bestAfter != null && bestAfter.timestampNs > bestBefore.timestampNs) {
                val spanNs = (bestAfter.timestampNs - bestBefore.timestampNs).toFloat()
                val alpha = ((cameraTimestampNs - bestBefore.timestampNs).toFloat() / spanNs).coerceIn(0f, 1f)
                val interpAz = interpolateAngleDeg(bestBefore.azimuthDeg, bestAfter.azimuthDeg, alpha)
                val interpPitch = (bestBefore.pitchDeg + (bestAfter.pitchDeg - bestBefore.pitchDeg) * alpha).roundToInt()
                val interpRoll = interpolateSignedAngleDeg(bestBefore.rollDeg, bestAfter.rollDeg, alpha)

                val qInterp = nlerpQuaternion(
                    bestBefore.quaternionW, bestBefore.quaternionX, bestBefore.quaternionY, bestBefore.quaternionZ,
                    bestAfter.quaternionW, bestAfter.quaternionX, bestAfter.quaternionY, bestAfter.quaternionZ,
                    alpha
                )

                val interpSpeed = lerp(bestBefore.angularSpeedDegPerSec, bestAfter.angularSpeedDegPerSec, alpha)
                val interpLinAccel = lerp(bestBefore.linearAccelMagnitudeMps2, bestAfter.linearAccelMagnitudeMps2, alpha)
                val interpStability = computeStabilityPercent(interpSpeed, interpLinAccel)
                val syncMs = (minAbsDiff / 1_000_000f).coerceIn(0.1f, 50f)

                return bestBefore.copy(
                    timestampNs = cameraTimestampNs,
                    azimuthDeg = interpAz,
                    pitchDeg = interpPitch,
                    rollDeg = interpRoll,
                    quaternionW = qInterp[0],
                    quaternionX = qInterp[1],
                    quaternionY = qInterp[2],
                    quaternionZ = qInterp[3],
                    gyroXRadPerSec = lerp(bestBefore.gyroXRadPerSec, bestAfter.gyroXRadPerSec, alpha),
                    gyroYRadPerSec = lerp(bestBefore.gyroYRadPerSec, bestAfter.gyroYRadPerSec, alpha),
                    gyroZRadPerSec = lerp(bestBefore.gyroZRadPerSec, bestAfter.gyroZRadPerSec, alpha),
                    angularSpeedDegPerSec = interpSpeed,
                    linearAccelXMps2 = lerp(bestBefore.linearAccelXMps2, bestAfter.linearAccelXMps2, alpha),
                    linearAccelYMps2 = lerp(bestBefore.linearAccelYMps2, bestAfter.linearAccelYMps2, alpha),
                    linearAccelZMps2 = lerp(bestBefore.linearAccelZMps2, bestAfter.linearAccelZMps2, alpha),
                    linearAccelMagnitudeMps2 = interpLinAccel,
                    translationOffsetMetersX = lerp(bestBefore.translationOffsetMetersX, bestAfter.translationOffsetMetersX, alpha),
                    translationOffsetMetersY = lerp(bestBefore.translationOffsetMetersY, bestAfter.translationOffsetMetersY, alpha),
                    translationOffsetMetersZ = lerp(bestBefore.translationOffsetMetersZ, bestAfter.translationOffsetMetersZ, alpha),
                    motionStabilityPercent = interpStability,
                    syncOffsetMs = syncMs,
                    isSteadyForCapture = isMotionSteady(interpSpeed, interpLinAccel)
                )
            }

            if (closestSingle != null) {
                val syncMs = (minAbsDiff / 1_000_000f).coerceIn(0.1f, 99f)
                return closestSingle.copy(syncOffsetMs = syncMs)
            }

            return buildSnapshot(cameraTimestampNs, syncMs = 1.0f)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val ev = event ?: return
        when (ev.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR,
            Sensor.TYPE_GAME_ROTATION_VECTOR -> handleRotationVectorEvent(ev)
            Sensor.TYPE_GYROSCOPE -> handleGyroscopeEvent(ev)
            Sensor.TYPE_ACCELEROMETER -> handleAccelerometerEvent(ev)
        }
    }

    private fun handleRotationVectorEvent(ev: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rawRotationMatrix, ev.values)
        // Remap for portrait camera viewing (X -> X, Z -> Y) to avoid gimbal lock when holding phone upright
        val remappedOk = SensorManager.remapCoordinateSystem(
            rawRotationMatrix,
            SensorManager.AXIS_X,
            SensorManager.AXIS_Z,
            remappedRotationMatrix
        )
        val activeMatrix = if (remappedOk) remappedRotationMatrix else rawRotationMatrix
        SensorManager.getOrientation(activeMatrix, orientationAngles)
        SensorManager.getQuaternionFromVector(quaternion, ev.values)

        val rawAzimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
        val normalizedAzimuth = ((rawAzimuth + 360f) % 360f).roundToInt() % 360
        val pitch = (-Math.toDegrees(orientationAngles[1].toDouble())).roundToInt().coerceIn(-89, 89)
        val roll = Math.toDegrees(orientationAngles[2].toDouble()).roundToInt().coerceIn(-180, 180)

        // Fallback angular speed estimate if hardware gyroscope is not present
        if (gyroscopeSensor == null && lastRotationTimestampNs != 0L) {
            val dtSec = ((ev.timestamp - lastRotationTimestampNs) / 1_000_000_000f).coerceIn(0.005f, 0.5f)
            val dAz = angularDeltaDeg(normalizedAzimuth, lastAzimuthDeg)
            val dPitch = abs(pitch - lastPitchDeg)
            currentAngularSpeedDegPerSec = sqrt((dAz * dAz + dPitch * dPitch).toDouble()).toFloat() / dtSec
        }

        lastRotationTimestampNs = ev.timestamp
        continuousYawDeg = normalizedAzimuth.toFloat()
        continuousPitchDeg = pitch.toFloat()
        continuousRollDeg = roll.toFloat()
        lastAzimuthDeg = normalizedAzimuth
        lastPitchDeg = pitch
        lastRollDeg = roll
        lastQw = quaternion[0]
        lastQx = quaternion[1]
        lastQy = quaternion[2]
        lastQz = quaternion[3]

        recordAndEmitPose(ev.timestamp)
    }

    private fun handleGyroscopeEvent(ev: SensorEvent) {
        if (ev.values.size < 3) return
        val wx = ev.values[0]
        val wy = ev.values[1]
        val wz = ev.values[2]
        lastGyroXRad = wx
        lastGyroYRad = wy
        lastGyroZRad = wz

        val radPerSecMag = sqrt((wx * wx + wy * wy + wz * wz).toDouble()).toFloat()
        currentAngularSpeedDegPerSec = Math.toDegrees(radPerSecMag.toDouble()).toFloat()

        if (lastGyroTimestampNs != 0L) {
            val dtSec = ((ev.timestamp - lastGyroTimestampNs) / 1_000_000_000f).coerceIn(0.0005f, 0.25f)
            val dPitchDeg = Math.toDegrees(wx.toDouble()).toFloat() * dtSec
            val dRollDeg = Math.toDegrees(wy.toDouble()).toFloat() * dtSec
            val dYawDeg = -Math.toDegrees(wz.toDouble()).toFloat() * dtSec

            // Integrate gyroscope between rotation-vector updates or fuse with accelerometer when no rotation sensor exists
            continuousYawDeg = ((continuousYawDeg + dYawDeg) % 360f + 360f) % 360f
            continuousPitchDeg = (continuousPitchDeg + dPitchDeg).coerceIn(-89f, 89f)
            continuousRollDeg = (continuousRollDeg + dRollDeg).coerceIn(-180f, 180f)

            if (rotationVectorSensor == null) {
                lastAzimuthDeg = continuousYawDeg.roundToInt() % 360
                lastPitchDeg = continuousPitchDeg.roundToInt()
                lastRollDeg = continuousRollDeg.roundToInt()
                updateQuaternionFromEuler(lastAzimuthDeg, lastPitchDeg, lastRollDeg)
            }
        }
        lastGyroTimestampNs = ev.timestamp
        recordAndEmitPose(ev.timestamp)
    }

    private fun handleAccelerometerEvent(ev: SensorEvent) {
        if (ev.values.size < 3) return
        val ax = ev.values[0]
        val ay = ev.values[1]
        val az = ev.values[2]
        lastAccelX = ax
        lastAccelY = ay
        lastAccelZ = az

        if (!isGravityInitialized) {
            gravity[0] = ax
            gravity[1] = ay
            gravity[2] = az
            isGravityInitialized = true
        }

        // Isolate gravity with a low-pass filter and extract high-pass linear acceleration
        val linear = computeLinearAcceleration(
            rawAx = ax,
            rawAy = ay,
            rawAz = az,
            gravity = gravity,
            alpha = 0.84f
        )
        lastLinearAccelX = linear[0]
        lastLinearAccelY = linear[1]
        lastLinearAccelZ = linear[2]
        currentLinearAccelMagMps2 = linear[3]

        // Update damped 3D translation offset with Zero-Velocity Update (ZUPT) drift suppression
        if (lastAccelTimestampNs != 0L) {
            val dtSec = ((ev.timestamp - lastAccelTimestampNs) / 1_000_000_000f).coerceIn(0.001f, 0.2f)
            if (currentLinearAccelMagMps2 < 0.22f && currentAngularSpeedDegPerSec < 8f) {
                // Zero-velocity clamp when phone is nearly stationary
                velocityX *= 0.25f
                velocityY *= 0.25f
                velocityZ *= 0.25f
            } else {
                velocityX = (velocityX + lastLinearAccelX * dtSec) * 0.90f
                velocityY = (velocityY + lastLinearAccelY * dtSec) * 0.90f
                velocityZ = (velocityZ + lastLinearAccelZ * dtSec) * 0.90f
                posOffsetX = (posOffsetX + velocityX * dtSec).coerceIn(-1.5f, 1.5f)
                posOffsetY = (posOffsetY + velocityY * dtSec).coerceIn(-1.5f, 1.5f)
                posOffsetZ = (posOffsetZ + velocityZ * dtSec).coerceIn(-1.5f, 1.5f)
            }
        }
        lastAccelTimestampNs = ev.timestamp

        // Complementary filter fallback when hardware TYPE_ROTATION_VECTOR is unavailable
        if (rotationVectorSensor == null) {
            val accelPitch = (atan2(ay.toDouble(), sqrt((ax * ax + az * az).toDouble())) * (180.0 / PI)).toFloat()
            val accelRoll = (atan2(-ax.toDouble(), az.toDouble()) * (180.0 / PI)).toFloat()

            if (gyroscopeSensor != null) {
                // Fuse 96% gyroscope integration + 4% accelerometer gravity tilt
                continuousPitchDeg = (0.96f * continuousPitchDeg + 0.04f * accelPitch).coerceIn(-89f, 89f)
                continuousRollDeg = (0.96f * continuousRollDeg + 0.04f * accelRoll).coerceIn(-180f, 180f)
            } else {
                continuousPitchDeg = accelPitch.coerceIn(-89f, 89f)
                continuousRollDeg = accelRoll.coerceIn(-180f, 180f)
                continuousYawDeg = ((continuousYawDeg + (continuousRollDeg / 18f)) + 360f) % 360f
            }

            lastAzimuthDeg = continuousYawDeg.roundToInt() % 360
            lastPitchDeg = continuousPitchDeg.roundToInt()
            lastRollDeg = continuousRollDeg.roundToInt()
            updateQuaternionFromEuler(lastAzimuthDeg, lastPitchDeg, lastRollDeg)
        }

        recordAndEmitPose(ev.timestamp)
    }

    private fun updateQuaternionFromEuler(azimuthDeg: Int, pitchDeg: Int, rollDeg: Int) {
        val yawRad = Math.toRadians(azimuthDeg.toDouble()) * 0.5
        val pitchRad = Math.toRadians(pitchDeg.toDouble()) * 0.5
        val rollRad = Math.toRadians(rollDeg.toDouble()) * 0.5

        val cy = cos(yawRad)
        val sy = sin(yawRad)
        val cp = cos(pitchRad)
        val sp = sin(pitchRad)
        val cr = cos(rollRad)
        val sr = sin(rollRad)

        lastQw = (cr * cp * cy + sr * sp * sy).toFloat()
        lastQx = (sr * cp * cy - cr * sp * sy).toFloat()
        lastQy = (cr * sp * cy + sr * cp * sy).toFloat()
        lastQz = (cr * cp * sy - sr * sp * cy).toFloat()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        currentAccuracy = accuracy
    }

    private fun buildSnapshot(timestampNs: Long, syncMs: Float): ImuPoseSnapshot {
        val stability = computeStabilityPercent(currentAngularSpeedDegPerSec, currentLinearAccelMagMps2)
        val steady = isMotionSteady(currentAngularSpeedDegPerSec, currentLinearAccelMagMps2)
        return ImuPoseSnapshot(
            timestampNs = timestampNs,
            azimuthDeg = lastAzimuthDeg,
            pitchDeg = lastPitchDeg,
            rollDeg = lastRollDeg,
            quaternionW = lastQw,
            quaternionX = lastQx,
            quaternionY = lastQy,
            quaternionZ = lastQz,
            rotationMatrix = remappedRotationMatrix.toList(),
            gyroXRadPerSec = lastGyroXRad,
            gyroYRadPerSec = lastGyroYRad,
            gyroZRadPerSec = lastGyroZRad,
            angularSpeedDegPerSec = currentAngularSpeedDegPerSec,
            accelXMps2 = lastAccelX,
            accelYMps2 = lastAccelY,
            accelZMps2 = lastAccelZ,
            linearAccelXMps2 = lastLinearAccelX,
            linearAccelYMps2 = lastLinearAccelY,
            linearAccelZMps2 = lastLinearAccelZ,
            linearAccelMagnitudeMps2 = currentLinearAccelMagMps2,
            translationOffsetMetersX = posOffsetX,
            translationOffsetMetersY = posOffsetY,
            translationOffsetMetersZ = posOffsetZ,
            motionStabilityPercent = stability,
            syncOffsetMs = syncMs,
            isSteadyForCapture = steady,
            hasHardwareSensor = hasSensor,
            hasRotationVectorSensor = rotationVectorSensor != null,
            hasGyroscopeSensor = gyroscopeSensor != null,
            hasAccelerometerSensor = accelerometerSensor != null,
            activeSensorsLabel = activeSensorsDescription,
            sensorAccuracy = currentAccuracy
        )
    }

    private fun recordAndEmitPose(timestampNs: Long) {
        val snapshot = buildSnapshot(timestampNs = timestampNs, syncMs = 0.5f)
        synchronized(bufferLock) {
            poseHistory[writeIndex] = snapshot
            writeIndex = (writeIndex + 1) % bufferCapacity
        }
        onPoseUpdated(snapshot)
    }

    companion object {
        private fun lerp(a: Float, b: Float, alpha: Float): Float = a + (b - a) * alpha

        fun isMotionSteady(angularSpeedDegPerSec: Float, linearAccelMagMps2: Float): Boolean {
            return angularSpeedDegPerSec < 36f && linearAccelMagMps2 < 1.35f
        }

        fun computeStabilityPercent(angularSpeedDegPerSec: Float, linearAccelMagMps2: Float): Int {
            val gyroPenalty = (angularSpeedDegPerSec * 0.9f).roundToInt()
            val accelPenalty = (linearAccelMagMps2 * 18f).roundToInt()
            return (100 - gyroPenalty - accelPenalty).coerceIn(10, 100)
        }

        fun angularDeltaDeg(a: Int, b: Int): Int {
            val diff = abs(a - b) % 360
            return if (diff > 180) 360 - diff else diff
        }

        fun interpolateAngleDeg(a: Int, b: Int, alpha: Float): Int {
            var delta = (b - a) % 360
            if (delta > 180) delta -= 360
            if (delta < -180) delta += 360
            return ((a + delta * alpha).roundToInt() % 360 + 360) % 360
        }

        fun interpolateSignedAngleDeg(a: Int, b: Int, alpha: Float): Int {
            var delta = (b - a) % 360
            if (delta > 180) delta -= 360
            if (delta < -180) delta += 360
            val raw = (a + delta * alpha).roundToInt()
            return when {
                raw > 180 -> raw - 360
                raw < -180 -> raw + 360
                else -> raw
            }
        }

        /**
         * Normalized Linear Quaternion Interpolation (NLERP) with shortest-hemisphere alignment.
         * Returns `[qw, qx, qy, qz]` normalized to unit length.
         */
        fun nlerpQuaternion(
            w1: Float, x1: Float, y1: Float, z1: Float,
            w2: Float, x2: Float, y2: Float, z2: Float,
            alpha: Float
        ): FloatArray {
            val t = alpha.coerceIn(0f, 1f)
            val dot = w1 * w2 + x1 * x2 + y1 * y2 + z1 * z2
            val sign = if (dot < 0f) -1f else 1f
            val rw = (1f - t) * w1 + t * (w2 * sign)
            val rx = (1f - t) * x1 + t * (x2 * sign)
            val ry = (1f - t) * y1 + t * (y2 * sign)
            val rz = (1f - t) * z1 + t * (z2 * sign)
            val norm = sqrt((rw * rw + rx * rx + ry * ry + rz * rz).toDouble()).toFloat().coerceAtLeast(1e-6f)
            return floatArrayOf(rw / norm, rx / norm, ry / norm, rz / norm)
        }

        /**
         * Applies a low-pass filter (`alpha`) to update `gravity` in-place and returns
         * `[linearAx, linearAy, linearAz, linearMagnitude]`.
         */
        fun computeLinearAcceleration(
            rawAx: Float,
            rawAy: Float,
            rawAz: Float,
            gravity: FloatArray,
            alpha: Float = 0.84f
        ): FloatArray {
            gravity[0] = alpha * gravity[0] + (1f - alpha) * rawAx
            gravity[1] = alpha * gravity[1] + (1f - alpha) * rawAy
            gravity[2] = alpha * gravity[2] + (1f - alpha) * rawAz

            val linX = rawAx - gravity[0]
            val linY = rawAy - gravity[1]
            val linZ = rawAz - gravity[2]
            val mag = sqrt((linX * linX + linY * linY + linZ * linZ).toDouble()).toFloat()
            return floatArrayOf(linX, linY, linZ, mag)
        }
    }
}

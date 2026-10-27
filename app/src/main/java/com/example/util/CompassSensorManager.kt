package com.example.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * High-precision, low-latency device compass sensor manager.
 * Listens to TYPE_ROTATION_VECTOR (with graceful fallback to Accelerometer + Magnetometer / Orientation)
 * and applies circular low-pass smoothing so heading responds in real time without jitter.
 */
class CompassSensorManager(
    context: Context,
    private val onHeadingChanged: (Float) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private var rotationVectorSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private var accelerometerSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var magneticSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private var orientationSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ORIENTATION)

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private val gravityValues = FloatArray(3)
    private val magneticValues = FloatArray(3)
    private var hasGravity = false
    private var hasMagnetic = false

    var currentHeading: Float = 0f
        private set

    private var isListening = false

    fun start() {
        if (isListening || sensorManager == null) return
        isListening = true

        if (rotationVectorSensor != null) {
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            if (accelerometerSensor != null && magneticSensor != null) {
                sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_UI)
                sensorManager.registerListener(this, magneticSensor, SensorManager.SENSOR_DELAY_UI)
            } else if (orientationSensor != null) {
                sensorManager.registerListener(this, orientationSensor, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    fun stop() {
        if (!isListening || sensorManager == null) return
        isListening = false
        sensorManager.unregisterListener(this)
        hasGravity = false
        hasMagnetic = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        var rawAzimuth: Float? = null

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                val azimuthRad = orientationAngles[0]
                var azimuthDeg = Math.toDegrees(azimuthRad.toDouble()).toFloat()
                if (azimuthDeg < 0f) azimuthDeg += 360f
                rawAzimuth = azimuthDeg
            }
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, gravityValues, 0, 3)
                hasGravity = true
                if (hasGravity && hasMagnetic) {
                    rawAzimuth = computeAzimuthFromGravityAndMagnetic()
                }
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magneticValues, 0, 3)
                hasMagnetic = true
                if (hasGravity && hasMagnetic) {
                    rawAzimuth = computeAzimuthFromGravityAndMagnetic()
                }
            }
            Sensor.TYPE_ORIENTATION -> {
                var azimuthDeg = event.values[0]
                if (azimuthDeg < 0f) azimuthDeg += 360f
                rawAzimuth = azimuthDeg
            }
        }

        rawAzimuth?.let { target ->
            // Smooth circular heading transition (handles 0° <-> 360° boundary)
            var diff = (target - currentHeading) % 360f
            if (diff > 180f) diff -= 360f
            if (diff < -180f) diff += 360f

            // EMA filter factor: 0.3 for responsive yet smooth compass rotation
            val smoothed = (currentHeading + 0.3f * diff) % 360f
            currentHeading = if (smoothed < 0f) smoothed + 360f else smoothed

            onHeadingChanged(currentHeading)
        }
    }

    private fun computeAzimuthFromGravityAndMagnetic(): Float? {
        val success = SensorManager.getRotationMatrix(rotationMatrix, null, gravityValues, magneticValues)
        if (success) {
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            var azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
            if (azimuth < 0f) azimuth += 360f
            return azimuth
        }
        return null
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

@Composable
fun rememberCompassHeading(enabled: Boolean = true): State<Float> {
    val context = LocalContext.current
    val headingState = remember { mutableFloatStateOf(0f) }

    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose {}

        val compass = CompassSensorManager(context) { newHeading ->
            headingState.floatValue = newHeading
        }
        compass.start()

        onDispose {
            compass.stop()
        }
    }

    return headingState
}

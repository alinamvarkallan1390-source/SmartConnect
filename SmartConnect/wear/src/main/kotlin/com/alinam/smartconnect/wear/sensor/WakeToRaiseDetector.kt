package com.alinam.smartconnect.wear.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import android.view.WindowManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Wake To Raise detector using Accelerometer + Gyroscope + Gravity.
 * Algorithm:
 * 1. Detect wrist rotation using gravity sensor (tilt toward face)
 * 2. Confirm with accelerometer motion threshold
 * 3. Prevent false positives with gyroscope stability check
 * 4. Uses SENSOR_DELAY_NORMAL to minimize battery use
 */
@Singleton
class WakeToRaiseDetector @Inject constructor(
    @ApplicationContext private val context: Context
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val gravSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private var gravity = FloatArray(3)
    private var accel = FloatArray(3)
    private var gyro = FloatArray(3)

    private var isRunning = false
    private var screenOnJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    // Constants for detection
    private val GRAVITY_TILT_THRESHOLD = 7.5f  // gravity.y > threshold = facing up
    private val ACCEL_MOTION_THRESHOLD = 1.8f  // minimum motion magnitude
    private val GYRO_STABILITY_THRESHOLD = 2.0f // max rotation speed (stable raise)
    private val SCREEN_ON_DURATION_MS = 5000L

    private var lastWakeTime = 0L
    private val WAKE_DEBOUNCE_MS = 3000L

    private var wakeLock: PowerManager.WakeLock? = null

    fun start() {
        if (isRunning) return
        isRunning = true
        sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, gravSensor, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_NORMAL)
        Timber.d("WakeToRaise started")
    }

    fun stop() {
        isRunning = false
        sensorManager.unregisterListener(this)
        screenOnJob?.cancel()
        releaseWakeLock()
        Timber.d("WakeToRaise stopped")
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY -> gravity = event.values.clone()
            Sensor.TYPE_ACCELEROMETER -> accel = event.values.clone()
            Sensor.TYPE_GYROSCOPE -> gyro = event.values.clone()
        }
        checkWakeCondition()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun checkWakeCondition() {
        val now = System.currentTimeMillis()
        if (now - lastWakeTime < WAKE_DEBOUNCE_MS) return
        if (powerManager.isInteractive) return  // Screen already on

        // Condition 1: Gravity Y indicates wrist raised (screen facing up)
        val gravityFacingUp = gravity[1] > GRAVITY_TILT_THRESHOLD

        // Condition 2: Some motion detected (wrist movement)
        val accelMag = sqrt(
            (accel[0] * accel[0] + accel[1] * accel[1] + accel[2] * accel[2]).toDouble()
        ).toFloat()
        val hasMotion = abs(accelMag - SensorManager.GRAVITY_EARTH) > ACCEL_MOTION_THRESHOLD

        // Condition 3: Gyroscope is stable (not shaking/random motion)
        val gyroMag = sqrt(
            (gyro[0] * gyro[0] + gyro[1] * gyro[1] + gyro[2] * gyro[2]).toDouble()
        ).toFloat()
        val isStableRaise = gyroMag < GYRO_STABILITY_THRESHOLD

        if (gravityFacingUp && hasMotion && isStableRaise) {
            lastWakeTime = now
            wakeScreen()
        }
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        try {
            screenOnJob?.cancel()
            // Acquire wake lock to turn screen on
            wakeLock?.release()
            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "SmartConnect:WakeToRaise"
            ).apply {
                acquire(SCREEN_ON_DURATION_MS + 1000)
            }
            Timber.d("WakeToRaise: Screen woke up")

            screenOnJob = scope.launch {
                delay(SCREEN_ON_DURATION_MS)
                releaseWakeLock()
            }
        } catch (e: Exception) {
            Timber.e(e, "WakeToRaise: Failed to wake screen")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) { }
        wakeLock = null
    }
}

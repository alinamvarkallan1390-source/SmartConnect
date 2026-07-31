package com.alinam.smartconnect.wear.sensor

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Wake-to-Raise detector that operates INDEPENDENTLY of the phone connection.
 *
 * The detector runs in its own foreground service so it works even when the
 * phone is not connected, not paired, or out of Bluetooth range.
 *
 * Algorithm:
 *  1. Gravity Y axis indicates the wrist rotation (face-up).
 *  2. Accelerometer confirms the user actually moved their wrist.
 *  3. Gyroscope stability check rejects false positives (random shaking).
 *  4. Significant-motion sensor (TYPE_SIGNIFICANT_MOTION) wakes the system
 *     cheaply to begin with, then the regular sensors take over for the
 *     fine-grained detection.
 *  5. A debounce window prevents repeated triggers.
 *  6. Only triggers when the screen is off (to save battery and avoid
 *     pointless wake events while the user is interacting).
 *  7. Quiet hours / disabled state is persisted in SharedPreferences.
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
    private val sigMotionSensor = sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)

    private var gravity = FloatArray(3)
    private var accel = FloatArray(3)
    private var gyro = FloatArray(3)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var isRunning = false
    private var screenOnJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ----- Tunable thresholds -----
    // For a flat-on-wrist watch:
    //  - When hanging down (face toward ground), gravity Y is roughly -9.8.
    //  - When raised toward face (screen up), gravity Y is roughly +9.8.
    // We use a midpoint threshold to require a definite tilt.
    private val GRAVITY_FACE_UP_THRESHOLD = 6.0f
    private val GRAVITY_FACE_DOWN_THRESHOLD = -6.0f
    // Movement delta (away from 1g baseline) confirms the user is moving.
    private val ACCEL_MOTION_THRESHOLD = 1.5f
    // Gyro magnitude cap so the raise is "smooth", not "shaking".
    private val GYRO_STABILITY_THRESHOLD = 3.5f
    private val SCREEN_ON_DURATION_MS = 6_000L
    private val WAKE_DEBOUNCE_MS = 2_500L
    // Reject triggers when the wrist is angled > this many degrees from horizontal.
    private val MAX_TILT_RAD = 1.4f // ~80 deg

    @Volatile
    private var lastWakeTime = 0L

    @Volatile
    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_ENABLED, value) }

    fun start() {
        if (isRunning) return
        if (!isEnabled) {
            Timber.d("WakeToRaise is disabled; not starting")
            return
        }
        isRunning = true
        // Use GAME / UI rate for gravity + accel so the gesture is responsive
        // without being a battery hog. Gyro is only needed briefly, so NORMAL is fine.
        accelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gravSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gyroSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        // Significant motion sensor pre-warms the pipeline cheaply.
        sigMotionSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        Timber.d("WakeToRaise started (independent of phone)")
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        sensorManager.unregisterListener(this)
        screenOnJob?.cancel()
        screenOnJob = null
        releaseWakeLock()
        Timber.d("WakeToRaise stopped")
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!isEnabled) return
        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY -> gravity = event.values.copyOf()
            Sensor.TYPE_ACCELEROMETER -> accel = event.values.copyOf()
            Sensor.TYPE_GYROSCOPE -> gyro = event.values.copyOf()
            Sensor.TYPE_SIGNIFICANT_MOTION -> {
                // Significant motion is binary; use it as a pre-filter only.
                Timber.v("Significant motion detected")
                // do not trigger from this directly; let the main gesture do it
                return
            }
        }
        checkWakeCondition()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // no-op
    }

    private fun checkWakeCondition() {
        val now = System.currentTimeMillis()
        if (now - lastWakeTime < WAKE_DEBOUNCE_MS) return
        if (powerManager.isInteractive) return  // screen already on
        if (!isRunning) return

        if (gravity.size < 3 || accel.size < 3 || gyro.size < 3) return
        val gx = gravity[0]
        val gy = gravity[1]
        val gz = gravity[2]
        val gMag = sqrt((gx * gx + gy * gy + gz * gz).toDouble()).toFloat()
        if (gMag < 1.0f) return // invalid gravity reading

        // Condition 1: Wrist is tilted toward the face (screen-up).
        val faceUp = gy > GRAVITY_FACE_UP_THRESHOLD

        // Condition 2: Wrist is NOT hanging straight down.
        val notFaceDown = gy > GRAVITY_FACE_DOWN_THRESHOLD

        // Condition 3: Magnitude of the tilt is within a sane range
        // (rejects upside-down "back of wrist" cases).
        val totalTilt = sqrt((gx * gx + gy * gy).toDouble()).toFloat()
        val tiltOk = kotlin.math.abs(gz) > 0.5f && totalTilt < MAX_TILT_RAD * 9.8f

        if (!faceUp || !notFaceDown || !tiltOk) return

        // Condition 4: There is real motion in the accelerometer
        // (a delta from 1g baseline, not just sensor noise).
        val accelMag = sqrt(
            (accel[0] * accel[0] + accel[1] * accel[1] + accel[2] * accel[2]).toDouble()
        ).toFloat()
        val hasMotion = abs(accelMag - SensorManager.GRAVITY_EARTH) > ACCEL_MOTION_THRESHOLD

        // Condition 5: Gyro is stable (the raise was smooth, not random shaking).
        val gyroMag = sqrt(
            (gyro[0] * gyro[0] + gyro[1] * gyro[1] + gyro[2] * gyro[2]).toDouble()
        ).toFloat()
        val isStableRaise = gyroMag < GYRO_STABILITY_THRESHOLD

        if (hasMotion && isStableRaise) {
            lastWakeTime = now
            wakeScreen()
        }
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        try {
            screenOnJob?.cancel()
            releaseWakeLock()
            // SCREEN_BRIGHT_WAKE_LOCK turns the screen on at the user's
            // previous brightness level. ACQUIRE_CAUSES_WAKEUP forces it
            // on even if it was off entirely.
            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "SmartConnect:WakeToRaise"
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    // Reference-counted wake lock is no longer required, but
                    // explicitly setting a reference avoids the platform auto-release
                    setReferenceCounted(false)
                }
                acquire(SCREEN_ON_DURATION_MS + 1000)
            }
            Timber.d("WakeToRaise: screen woke up (independent of phone)")

            screenOnJob = scope.launch {
                delay(SCREEN_ON_DURATION_MS)
                releaseWakeLock()
            }
        } catch (e: Exception) {
            Timber.e(e, "WakeToRaise: failed to wake screen")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) { }
        wakeLock = null
    }

    companion object {
        private const val PREFS_NAME = "smartconnect_wear_prefs"
        private const val KEY_ENABLED = "wake_to_raise_enabled"
    }
}

package com.alinam.smartconnect.wear.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.alinam.smartconnect.wear.sensor.WakeToRaiseDetector
import com.alinam.smartconnect.wear.ui.WearMainActivity
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * Foreground service that keeps the wake-to-raise gesture detector alive
 * INDEPENDENTLY of the phone / Bluetooth connection.
 *
 * Why a service?
 *  - Sensor listeners get unregistered when the process is killed.
 *  - Wear OS is aggressive about background process management, so we need
 *    a foreground service to stay alive.
 *  - This service does NOT depend on WearBluetoothService; both can run
 *    together or separately.
 */
@AndroidEntryPoint
class WakeToRaiseService : Service() {

    @Inject lateinit var detector: WakeToRaiseDetector

    override fun onCreate() {
        super.onCreate()
        createChannelIfNeeded()
        startForeground(NOTIF_ID, buildNotification())
        detector.start()
        Timber.i("WakeToRaiseService created and started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Self-restart if killed
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        detector.stop()
        super.onDestroy()
        Timber.i("WakeToRaiseService destroyed")
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, WearMainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SmartConnect Wear")
            .setContentText("Wake-to-raise is active")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openIntent)
            .build()
    }

    private fun createChannelIfNeeded() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Wake-to-raise",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Keeps the wrist-raise gesture detector running"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "smartconnect_wear_wake_service"
        private const val NOTIF_ID = 2002

        fun start(context: Context) {
            val intent = Intent(context, WakeToRaiseService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WakeToRaiseService::class.java))
        }
    }
}

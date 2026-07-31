package com.alinam.smartconnect.wear.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.alinam.smartconnect.shared.protocol.Message
import com.alinam.smartconnect.shared.protocol.MessageType
import com.alinam.smartconnect.wear.bluetooth.WearBluetoothManager
import com.alinam.smartconnect.wear.ui.WearMainActivity
import com.alinam.smartconnect.wear.util.DeviceInfoCollectorWear
import com.alinam.smartconnect.wear.util.FindPhoneHelper
import com.alinam.smartconnect.wear.util.VibrateHelper
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class WearBluetoothService : Service() {

    companion object {
        private const val CHANNEL_ID = "smartconnect_wear_service"
        private const val NOTIF_ID = 2001

        fun start(context: Context) {
            val intent = Intent(context, WearBluetoothService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WearBluetoothService::class.java))
        }
    }

    @Inject lateinit var btManager: WearBluetoothManager
    @Inject lateinit var vibrateHelper: VibrateHelper
    @Inject lateinit var findPhoneHelper: FindPhoneHelper
    @Inject lateinit var deviceInfoCollector: DeviceInfoCollectorWear

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()
    private var wakeLock: PowerManager.WakeLock? = null
    private var deviceInfoJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("در حال اجرا..."))
        acquireWakeLock()
        // wakeDetector is now started by WakeToRaiseService so it keeps
        // working even when this service is not running (e.g. no phone).
        btManager.onMessageReceived = { msg -> handleMessage(msg) }
        btManager.startSmartReconnect()
        observeConnection()
    }

    private fun observeConnection() {
        scope.launch {
            btManager.isConnected.collectLatest { connected ->
                val text = if (connected) "گوشی متصل است" else "در حال اتصال..."
                updateNotification(text)
                // Always cancel any in-flight loop before starting fresh
                deviceInfoJob?.cancel()
                if (connected) {
                    vibrateHelper.shortVibrate()
                    showConnectionNotification()
                    deviceInfoJob = scope.launch { sendDeviceInfoLoop() }
                }
            }
        }
    }

    private suspend fun sendDeviceInfoLoop() {
        while (btManager.isConnected.value) {
            try {
                val info = deviceInfoCollector.collect()
                btManager.sendMessage(Message(MessageType.DEVICE_INFO_RESPONSE, gson.toJson(info)))
            } catch (e: Exception) { Timber.w("DeviceInfo send failed: ${e.message}") }
            delay(10_000)
        }
    }

    private fun handleMessage(msg: Message) {
        Timber.d("Wear received: ${msg.type}")
        when (msg.type) {
            MessageType.FIND_DEVICE -> {
                vibrateHelper.continuousVibrate()
                // Play loud sound
                findPhoneHelper.startFinding()
            }
            MessageType.FIND_DEVICE_STOP -> {
                vibrateHelper.stopVibrate()
                findPhoneHelper.stopFinding()
            }
            MessageType.DEVICE_INFO_REQUEST -> {
                scope.launch {
                    val info = deviceInfoCollector.collect()
                    btManager.sendMessage(Message(MessageType.DEVICE_INFO_RESPONSE, gson.toJson(info)))
                }
            }
            MessageType.REMOTE_CONTROL -> handleRemoteControl(msg.payload)
            MessageType.CLIPBOARD_SYNC -> syncClipboard(msg.payload)
            MessageType.NOTIFICATION -> showNotification(msg.payload)
            MessageType.CALL -> showCallNotification(msg.payload)
            MessageType.SMS -> showSmsNotification(msg.payload)
            MessageType.HEARTBEAT -> btManager.sendMessage(Message(MessageType.HEARTBEAT_ACK))
            MessageType.FIND_PHONE -> {
                // The phone is asking the wear to do something — but on this
                // protocol the wear is the *sender* of FIND_PHONE (asking the
                // phone to ring). If we receive FIND_PHONE here it's a loop
                // and we just ignore it.
                Timber.w("Received FIND_PHONE (should not happen on wear)")
            }
            MessageType.FIND_PHONE_STOP -> {
                // No-op; the wear never started ringing in response to a
                // FIND_PHONE_STOP. Keep handler for protocol symmetry.
            }
        }
    }

    private fun handleRemoteControl(payload: String) {
        try {
            val ctrl = gson.fromJson(payload, com.alinam.smartconnect.shared.protocol.RemoteControlPayload::class.java)
            when (ctrl.action) {
                "SET_BRIGHTNESS" -> setBrightness(ctrl.value.toIntOrNull() ?: 128)
                "SET_VOLUME" -> setVolume(ctrl.value.toIntOrNull() ?: 7)
                "VIBRATE" -> vibrateHelper.shortVibrate()
                "REBOOT" -> {
                    // REBOOT requires a privileged permission; ignore silently
                    Timber.w("REBOOT requested but permission unavailable")
                }
                "SHUTDOWN" -> {
                    // SHUTDOWN requires a privileged permission; ignore silently
                    Timber.w("SHUTDOWN requested but permission unavailable")
                }
                "OPEN_SETTINGS" -> openSettings()
                else -> Timber.w("Unknown remote action: ${ctrl.action}")
            }
        } catch (e: Exception) { Timber.e(e, "Remote control failed") }
    }

    private fun setBrightness(value: Int) {
        try {
            android.provider.Settings.System.putInt(
                contentResolver,
                android.provider.Settings.System.SCREEN_BRIGHTNESS,
                value.coerceIn(0, 255)
            )
        } catch (e: Exception) { Timber.w("Cannot set brightness: ${e.message}") }
    }

    private fun setVolume(value: Int) {
        val am = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, value, 0)
    }

    private fun openSettings() {
        val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun syncClipboard(text: String) {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("SmartConnect", text))
        } catch (e: Exception) { }
    }

    private fun showNotification(payload: String) {
        try {
            val n = gson.fromJson(payload, com.alinam.smartconnect.shared.protocol.NotificationPayload::class.java)
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel("notif_sync", "اعلان‌های همگام", NotificationManager.IMPORTANCE_DEFAULT)
            nm.createNotificationChannel(ch)
            val notif = NotificationCompat.Builder(this, "notif_sync")
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("${n.appName}: ${n.title}")
                .setContentText(n.text)
                .setAutoCancel(true)
                .build()
            nm.notify(n.id, notif)
        } catch (e: Exception) { }
    }

    private fun showCallNotification(payload: String) {
        try {
            val call = gson.fromJson(payload, com.alinam.smartconnect.shared.protocol.CallPayload::class.java)
            vibrateHelper.continuousVibrate()
            val nm = getSystemService(NotificationManager::class.java)
            val notif = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setContentTitle("تماس: ${call.name}")
                .setContentText(call.number)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            nm.notify(3001, notif)
        } catch (e: Exception) { }
    }

    private fun showSmsNotification(payload: String) {
        try {
            val sms = gson.fromJson(payload, com.alinam.smartconnect.shared.protocol.SmsPayload::class.java)
            val nm = getSystemService(NotificationManager::class.java)
            val notif = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_send)
                .setContentTitle("پیامک از ${sms.sender}")
                .setContentText(sms.body)
                .setAutoCancel(true)
                .build()
            nm.notify(3002, notif)
        } catch (e: Exception) { }
    }

    private fun showConnectionNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentTitle("SmartConnect")
            .setContentText("گوشی متصل شد.")
            .setAutoCancel(true)
            .build()
        nm.notify(3003, notif)
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SmartConnect:WearService").apply {
            acquire()
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, WearMainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentTitle("SmartConnect Wear")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "SmartConnect Wear Service",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        deviceInfoJob?.cancel()
        // Do NOT stop wakeDetector here - it is owned by WakeToRaiseService
        // and should keep running independently of the bluetooth service.
        btManager.disconnect()
        scope.cancel()
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (e: Exception) { }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
}

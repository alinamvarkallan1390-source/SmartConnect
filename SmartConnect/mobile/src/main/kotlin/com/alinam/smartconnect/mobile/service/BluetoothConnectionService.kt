package com.alinam.smartconnect.mobile.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.alinam.smartconnect.mobile.MainActivity
import com.alinam.smartconnect.mobile.R
import com.alinam.smartconnect.mobile.bluetooth.BluetoothManager
import com.alinam.smartconnect.mobile.data.model.ConnectionState
import com.alinam.smartconnect.mobile.data.repository.ConnectionRepository
import com.alinam.smartconnect.mobile.data.repository.DeviceInfoRepository
import com.alinam.smartconnect.mobile.sync.SyncManager
import com.alinam.smartconnect.mobile.util.BatteryOptimizationHelper
import com.alinam.smartconnect.shared.protocol.Message
import com.alinam.smartconnect.shared.protocol.MessageType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class BluetoothConnectionService : Service() {

    @Inject lateinit var bluetoothManager: BluetoothManager
    @Inject lateinit var connectionRepository: ConnectionRepository
    @Inject lateinit var deviceInfoRepository: DeviceInfoRepository
    @Inject lateinit var syncManager: SyncManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null
    private var toneGenerator: ToneGenerator? = null
    private var lastDistanceWarningMs = 0L
    private var lastConnectedNotificationMs = 0L
    private var lastConnectionSoundMs = 0L
    private var lastConnectedAddress: String? = null

    companion object {
        const val NOTIF_ID = 1001
        const val ACTION_STOP = "com.alinam.smartconnect.STOP"
        const val ACTION_FIND_WATCH = "com.alinam.smartconnect.FIND_WATCH"
        const val ACTION_STOP_FIND = "com.alinam.smartconnect.STOP_FIND"
    }

    private val btStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                BluetoothAdapter.STATE_ON -> {
                    Timber.d("Bluetooth ON - starting scan")
                    bluetoothManager.startSmartScan()
                }
                BluetoothAdapter.STATE_OFF -> {
                    Timber.w("Bluetooth OFF")
                    updateNotification("بلوتوث خاموش شد", false)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
        registerBtReceiver()
        startForegroundWithNotification()
        observeConnectionState()
        observeMessages()
        bluetoothManager.startSmartScan()
        toneGenerator = try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        } catch (e: Exception) { null }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_FIND_WATCH -> sendFindWatch()
            ACTION_STOP_FIND -> stopFindWatch()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        unregisterReceiver(btStateReceiver)
        wakeLock?.release()
        bluetoothManager.disconnect()
        toneGenerator?.release()
        super.onDestroy()
        // Restart service if killed
        val restartIntent = Intent(applicationContext, BluetoothConnectionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartIntent)
        } else {
            startService(restartIntent)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val restartIntent = Intent(applicationContext, BluetoothConnectionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartIntent)
        } else {
            startService(restartIntent)
        }
    }

    // ====================================================
    // NOTIFICATION
    // ====================================================

    private fun startForegroundWithNotification() {
        val notification = buildNotification("در حال جستجوی ساعت...", false)
        startForeground(NOTIF_ID, notification)
    }

    private fun updateNotification(text: String, connected: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text, connected))
    }

    private fun buildNotification(text: String, connected: Boolean): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, BluetoothConnectionService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        val icon = if (connected) android.R.drawable.ic_dialog_info
                   else android.R.drawable.ic_dialog_alert
        return NotificationCompat.Builder(this, getString(R.string.channel_id))
            .setContentTitle("SmartConnect")
            .setContentText(text)
            .setSmallIcon(icon)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_delete, "خروج", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // ====================================================
    // CONNECTION OBSERVER
    // ====================================================

    private fun observeConnectionState() {
        scope.launch {
            bluetoothManager.connectionState.collect { state ->
                when (state) {
                    ConnectionState.CONNECTED -> {
                        val name = bluetoothManager.connectedDevice.value?.name ?: "Watch"
                        val address = bluetoothManager.connectedDevice.value?.address
                        updateNotification("متصل: $name", true)
                        // Throttle: only play sound & notification on a NEW device
                        if (address != lastConnectedAddress) {
                            lastConnectedAddress = address
                            val now = System.currentTimeMillis()
                            if (now - lastConnectionSoundMs > 5_000) {
                                playConnectionSound()
                                lastConnectionSoundMs = now
                            }
                            if (now - lastConnectedNotificationMs > 5_000) {
                                showConnectedNotification(name)
                                lastConnectedNotificationMs = now
                            }
                        }
                        syncManager.startSync()
                        if (!address.isNullOrEmpty()) {
                            scope.launch {
                                connectionRepository.saveLastConnection(address)
                            }
                            // Log connection event
                            scope.launch {
                                connectionRepository.logConnection(
                                    address = address,
                                    name = name,
                                    rssi = bluetoothManager.rssi.value
                                )
                            }
                        }
                    }
                    ConnectionState.DISCONNECTED -> {
                        updateNotification("قطع ارتباط", false)
                        syncManager.stopSync()
                        // Log disconnection
                        val last = bluetoothManager.connectedDevice.value
                        scope.launch {
                            connectionRepository.logDisconnection(
                                address = last?.address ?: lastConnectedAddress ?: "",
                                name = last?.name ?: "",
                                durationSeconds = 0
                            )
                        }
                        lastConnectedAddress = null
                    }
                    ConnectionState.SCANNING -> updateNotification("در حال جستجو...", false)
                    ConnectionState.CONNECTING -> updateNotification("در حال اتصال...", false)
                    else -> {}
                }
            }
        }
    }

    private fun observeMessages() {
        scope.launch {
            bluetoothManager.incomingMessages.collect { message ->
                handleMessage(message)
            }
        }
        // Monitor RSSI for distance warnings (throttled to once per 30s)
        scope.launch {
            bluetoothManager.rssi.collect { rssi ->
                if (rssi < -85 && bluetoothManager.connectionState.value == ConnectionState.CONNECTED) {
                    val now = System.currentTimeMillis()
                    if (now - lastDistanceWarningMs > 30_000) {
                        lastDistanceWarningMs = now
                        showDistanceWarning()
                    }
                }
            }
        }
    }

    private fun handleMessage(message: Message) {
        when (message.type) {
            MessageType.FIND_DEVICE_ACK -> {}
            MessageType.HEARTBEAT_ACK -> {}
            MessageType.DEVICE_INFO, MessageType.DEVICE_INFO_RESPONSE -> {
                scope.launch { deviceInfoRepository.updateWatchInfo(message.payload) }
            }
            MessageType.HANDSHAKE -> {
                // Acknowledge handshake from wear
                bluetoothManager.sendMessage(Message(MessageType.HANDSHAKE_ACK))
            }
            else -> syncManager.processMessage(message)
        }
    }

    // ====================================================
    // FIND WATCH
    // ====================================================

    private fun sendFindWatch() {
        bluetoothManager.sendMessage(Message(MessageType.FIND_DEVICE))
    }

    private fun stopFindWatch() {
        bluetoothManager.sendMessage(Message(MessageType.FIND_DEVICE_STOP))
    }

    // ====================================================
    // HELPERS
    // ====================================================

    private fun playConnectionSound() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
    }

    private fun showConnectedNotification(name: String) {
        val nm = getSystemService(NotificationManager::class.java)
        val notif = NotificationCompat.Builder(this, getString(R.string.channel_id))
            .setContentTitle(getString(R.string.notif_connected))
            .setContentText(name)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        nm.notify(1002, notif)
    }

    private fun showDistanceWarning() {
        val nm = getSystemService(NotificationManager::class.java)
        val notif = NotificationCompat.Builder(this, getString(R.string.channel_id))
            .setContentTitle(getString(R.string.watch_far_away))
            .setContentText("سیگنال بلوتوث ضعیف شده است")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        nm.notify(1003, notif)
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SmartConnect::ConnectionLock"
        ).also { it.acquire(60 * 60 * 1000L /* 1 hour, refreshed periodically */) }
        // Periodic refresh to prevent expiration
        scope.launch {
            while (wakeLock?.isHeld == true) {
                kotlinx.coroutines.delay(15 * 60 * 1000L) // every 15 minutes
                if (wakeLock?.isHeld == true) {
                    wakeLock?.release()
                    wakeLock?.acquire(60 * 60 * 1000L)
                }
            }
        }
    }

    private fun registerBtReceiver() {
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(btStateReceiver, filter)
    }
}

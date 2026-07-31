package com.alinam.smartconnect.mobile.service

import android.app.Notification
import android.content.pm.PackageManager
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.alinam.smartconnect.mobile.bluetooth.BluetoothManager
import com.alinam.smartconnect.mobile.data.model.ConnectionState
import com.alinam.smartconnect.shared.protocol.Message
import com.alinam.smartconnect.shared.protocol.MessageType
import com.alinam.smartconnect.shared.protocol.NotificationPayload
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class SmartNotificationListenerService : NotificationListenerService() {

    @Inject lateinit var bluetoothManager: BluetoothManager
    private val gson = Gson()

    // Apps whose notifications we don't forward (noise reduction)
    private val blockedPackages = setOf(
        packageName, // Our own app
        "android",
        "com.android.systemui"
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (bluetoothManager.connectionState.value != ConnectionState.CONNECTED) return
        if (sbn.packageName in blockedPackages) return
        if (sbn.isOngoing) return

        try {
            val extras: Bundle = sbn.notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val appName = getAppName(sbn.packageName)

            if (title.isBlank() && text.isBlank()) return

            val payload = NotificationPayload(
                packageName = sbn.packageName,
                appName = appName,
                title = title,
                text = text,
                timestamp = sbn.postTime
            )
            bluetoothManager.sendMessage(
                Message(
                    type = MessageType.NOTIFICATION,
                    payload = gson.toJson(payload)
                )
            )
            Timber.d("Forwarded notification from $appName")
        } catch (e: Exception) {
            Timber.e(e, "Failed to forward notification")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {}

    private fun getAppName(packageName: String): String {
        return try {
            val pm = applicationContext.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }
}

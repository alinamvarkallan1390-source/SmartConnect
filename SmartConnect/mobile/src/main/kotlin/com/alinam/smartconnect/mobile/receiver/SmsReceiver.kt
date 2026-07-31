package com.alinam.smartconnect.mobile.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.alinam.smartconnect.mobile.bluetooth.BluetoothManager
import com.alinam.smartconnect.shared.protocol.Message
import com.alinam.smartconnect.shared.protocol.MessageType
import com.alinam.smartconnect.shared.protocol.SmsPayload
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject lateinit var bluetoothManager: BluetoothManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            messages.forEach { sms ->
                val payload = SmsPayload(
                    sender = sms.originatingAddress ?: "",
                    body = sms.messageBody ?: "",
                    timestamp = sms.timestampMillis
                )
                bluetoothManager.sendMessage(
                    Message(MessageType.SMS_EVENT, Gson().toJson(payload))
                )
                Timber.d("SMS from ${sms.originatingAddress}")
            }
        } catch (e: Exception) {
            Timber.e(e, "SMS receive error")
        }
    }
}

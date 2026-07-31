package com.alinam.smartconnect.mobile.receiver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Build
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.alinam.smartconnect.mobile.bluetooth.BluetoothManager
import com.alinam.smartconnect.shared.protocol.CallEventPayload
import com.alinam.smartconnect.shared.protocol.Message
import com.alinam.smartconnect.shared.protocol.MessageType
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class CallReceiver : BroadcastReceiver() {

    @Inject lateinit var bluetoothManager: BluetoothManager

    override fun onReceive(context: Context, intent: Intent) {
        // On API 31+ TelephonyManager.ACTION_PHONE_STATE_CHANGED still fires,
        // but the EXTRA_INCOMING_NUMBER is only delivered to apps holding the
        // READ_PHONE_STATE permission and a default dialer / system role.
        // We attempt a best-effort lookup; missing data is acceptable.
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""

        val callState = when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> "RINGING"
            TelephonyManager.EXTRA_STATE_OFFHOOK -> "ACTIVE"
            TelephonyManager.EXTRA_STATE_IDLE -> "ENDED"
            else -> return
        }

        // Only attach the contact name if the user has granted READ_CONTACTS
        val contactName = if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            getContactName(context, number)
        } else {
            ""
        }

        val payload = CallEventPayload(
            state = callState,
            number = number,
            contactName = contactName
        )
        try {
            bluetoothManager.sendMessage(
                Message(MessageType.CALL_EVENT, Gson().toJson(payload))
            )
            Timber.d("Call event: $callState from $number")
        } catch (e: Exception) {
            Timber.w(e, "sendMessage failed in CallReceiver")
        }
    }

    private fun getContactName(context: Context, number: String): String {
        if (number.isBlank()) return ""
        return try {
            val uri = android.net.Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(number)
            )
            val cursor: Cursor? = context.contentResolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
            )
            cursor?.use {
                if (it.moveToFirst()) it.getString(0) else number
            } ?: number
        } catch (e: Exception) { number }
    }
}

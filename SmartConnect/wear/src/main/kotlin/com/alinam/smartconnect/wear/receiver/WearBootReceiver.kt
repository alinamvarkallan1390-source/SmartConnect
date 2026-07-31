package com.alinam.smartconnect.wear.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alinam.smartconnect.wear.service.WakeToRaiseService
import com.alinam.smartconnect.wear.service.WearBluetoothService

class WearBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // Start both services so wake-to-raise works without phone.
            WakeToRaiseService.start(context)
            WearBluetoothService.start(context)
        }
    }
}

package com.alinam.smartconnect.wear

import android.app.Application
import com.alinam.smartconnect.wear.service.WakeToRaiseService
import com.alinam.smartconnect.wear.service.WearBluetoothService
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class WearApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        // Start both services at boot so wake-to-raise works even when the
        // phone is not connected.
        WakeToRaiseService.start(this)
        WearBluetoothService.start(this)
    }
}

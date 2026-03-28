package com.moqaddas.handsoff

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import rikka.shizuku.Shizuku

@HiltAndroidApp
class HandsOffApp : Application() {

    // Held as a field so it can be removed cleanly — prevents listener accumulation
    // across any edge cases where onCreate is called more than once.
    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        // ViewModels re-check isShizukuAvailable() on each health tick,
        // so the UI will reflect the new grant/deny state automatically.
    }

    override fun onCreate() {
        super.onCreate()
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
    }

    override fun onTerminate() {
        super.onTerminate()
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
    }
}

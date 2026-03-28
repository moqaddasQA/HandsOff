package com.moqaddas.handsoff.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import com.moqaddas.handsoff.service.GuardianVpnService
import com.moqaddas.handsoff.service.SensorGuardService

/**
 * Fires on device boot and after OTA updates.
 * Jobs:
 *  1. Store/compare Build.FINGERPRINT to detect OTA.
 *  2. Auto-restart GuardianVpnService if it was previously running.
 *  3. Auto-restart SensorGuardService (always — it handles its own permission check).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.LOCKED_BOOT_COMPLETED") return

        val prefs = context.getSharedPreferences("handsoff_prefs", Context.MODE_PRIVATE)
        val storedFingerprint  = prefs.getString("build_fingerprint", null)
        val currentFingerprint = Build.FINGERPRINT

        val otaDetected = storedFingerprint != null && storedFingerprint != currentFingerprint
        prefs.edit()
            .putString("build_fingerprint", currentFingerprint)
            .putBoolean("ota_detected", otaDetected)
            .apply()

        // Restart VPN guardian only if VPN permission is already granted (no dialog can show on boot)
        val vpnPrepareIntent = VpnService.prepare(context)
        if (vpnPrepareIntent == null) {
            // null = already granted — safe to start
            context.startService(
                Intent(context, GuardianVpnService::class.java).apply {
                    action = GuardianVpnService.ACTION_START
                }
            )
        }

        // Always restart sensor monitoring — it handles missing permissions gracefully
        context.startService(
            Intent(context, SensorGuardService::class.java).apply {
                action = SensorGuardService.ACTION_START
            }
        )
    }
}

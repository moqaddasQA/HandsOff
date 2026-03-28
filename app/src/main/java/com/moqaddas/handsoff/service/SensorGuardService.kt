package com.moqaddas.handsoff.service

import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.ServiceCompat
import com.moqaddas.handsoff.data.db.AppDatabase
import com.moqaddas.handsoff.data.db.ThreatEventEntity
import com.moqaddas.handsoff.domain.model.ThreatType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * Monitors microphone and camera access across all installed apps.
 *
 * Uses AppOpsManager.startWatchingActive (public API, added in API 28).
 * Requires android.permission.GET_APP_OPS_STATS for cross-app monitoring.
 * Grant once via ADB or Shizuku:
 *   adb shell pm grant <packageId> android.permission.GET_APP_OPS_STATS
 *
 * If permission is absent, SensorGuardState signals the dashboard to show a setup card.
 */
@AndroidEntryPoint
class SensorGuardService : Service() {

    companion object {
        const val ACTION_START = "com.moqaddas.handsoff.ACTION_START_SENSOR_GUARD"
        const val ACTION_STOP  = "com.moqaddas.handsoff.ACTION_STOP_SENSOR_GUARD"
    }

    @Inject lateinit var db: AppDatabase

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Listener reference kept so we can unregister in onDestroy
    private var opListener: AppOpsManager.OnOpActiveChangedListener? = null

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP -> { stopSelf(); START_NOT_STICKY }
            else -> { startGuard(); START_STICKY }
        }
    }

    private fun startGuard() {
        GuardianNotification.createChannel(this)
        ServiceCompat.startForeground(
            this,
            GuardianNotification.NOTIF_ID + 10,
            GuardianNotification.build(this, 0),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            registerOpWatcher()
        } else {
            SensorGuardState.setPermissionGranted(false)
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun registerOpWatcher() {
        val appOps = getSystemService(AppOpsManager::class.java)
        val ops    = arrayOf(AppOpsManager.OPSTR_RECORD_AUDIO, AppOpsManager.OPSTR_CAMERA)

        val listener = AppOpsManager.OnOpActiveChangedListener { op, _, packageName, active ->
            if (!active) return@OnOpActiveChangedListener
            scope.launch { onSensorActive(packageName, op) }
        }

        try {
            appOps.startWatchingActive(ops, mainExecutor, listener)
            opListener = listener
            SensorGuardState.setPermissionGranted(true)
        } catch (e: SecurityException) {
            // GET_APP_OPS_STATS not granted — show setup card in dashboard
            SensorGuardState.setPermissionGranted(false)
        }
    }

    private suspend fun onSensorActive(packageName: String, opStr: String) {
        val pm = packageManager
        val appName = runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)).toString()
        }.getOrDefault(packageName)

        val type = when (opStr) {
            AppOpsManager.OPSTR_RECORD_AUDIO -> ThreatType.MIC_ACCESS
            AppOpsManager.OPSTR_CAMERA       -> ThreatType.CAMERA_ACCESS
            else                             -> return
        }
        val sensorLabel = if (type == ThreatType.MIC_ACCESS) "Microphone" else "Camera"

        db.threatEventDao().insert(
            ThreatEventEntity(
                packageName = packageName,
                appName     = appName,
                threatType  = type.name,
                description = "$appName accessed the ${sensorLabel.lowercase()}",
                detectedAt  = System.currentTimeMillis()
            )
        )

        withContext(Dispatchers.Main) {
            getSystemService(NotificationManager::class.java).notify(
                (packageName.hashCode() and 0x0FFF) + 100,
                GuardianNotification.buildSensorAlert(this@SensorGuardService, appName, sensorLabel)
            )
        }
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            opListener?.let { getSystemService(AppOpsManager::class.java).stopWatchingActive(it) }
        }
        scope.cancel()
        SensorGuardState.clear()
        super.onDestroy()
    }
}

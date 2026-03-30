package com.moqaddas.handsoff.service

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.app.ServiceCompat
import com.moqaddas.handsoff.data.db.AppDatabase
import com.moqaddas.handsoff.data.db.ThreatEventEntity
import com.moqaddas.handsoff.data.shizuku.ShizukuCommandRunner
import com.moqaddas.handsoff.domain.model.ThreatType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * Monitors microphone and camera access across all installed apps.
 *
 * Primary:  AppOpsManager.startWatchingActive (API 28) — works on stock Android.
 * Fallback: CameraManager.AvailabilityCallback + AudioManager.AudioRecordingCallback —
 *           hardware-level callbacks that bypass AppOps delivery restrictions on
 *           Samsung One UI 4.1 and similar OEM builds.
 *
 * Both paths share a dedup window so a single real access produces one event even if
 * both the AppOps watcher and the hardware callback fire simultaneously.
 *
 * Requires android.permission.GET_APP_OPS_STATS for AppOps cross-app monitoring.
 * Grant via ADB or Shizuku:
 *   adb shell pm grant <packageId> android.permission.GET_APP_OPS_STATS
 */
@AndroidEntryPoint
class SensorGuardService : Service() {

    companion object {
        const val ACTION_START = "com.moqaddas.handsoff.ACTION_START_SENSOR_GUARD"
        const val ACTION_STOP  = "com.moqaddas.handsoff.ACTION_STOP_SENSOR_GUARD"
        private const val DEDUP_MS = 3_000L
    }

    @Inject lateinit var db: AppDatabase
    @Inject lateinit var shizuku: ShizukuCommandRunner

    private val scope      = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    // ── Primary AppOps watcher ───────────────────────────────────────────────
    private var opActiveListener: AppOpsManager.OnOpActiveChangedListener? = null

    // ── Hardware fallback handles ────────────────────────────────────────────
    private var cameraCallback: CameraManager.AvailabilityCallback? = null
    private var audioCallback:  AudioManager.AudioRecordingCallback? = null

    // Dedup: "packageName:opStr" → last-fired epoch ms.
    // Prevents a single real access from producing multiple DB rows when both
    // the AppOps path and the hardware path fire for the same event.
    // Accessed only from main-thread callbacks — no synchronization needed.
    private val lastEventMs    = mutableMapOf<String, Long>()
    private val activeCameraIds = mutableSetOf<String>()   // main-thread only

    // ── Service lifecycle ────────────────────────────────────────────────────

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP -> { stopSelf(); START_NOT_STICKY }
            else        -> { startGuard(); START_STICKY }
        }
    }

    private fun startGuard() {
        GuardianNotification.createChannel(this)

        // Android 11+ requires mic|camera foreground type for AppOps callbacks to route correctly
        val fgType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        ServiceCompat.startForeground(
            this, GuardianNotification.NOTIF_ID + 10,
            GuardianNotification.build(this, 0), fgType
        )

        // Primary path — AppOps watcher (requires GET_APP_OPS_STATS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            registerOpWatcher()
        } else {
            SensorGuardState.setPermissionGranted(false)
        }

        // Fallback path — hardware-level callbacks
        // These fire regardless of OEM AppOps delivery restrictions
        registerCameraFallback()
        registerMicFallback()
    }

    // ── AppOps primary ───────────────────────────────────────────────────────

    @RequiresApi(Build.VERSION_CODES.P)
    private fun registerOpWatcher() {
        val appOps = getSystemService(AppOpsManager::class.java)
        val ops    = arrayOf(AppOpsManager.OPSTR_RECORD_AUDIO, AppOpsManager.OPSTR_CAMERA)

        val listener = AppOpsManager.OnOpActiveChangedListener { op, _, packageName, active ->
            if (!active) return@OnOpActiveChangedListener
            maybeFire(packageName, op)
        }
        try {
            appOps.startWatchingActive(ops, mainExecutor, listener)
            opActiveListener = listener
            SensorGuardState.setPermissionGranted(true)
        } catch (e: SecurityException) {
            SensorGuardState.setPermissionGranted(false)
        }
    }

    // ── Camera hardware fallback ─────────────────────────────────────────────

    private fun registerCameraFallback() {
        val cm = getSystemService(CameraManager::class.java)
        val cb = object : CameraManager.AvailabilityCallback() {
            override fun onCameraUnavailable(cameraId: String) {
                // Camera just opened by some app
                if (activeCameraIds.add(cameraId)) {
                    maybeFire(findForegroundPackage(), AppOpsManager.OPSTR_CAMERA)
                }
            }
            override fun onCameraAvailable(cameraId: String) {
                activeCameraIds.remove(cameraId)
            }
        }
        cm.registerAvailabilityCallback(cb, mainHandler)
        cameraCallback = cb
    }

    // ── Microphone hardware fallback ─────────────────────────────────────────

    private fun registerMicFallback() {
        val am = getSystemService(AudioManager::class.java)
        val cb = object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
                if (configs.isEmpty()) return
                maybeFire(findForegroundPackage(), AppOpsManager.OPSTR_RECORD_AUDIO)
            }
        }
        am.registerAudioRecordingCallback(cb, mainHandler)
        audioCallback = cb
    }

    // ── Shared helpers ───────────────────────────────────────────────────────

    /**
     * Fires a sensor event only if the same package+op hasn't fired within DEDUP_MS.
     * Called from main-thread callbacks only.
     */
    private fun maybeFire(packageName: String, opStr: String) {
        val key = "$packageName:$opStr"
        val now = System.currentTimeMillis()
        if (now - (lastEventMs[key] ?: 0L) < DEDUP_MS) return
        lastEventMs[key] = now
        scope.launch { onSensorActive(packageName, opStr) }
    }

    /**
     * Returns the package that most recently moved to foreground (last 5 s).
     * Uses UsageStatsManager.queryEvents() — requires PACKAGE_USAGE_STATS.
     * Falls back to "unknown" if the permission is not yet granted.
     *
     * Note: ActivityManager.getRunningAppProcesses() only returns the caller's
     * own process on Android 8+, so UsageEvents is the correct public API here.
     */
    private fun findForegroundPackage(): String {
        return try {
            val usm = getSystemService(UsageStatsManager::class.java)
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - 5_000L, now)
            val event = UsageEvents.Event()
            var lastFgPkg: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND &&
                    !isSystemOrSelf(event.packageName)) {
                    lastFgPkg = event.packageName
                }
            }
            lastFgPkg ?: "unknown"
        } catch (_: SecurityException) {
            "unknown"  // PACKAGE_USAGE_STATS not yet granted — user hasn't enabled Usage Access
        }
    }

    private fun isSystemOrSelf(pkg: String) =
        pkg == packageName ||
        pkg.startsWith("android") ||
        pkg == "com.android.systemui" ||
        pkg == "com.samsung.android.systemui"

    private suspend fun onSensorActive(packageName: String, opStr: String) {
        val pm      = packageManager
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

        if (SensorGuardState.blockingEnabled.value && shizuku.isAvailable()) {
            if (type == ThreatType.MIC_ACCESS)    shizuku.denyMicAccess(packageName)
            if (type == ThreatType.CAMERA_ACCESS) shizuku.denyCameraAccess(packageName)
        }
    }

    // ── Cleanup ──────────────────────────────────────────────────────────────

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            opActiveListener?.let { getSystemService(AppOpsManager::class.java).stopWatchingActive(it) }
        }
        cameraCallback?.let { getSystemService(CameraManager::class.java).unregisterAvailabilityCallback(it) }
        audioCallback?.let  { getSystemService(AudioManager::class.java).unregisterAudioRecordingCallback(it) }
        scope.cancel()
        SensorGuardState.clear()
        super.onDestroy()
    }
}

package com.moqaddas.handsoff.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.moqaddas.handsoff.data.db.AppDatabase
import com.moqaddas.handsoff.data.db.PermissionSnapshotEntity
import com.moqaddas.handsoff.data.db.ThreatEventEntity
import com.moqaddas.handsoff.domain.model.ThreatType
import com.moqaddas.handsoff.service.GuardianNotification
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fires on every app install/update.
 *
 * On ACTION_PACKAGE_REPLACED: compares the current granted permissions against
 * the stored snapshot. Any new permission not in the old snapshot triggers a
 * PERMISSION_RISK ThreatEventEntity + high-priority alert notification.
 *
 * On ACTION_PACKAGE_ADDED (first install): stores the initial snapshot with no alert.
 *
 * On ACTION_PACKAGE_FULLY_REMOVED: removes the stored snapshot.
 */
@AndroidEntryPoint
class PackageChangedReceiver : BroadcastReceiver() {

    @Inject lateinit var db: AppDatabase

    // BroadcastReceiver.onReceive must return quickly — use a detached scope.
    // SupervisorJob prevents a crash in one coroutine from cancelling others.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.data?.schemeSpecificPart ?: return

        when (intent.action) {
            Intent.ACTION_PACKAGE_REPLACED -> scope.launch {
                handleUpdate(context, packageName)
            }
            Intent.ACTION_PACKAGE_ADDED -> scope.launch {
                seedSnapshot(context, packageName)
            }
            Intent.ACTION_PACKAGE_FULLY_REMOVED -> scope.launch {
                db.permissionSnapshotDao().delete(packageName)
            }
        }
    }

    // ── Update path ───────────────────────────────────────────────────────────

    private suspend fun handleUpdate(context: Context, packageName: String) {
        val dao          = db.permissionSnapshotDao()
        val old          = dao.getForPackage(packageName)
        val currentPerms = getGrantedPermissions(context, packageName)

        if (old != null) {
            val oldPerms = old.permissions
                .split(",")
                .filter { it.isNotBlank() }
                .toSet()

            val newPerms = currentPerms - oldPerms

            if (newPerms.isNotEmpty()) {
                val appName     = getAppName(context, packageName)
                val permSummary = newPerms.joinToString(", ") { it.substringAfterLast('.') }

                db.threatEventDao().insert(
                    ThreatEventEntity(
                        packageName = packageName,
                        appName     = appName,
                        threatType  = ThreatType.PERMISSION_RISK.name,
                        description = "$appName gained: $permSummary"
                    )
                )

                GuardianNotification.createChannel(context)
                val nm = context.getSystemService(android.app.NotificationManager::class.java)
                nm.notify(
                    (packageName.hashCode() and 0x0FFF) + 200,
                    GuardianNotification.buildPermissionAlert(context, appName, newPerms)
                )
            }
        }

        // Always refresh the snapshot after an update
        dao.upsert(buildSnapshot(context, packageName, currentPerms))
    }

    // ── Seed path (first install) ─────────────────────────────────────────────

    private suspend fun seedSnapshot(context: Context, packageName: String) {
        val currentPerms = getGrantedPermissions(context, packageName)
        db.permissionSnapshotDao().upsert(buildSnapshot(context, packageName, currentPerms))
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun getGrantedPermissions(context: Context, packageName: String): Set<String> {
        return try {
            val pm   = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(
                    PackageManager.GET_PERMISSIONS.toLong()
                ))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            }
            val perms   = info.requestedPermissions ?: return emptySet()
            val results = info.requestedPermissionsFlags ?: return emptySet()
            perms.indices
                .filter { results[it] and PackageManager.GET_PERMISSIONS != 0 }
                .map { perms[it] }
                .toSet()
        } catch (_: PackageManager.NameNotFoundException) {
            emptySet()
        }
    }

    private fun buildSnapshot(
        context: Context,
        packageName: String,
        perms: Set<String>
    ): PermissionSnapshotEntity {
        val versionCode = try {
            val pm   = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        } catch (_: PackageManager.NameNotFoundException) { 0L }

        return PermissionSnapshotEntity(
            packageName  = packageName,
            permissions  = perms.sorted().joinToString(","),
            versionCode  = versionCode
        )
    }

    private fun getAppName(context: Context, packageName: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)).toString()
    } catch (_: PackageManager.NameNotFoundException) { packageName }
}

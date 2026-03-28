package com.moqaddas.handsoff.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores the last-known permission set for an installed package.
 *
 * On every ACTION_PACKAGE_REPLACED broadcast, PackageChangedReceiver compares
 * the current granted permissions against this snapshot. Any new permission
 * not present in [permissions] fires a PERMISSION_RISK alert.
 *
 * [permissions] is a sorted, comma-separated list of granted permission names.
 * Using a flat string keeps the schema simple and avoids a join table.
 */
@Entity(tableName = "permission_snapshots")
data class PermissionSnapshotEntity(
    @PrimaryKey val packageName: String,
    val permissions: String,          // sorted comma-separated granted permissions
    val versionCode: Long,
    val snapshotTime: Long = System.currentTimeMillis()
)

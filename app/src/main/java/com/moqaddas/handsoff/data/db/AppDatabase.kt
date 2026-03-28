package com.moqaddas.handsoff.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ThreatEventEntity::class, PermissionSnapshotEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun threatEventDao(): ThreatEventDao
    abstract fun permissionSnapshotDao(): PermissionSnapshotDao
}

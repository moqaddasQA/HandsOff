package com.moqaddas.handsoff.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PermissionSnapshotDao {

    /** Insert or replace the snapshot for a package (upsert). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: PermissionSnapshotEntity)

    /** Get the stored snapshot for a specific package, or null if never seen before. */
    @Query("SELECT * FROM permission_snapshots WHERE packageName = :packageName LIMIT 1")
    suspend fun getForPackage(packageName: String): PermissionSnapshotEntity?

    /** Get all stored snapshots — used to seed new installs on first launch. */
    @Query("SELECT * FROM permission_snapshots")
    suspend fun getAll(): List<PermissionSnapshotEntity>

    /** Remove the snapshot when a package is uninstalled. */
    @Query("DELETE FROM permission_snapshots WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}

package com.moqaddas.handsoff.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ThreatEventDao {

    @Query("SELECT * FROM threat_events ORDER BY detectedAt DESC LIMIT 50")
    fun getRecentEvents(): Flow<List<ThreatEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: ThreatEventEntity): Long

    @Query("DELETE FROM threat_events WHERE detectedAt < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)

    @Query("SELECT COUNT(*) FROM threat_events WHERE threatType = 'DNS_BLOCKED'")
    fun getDnsBlockedCount(): Flow<Int>
}

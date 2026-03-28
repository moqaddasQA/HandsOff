package com.moqaddas.handsoff.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "threat_events")
data class ThreatEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val threatType: String,     // ThreatType.name — stored as string for Room simplicity
    val description: String,
    val detectedAt: Long = System.currentTimeMillis(),
    val resolved: Boolean = false
)

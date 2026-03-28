package com.moqaddas.handsoff.domain.model

import java.time.Instant

data class ThreatEvent(
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val threatType: ThreatType,
    val description: String,
    val detectedAt: Instant = Instant.now(),
    val resolved: Boolean = false
)

enum class ThreatType {
    BLOATWARE,
    PERMISSION_RISK,
    TRACKING,
    WAKELOCK,
    BACKGROUND_PROCESS,
    DATA_COLLECTION,
    OTA_RESTORE_NEEDED,
    DNS_BLOCKED,
    MIC_ACCESS,
    CAMERA_ACCESS
}

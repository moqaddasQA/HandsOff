package com.moqaddas.handsoff.domain.repository

import com.moqaddas.handsoff.domain.model.AppThreat
import com.moqaddas.handsoff.domain.model.PrivacyScore
import com.moqaddas.handsoff.domain.model.ThreatEvent
import kotlinx.coroutines.flow.Flow

interface PrivacyRepository {
    /** Scans installed packages against the packages.json database. Returns only installed threats. */
    suspend fun scanForThreats(): List<AppThreat>

    /** Permanently removes a package for the current user (OTA-resistant). */
    suspend fun uninstallPackage(packageName: String): Boolean

    /** Revokes a runtime permission from a package. */
    suspend fun revokePermission(packageName: String, permission: String): Boolean

    /** Applies a hardening setting from settings.json by its id field. */
    suspend fun applyHardeningSetting(id: String): Boolean

    /** Reinstalls a previously uninstalled package (restore path). */
    suspend fun reinstallPackage(packageName: String): Boolean

    /** Computes the current privacy score from installed threats. */
    suspend fun getPrivacyScore(): PrivacyScore

    /** Live stream of threat events from the Room database. */
    fun getThreatEvents(): Flow<List<ThreatEvent>>
}

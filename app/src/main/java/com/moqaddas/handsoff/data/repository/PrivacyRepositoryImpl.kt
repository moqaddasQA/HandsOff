package com.moqaddas.handsoff.data.repository

import android.content.Context
import android.content.pm.PackageManager
import com.moqaddas.handsoff.data.model.PackageEntry
import com.moqaddas.handsoff.data.model.PackagesDatabase
import com.moqaddas.handsoff.data.model.SettingsDatabase
import com.moqaddas.handsoff.data.shizuku.ShizukuCommandRunner
import com.moqaddas.handsoff.domain.model.AppThreat
import com.moqaddas.handsoff.domain.model.PrivacyScore
import com.moqaddas.handsoff.domain.model.ThreatEvent
import com.moqaddas.handsoff.domain.repository.PrivacyRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrivacyRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val commandRunner: ShizukuCommandRunner
) : PrivacyRepository {

    private val json = Json { ignoreUnknownKeys = true }

    private fun loadPackagesDb(): PackagesDatabase {
        val content = context.assets.open("packages.json").bufferedReader().readText()
        return json.decodeFromString(content)
    }

    private fun loadSettingsDb(): SettingsDatabase {
        val content = context.assets.open("settings.json").bufferedReader().readText()
        return json.decodeFromString(content)
    }

    private fun isInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    override suspend fun scanForThreats(): List<AppThreat> = withContext(Dispatchers.IO) {
        val db = loadPackagesDb()
        db.packages
            .filter { isInstalled(it.packageName) }
            .map { it.toAppThreat(isInstalled = true) }
            .sortedByDescending { it.threatLevel }
    }

    override suspend fun uninstallPackage(packageName: String): Boolean {
        return commandRunner.uninstallPackage(packageName).success
    }

    override suspend fun reinstallPackage(packageName: String): Boolean {
        return commandRunner.reinstallPackage(packageName).success
    }

    override suspend fun revokePermission(packageName: String, permission: String): Boolean {
        return commandRunner.revokePermission(packageName, permission).success
    }

    override suspend fun applyHardeningSetting(id: String): Boolean {
        val setting = loadSettingsDb().settings.find { it.id == id } ?: return false
        return when (setting.commandType) {
            "bmgr" -> commandRunner.execute("bmgr", "enable", "false").success
            "settings" -> commandRunner.setSetting(
                setting.namespace!!,
                setting.key!!,
                setting.value!!
            ).success
            "appops" -> commandRunner.denyAppOp(
                setting.target ?: return false,
                setting.op ?: return false
            ).success
            else -> false
        }
    }

    override suspend fun getPrivacyScore(): PrivacyScore {
        val threats = scanForThreats()
        return PrivacyScore.fromThreats(threats)
    }

    // Phase 3: Room database Flow — returns empty until database layer is added
    override fun getThreatEvents(): Flow<List<ThreatEvent>> = flow { emit(emptyList()) }

    private fun PackageEntry.toAppThreat(isInstalled: Boolean) = AppThreat(
        packageName = packageName,
        appName = name,
        threatLevel = threatLevel,
        categories = categories,
        description = description,
        safeToDisable = safeToDisable,
        affectsCoreFunction = affectsCoreFunction,
        isInstalled = isInstalled,
        brand = brand
    )
}

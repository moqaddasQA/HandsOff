package com.moqaddas.handsoff.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SettingsDatabase(
    val version: Int,
    val settings: List<SettingEntry>
)

@Serializable
data class SettingEntry(
    val id: String,
    val name: String,
    val description: String,
    @SerialName("command_type") val commandType: String,  // "bmgr" | "settings" | "appops"
    val namespace: String? = null,   // "global" | "secure" | "system"
    val key: String? = null,
    val value: String? = null,
    val op: String? = null,          // for command_type="appops"
    val target: String? = null,      // package name for appops target, null = system-wide
    val impact: String,              // "low" | "medium" | "high"
    val reversible: Boolean
)

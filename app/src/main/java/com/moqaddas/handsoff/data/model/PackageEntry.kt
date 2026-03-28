package com.moqaddas.handsoff.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PackagesDatabase(
    val version: Int,
    val updated: String,
    val packages: List<PackageEntry>
)

@Serializable
data class PackageEntry(
    // 'package' is a reserved keyword in Kotlin — @SerialName maps JSON key to Kotlin field name
    @SerialName("package") val packageName: String,
    val name: String,
    val description: String,
    val brand: String,
    @SerialName("threat_level") val threatLevel: Int,
    @SerialName("safe_to_disable") val safeToDisable: Boolean,
    @SerialName("affects_core_function") val affectsCoreFunction: Boolean,
    val categories: List<String>
)

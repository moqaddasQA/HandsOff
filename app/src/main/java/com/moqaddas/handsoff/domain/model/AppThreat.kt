package com.moqaddas.handsoff.domain.model

data class AppThreat(
    val packageName: String,
    val appName: String,
    val threatLevel: Int,             // 1 (low) to 5 (critical)
    val categories: List<String>,
    val description: String,
    val safeToDisable: Boolean,
    val affectsCoreFunction: Boolean, // true = touches calls/SMS/system — extra caution
    val isInstalled: Boolean = false,
    val brand: String                 // "samsung" | "google" | "meta" | "xiaomi" | "oneplus" | "microsoft" | "amazon"
)

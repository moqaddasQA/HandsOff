package com.moqaddas.handsoff.domain.model

data class PrivacyScore(
    val score: Int,          // 0–100, higher = better privacy
    val grade: String,       // "A", "B", "C", "D", "F"
    val activeThreats: Int,
    val resolvedThreats: Int,
    val riskyPermissions: Int
) {
    companion object {
        fun fromThreats(threats: List<AppThreat>, resolvedCount: Int = 0): PrivacyScore {
            val active = threats.filter { it.isInstalled }
            // Each threat deducts points proportional to threat level (max 80 pts deducted)
            val penalty = active.sumOf { it.threatLevel * 4 }.coerceAtMost(80)
            val score = (100 - penalty).coerceIn(0, 100)
            val grade = when {
                score >= 90 -> "A"
                score >= 80 -> "B"
                score >= 70 -> "C"
                score >= 60 -> "D"
                else -> "F"
            }
            return PrivacyScore(
                score = score,
                grade = grade,
                activeThreats = active.size,
                resolvedThreats = resolvedCount,
                riskyPermissions = 0   // populated by PermissionAuditor in Phase 3
            )
        }
    }
}

package com.moqaddas.handsoff.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class VpnState {
    object Stopped  : VpnState()
    object Starting : VpnState()
    object Running  : VpnState()
    data class Error(val message: String) : VpnState()
}

/**
 * Process-wide state bus for VPN status and blocked-count.
 * Written by GuardianVpnService, read by ViewModels and notifications.
 * Using a plain object (not Hilt singleton) so the service can write to it
 * without needing injection — the service lifecycle is managed by Android, not Hilt.
 */
object VpnStateManager {
    private val _state        = MutableStateFlow<VpnState>(VpnState.Stopped)
    private val _blockedCount = MutableStateFlow(0)

    val state:        StateFlow<VpnState> = _state.asStateFlow()
    val blockedCount: StateFlow<Int>      = _blockedCount.asStateFlow()

    fun setState(new: VpnState) { _state.value = new }

    fun incrementBlocked() { _blockedCount.value++ }

    /** Called when VPN is stopped so the dashboard resets correctly on next start. */
    fun resetCount() { _blockedCount.value = 0 }
}

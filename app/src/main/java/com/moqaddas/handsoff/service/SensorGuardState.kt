package com.moqaddas.handsoff.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide state bus for SensorGuardService — mirrors VpnStateManager pattern.
 * Written by SensorGuardService, read by DashboardViewModel.
 */
object SensorGuardState {

    private val _permissionGranted = MutableStateFlow<Boolean?>(null) // null = unknown
    private val _activeAccesses    = MutableStateFlow<Set<String>>(emptySet())

    val permissionGranted: StateFlow<Boolean?> = _permissionGranted.asStateFlow()
    val activeAccesses:    StateFlow<Set<String>> = _activeAccesses.asStateFlow()

    fun setPermissionGranted(granted: Boolean) { _permissionGranted.value = granted }
    fun setActiveAccesses(accesses: Set<String>) { _activeAccesses.value = accesses }
    fun setUnsupported() { _permissionGranted.value = false }
    fun clear() { _activeAccesses.value = emptySet() }
}

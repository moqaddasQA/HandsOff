package com.moqaddas.handsoff.presentation

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import com.moqaddas.handsoff.data.db.ThreatEventDao
import com.moqaddas.handsoff.data.db.ThreatEventEntity
import com.moqaddas.handsoff.service.SensorGuardService
import com.moqaddas.handsoff.service.SensorGuardState
import com.moqaddas.handsoff.service.VpnController
import com.moqaddas.handsoff.service.VpnState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val controller: VpnController,
    private val dao: ThreatEventDao
) : ViewModel() {

    val vpnState: StateFlow<VpnState> = controller.state
    val blockedCount: StateFlow<Int>  = controller.blockedCount

    /** Live stream of the 50 most recent threat events for the dashboard timeline. */
    val recentEvents: Flow<List<ThreatEventEntity>> = dao.getRecentEvents()

    /** Whether sensor monitoring has been granted GET_APP_OPS_STATS permission. */
    val sensorPermissionGranted: StateFlow<Boolean?> = SensorGuardState.permissionGranted

    fun prepareIntent(context: Context): Intent? = controller.prepareIntent(context)
    fun startVpn(context: Context) = controller.startVpn(context)
    fun stopVpn(context: Context)  = controller.stopVpn(context)

    fun startSensorGuard(context: Context) {
        context.startService(
            Intent(context, SensorGuardService::class.java).apply {
                action = SensorGuardService.ACTION_START
            }
        )
    }
}

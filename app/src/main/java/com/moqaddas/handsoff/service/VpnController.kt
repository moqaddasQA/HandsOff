package com.moqaddas.handsoff.service

import android.content.Context
import android.content.Intent
import android.net.VpnService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single entry point for the UI layer to interact with the VPN.
 *
 * Permission flow (must happen in an Activity):
 *   val intent = controller.prepareIntent(context)
 *   if (intent != null) {
 *       startActivityForResult(intent, VPN_REQUEST_CODE)
 *       // in onActivityResult: if resultCode == RESULT_OK → controller.startVpn(context)
 *   } else {
 *       controller.startVpn(context)  // already granted
 *   }
 */
@Singleton
class VpnController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val state:        StateFlow<VpnState> = VpnStateManager.state
    val blockedCount: StateFlow<Int>      = VpnStateManager.blockedCount

    /**
     * Returns an Intent to show the system VPN permission dialog,
     * or null if permission is already granted.
     */
    fun prepareIntent(context: Context): Intent? =
        VpnService.prepare(context)

    fun startVpn(context: Context) {
        VpnStateManager.setState(VpnState.Starting)
        context.startService(
            Intent(context, GuardianVpnService::class.java).apply {
                action = GuardianVpnService.ACTION_START
            }
        )
    }

    fun stopVpn(context: Context) {
        context.startService(
            Intent(context, GuardianVpnService::class.java).apply {
                action = GuardianVpnService.ACTION_STOP
            }
        )
    }
}

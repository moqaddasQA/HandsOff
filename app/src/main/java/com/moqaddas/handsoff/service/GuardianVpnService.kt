package com.moqaddas.handsoff.service

import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramSocket
import javax.inject.Inject

/**
 * HandsOff's DNS-level privacy shield.
 *
 * Architecture:
 *   TUN interface captures all DNS traffic (addDnsServer + addRoute for 10.0.0.1/32 only).
 *   A coroutine on Dispatchers.IO reads raw packets from the TUN fd.
 *   Each packet is handed to DnsPacketParser:
 *     - Blocked domain → NXDOMAIN returned instantly, no network request made.
 *     - Allowed domain → forwarded to AdGuard DNS (94.140.14.14), response returned.
 *   Result packet is written back to the TUN fd so the requesting app receives it.
 *
 * Only DNS traffic (UDP port 53 to 10.0.0.1) is intercepted.
 * All other traffic flows through the normal network path, unmodified.
 */
@AndroidEntryPoint
class GuardianVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.moqaddas.handsoff.ACTION_START_VPN"
        const val ACTION_STOP  = "com.moqaddas.handsoff.ACTION_STOP_VPN"

        private const val VPN_ADDRESS   = "10.0.0.2"   // TUN interface address
        private const val DNS_SERVER_IP = "10.0.0.1"   // fake DNS server — our TUN
    }

    @Inject lateinit var blocklist: DnsBlocklist

    private var tun: ParcelFileDescriptor? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP -> { stopGuardian(); START_NOT_STICKY }
            else        -> { startGuardian(); START_STICKY }
        }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    private fun startGuardian() {
        GuardianNotification.createChannel(this)
        // ServiceCompat handles API differences: 3-arg startForeground on API 29+,
        // legacy 2-arg on API 26–28. FOREGROUND_SERVICE_TYPE_DATA_SYNC must match
        // the android:foregroundServiceType declared in the manifest.
        ServiceCompat.startForeground(
            this,
            GuardianNotification.NOTIF_ID,
            GuardianNotification.build(this, 0),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        val iface = Builder()
            .addAddress(VPN_ADDRESS, 32)
            .addDnsServer(DNS_SERVER_IP)      // route all DNS through our TUN
            .addRoute(DNS_SERVER_IP, 32)      // only intercept traffic to our fake DNS IP
            .setSession("HandsOff Guardian")
            .setMtu(1500)
            .establish()

        if (iface == null) {
            VpnStateManager.setState(VpnState.Error("Failed to establish VPN interface"))
            stopSelf()
            return
        }

        tun = iface
        VpnStateManager.setState(VpnState.Running)
        scope.launch { runPacketLoop(iface) }
    }

    private fun stopGuardian() {
        scope.coroutineContext.cancelChildren()
        tun?.close()
        tun = null
        VpnStateManager.setState(VpnState.Stopped)
        VpnStateManager.resetCount()
        // ServiceCompat.stopForeground works on API 26–33+ without deprecation warnings
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ── Packet loop ──────────────────────────────────────────────────────────

    private suspend fun runPacketLoop(iface: ParcelFileDescriptor) {
        val input  = FileInputStream(iface.fileDescriptor)
        val output = FileOutputStream(iface.fileDescriptor)
        val buf    = ByteArray(32_767)
        var blockedTotal = 0

        while (currentCoroutineContext().isActive) {
            val length = runCatching { input.read(buf) }.getOrDefault(-1)
            if (length <= 0) {
                // Non-blocking fd returned nothing or fd was closed
                if (length < 0) break
                yield()
                continue
            }

            val result = DnsPacketParser.handle(buf, length, blocklist) { socket: DatagramSocket ->
                protect(socket)   // prevents upstream socket from looping through TUN
            } ?: continue

            runCatching { output.write(result.packet) }

            if (result.wasBlocked) {
                blockedTotal++
                VpnStateManager.incrementBlocked()
                // Update notification every 10 blocks — avoids excessive binder calls
                if (blockedTotal % 10 == 0) updateNotification(blockedTotal)
            }
        }
    }

    private fun updateNotification(count: Int) {
        getSystemService(NotificationManager::class.java)
            .notify(GuardianNotification.NOTIF_ID, GuardianNotification.build(this, count))
    }

    override fun onDestroy() {
        scope.cancel()
        tun?.close()
        VpnStateManager.setState(VpnState.Stopped)
        super.onDestroy()
    }
}

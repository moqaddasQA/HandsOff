package com.moqaddas.handsoff.presentation

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moqaddas.handsoff.data.db.ThreatEventEntity
import com.moqaddas.handsoff.domain.model.ThreatType
import com.moqaddas.handsoff.service.VpnState
import java.text.SimpleDateFormat
import java.util.*

// ── Palette ──────────────────────────────────────────────────────────────────
private val BgColor     = Color(0xFF0D1117)
private val SurfaceColor= Color(0xFF161B22)
private val BorderColor = Color(0xFF30363D)
private val ActiveColor = Color(0xFF00D26A)
private val IdleColor   = Color(0xFF30363D)
private val StartColor  = Color(0xFFE3B341)
private val ErrorColor  = Color(0xFFF85149)
private val MicColor    = Color(0xFFFF7B72)
private val CamColor    = Color(0xFFD2A8FF)
private val TextPrimary = Color(0xFFE6EDF3)
private val TextSecond  = Color(0xFF8B949E)

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val context       = LocalContext.current
    val vpnState      by viewModel.vpnState.collectAsStateWithLifecycle()
    val blockedCount  by viewModel.blockedCount.collectAsStateWithLifecycle()
    val recentEvents  by viewModel.recentEvents.collectAsStateWithLifecycle(emptyList())
    val sensorGranted    by viewModel.sensorPermissionGranted.collectAsStateWithLifecycle()
    val blockingEnabled  by viewModel.blockingEnabled.collectAsStateWithLifecycle()
    val shizukuAvailable by viewModel.shizukuAvailable.collectAsStateWithLifecycle()

    // Start sensor guard + refresh Shizuku state on first composition
    LaunchedEffect(Unit) {
        viewModel.startSensorGuard(context)
        viewModel.refreshShizukuState()
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.startVpn(context)
    }

    fun toggle() {
        when (vpnState) {
            is VpnState.Running, VpnState.Starting -> viewModel.stopVpn(context)
            else -> {
                val intent = viewModel.prepareIntent(context)
                if (intent != null) permLauncher.launch(intent) else viewModel.startVpn(context)
            }
        }
    }

    val shieldColor by animateColorAsState(
        targetValue = when (vpnState) {
            VpnState.Running  -> ActiveColor
            VpnState.Starting -> StartColor
            is VpnState.Error -> ErrorColor
            else              -> IdleColor
        },
        animationSpec = tween(600),
        label = "shieldColor"
    )

    val isActive = vpnState is VpnState.Running || vpnState == VpnState.Starting

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ── Header ─────────────────────────────────────────────────────────
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("HandsOff", color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                StatusBadge(vpnState)
            }
        }

        // ── Shield + counter ────────────────────────────────────────────────
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(148.dp)
                        .background(shieldColor.copy(alpha = 0.10f), CircleShape)
                ) {
                    Icon(Icons.Rounded.Security, "Guardian shield", tint = shieldColor, modifier = Modifier.size(88.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$blockedCount", color = TextPrimary, fontSize = 64.sp, fontWeight = FontWeight.Bold, letterSpacing = (-2).sp)
                    Text("trackers blocked", color = TextSecond, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // ── Toggle button ───────────────────────────────────────────────────
        item {
            Button(
                onClick = ::toggle,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) Color(0xFF21262D) else ActiveColor,
                    contentColor   = if (isActive) ErrorColor else Color(0xFF0D1117)
                )
            ) {
                Text(
                    text = when (vpnState) {
                        VpnState.Running  -> "Stop Protection"
                        VpnState.Starting -> "Starting…"
                        is VpnState.Error -> "Retry"
                        else              -> "Start Protection"
                    },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // ── Sensor permission prompt ────────────────────────────────────────
        if (sensorGranted == false) {
            item { SensorSetupCard() }
        }

        // ── Block Mode toggle (only when Shizuku is bound) ──────────────────
        if (shizukuAvailable) {
            item {
                BlockModeCard(
                    enabled = blockingEnabled,
                    onToggle = { viewModel.setBlockingEnabled(!blockingEnabled) }
                )
            }
        }

        // ── Event timeline ──────────────────────────────────────────────────
        if (recentEvents.isNotEmpty()) {
            item {
                Text(
                    "Recent Events",
                    color = TextSecond,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            items(recentEvents) { event ->
                EventRow(event)
            }
        }
    }
}

@Composable
private fun StatusBadge(state: VpnState) {
    val (label, color) = when (state) {
        VpnState.Running  -> "ACTIVE"     to ActiveColor
        VpnState.Starting -> "CONNECTING" to StartColor
        is VpnState.Error -> "ERROR"      to ErrorColor
        else              -> "OFF"        to TextSecond
    }
    Surface(shape = RoundedCornerShape(20.dp), color = color.copy(alpha = 0.15f)) {
        Text(label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
            color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
    }
}

@Composable
private fun SensorSetupCard() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1C2128),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE3B341).copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Mic, null, tint = StartColor, modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Sensor Monitoring Needs Setup", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("Grant GET_APP_OPS_STATS via ADB to enable mic/camera detection", color = TextSecond, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun BlockModeCard(enabled: Boolean, onToggle: () -> Unit) {
    val blockColor = if (enabled) ErrorColor else TextSecond
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1C2128),
        border = androidx.compose.foundation.BorderStroke(1.dp, blockColor.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.Block, null, tint = blockColor, modifier = Modifier.size(22.dp))
                Column {
                    Text(
                        "Block Mode",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (enabled) "Mic & camera access denied instantly"
                        else "Detect only — tap to also block",
                        color = TextSecond,
                        fontSize = 12.sp
                    )
                }
            }
            Switch(
                checked = enabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = ErrorColor,
                    uncheckedThumbColor = TextSecond,
                    uncheckedTrackColor = Color(0xFF30363D)
                )
            )
        }
    }
}

@Composable
private fun EventRow(event: ThreatEventEntity) {
    val (icon, color) = when (event.threatType) {
        ThreatType.MIC_ACCESS.name    -> Icons.Rounded.Mic       to MicColor
        ThreatType.CAMERA_ACCESS.name -> Icons.Rounded.PhotoCamera to CamColor
        else                          -> Icons.Rounded.Shield     to TextSecond
    }
    val time = remember(event.detectedAt) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(event.detectedAt))
    }

    Surface(shape = RoundedCornerShape(10.dp), color = SurfaceColor) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(36.dp).background(color.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(event.appName, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(event.description, color = TextSecond, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(time, color = TextSecond, fontSize = 11.sp)
        }
    }
}

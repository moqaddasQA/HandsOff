package com.moqaddas.handsoff.presentation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch

private val BgColor      = Color(0xFF0D1117)
private val SurfaceColor = Color(0xFF161B22)
private val BorderColor  = Color(0xFF30363D)
private val ActiveColor  = Color(0xFF00D26A)
private val TextPrimary  = Color(0xFFE6EDF3)
private val TextSecond   = Color(0xFF8B949E)
private val MicColor     = Color(0xFFFF7B72)
private val CamColor     = Color(0xFFD2A8FF)

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .systemBarsPadding()
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 140.dp)
        ) { page ->
            when (page) {
                0 -> WelcomePage()
                1 -> FeaturesPage()
                2 -> SetupPage()
            }
        }

        // Pill-style page indicators
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { i ->
                val active = pagerState.currentPage == i
                val color by animateColorAsState(
                    targetValue = if (active) ActiveColor else BorderColor,
                    animationSpec = tween(300),
                    label = "dot$i"
                )
                Box(
                    modifier = Modifier
                        .size(width = if (active) 22.dp else 8.dp, height = 8.dp)
                        .background(color, RoundedCornerShape(4.dp))
                )
            }
        }

        // Primary CTA button
        Button(
            onClick = {
                if (pagerState.currentPage < 2) {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                } else {
                    onComplete()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 48.dp)
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ActiveColor,
                contentColor = Color(0xFF0D1117)
            )
        ) {
            Text(
                text = if (pagerState.currentPage < 2) "Next" else "Start Protecting",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun WelcomePage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(120.dp)
                .background(ActiveColor.copy(alpha = 0.10f), CircleShape)
        ) {
            Icon(
                Icons.Rounded.Security,
                contentDescription = null,
                tint = ActiveColor,
                modifier = Modifier.size(72.dp)
            )
        }
        Spacer(Modifier.height(40.dp))
        Text(
            "HandsOff",
            color = TextPrimary,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Privacy is a human right.\nNot a privilege.",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 28.sp
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "HandsOff watches your device so you don't have to.",
            color = TextSecond,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}

@Composable
private fun FeaturesPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Your device.\nYour rules.",
            color = TextPrimary,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            letterSpacing = (-0.5).sp
        )
        Spacer(Modifier.height(40.dp))
        FeatureCard(
            icon = Icons.Rounded.Mic,
            tint = MicColor,
            title = "Mic Guardian",
            body = "Detect and block microphone access in real time."
        )
        Spacer(Modifier.height(12.dp))
        FeatureCard(
            icon = Icons.Rounded.PhotoCamera,
            tint = CamColor,
            title = "Camera Guard",
            body = "Know instantly when any app opens your camera."
        )
        Spacer(Modifier.height(12.dp))
        FeatureCard(
            icon = Icons.Rounded.Shield,
            tint = ActiveColor,
            title = "Tracker Shield",
            body = "Block 250+ ad tracking and spyware domains via local VPN."
        )
    }
}

@Composable
private fun FeatureCard(icon: ImageVector, tint: Color, title: String, body: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceColor,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(tint.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            }
            Column {
                Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(body, color = TextSecond, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun SetupPage() {
    val context = LocalContext.current
    val adbCmd = "adb shell pm grant ${context.packageName} android.permission.GET_APP_OPS_STATS"
    var copied by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "One optional step.",
            color = TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "For full mic & camera detection, run this command from your computer once:",
            color = TextSecond,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(24.dp))
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF0E1C2F),
            border = BorderStroke(1.dp, Color(0xFF1F6FEB).copy(alpha = 0.4f))
        ) {
            Text(
                text = adbCmd,
                modifier = Modifier.padding(16.dp),
                color = Color(0xFF79C0FF),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp
            )
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                val clip = ClipData.newPlainText("ADB command", adbCmd)
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(clip)
                copied = true
            },
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecond),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (copied) "Copied!" else "Copy command", fontSize = 13.sp)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "You can skip this — tracker blocking is always active without it.",
            color = TextSecond,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}

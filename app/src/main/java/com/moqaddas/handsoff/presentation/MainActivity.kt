package com.moqaddas.handsoff.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import dagger.hilt.android.AndroidEntryPoint

private const val PREFS_NAME = "handsoff_prefs"
private const val KEY_ONBOARDING_DONE = "onboarding_complete"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        setContent {
            var onboardingDone by remember {
                mutableStateOf(prefs.getBoolean(KEY_ONBOARDING_DONE, false))
            }
            if (onboardingDone) {
                DashboardScreen()
            } else {
                OnboardingScreen {
                    prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
                    onboardingDone = true
                }
            }
        }
    }
}

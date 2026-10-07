package com.dculus.stayfocused

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.dculus.stayfocused.navigation.AppStartViewModel
import com.dculus.stayfocused.navigation.StayFocusedNavigation
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appStart: AppStartViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { appStart.startGraph.value == null }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            StayFocusedTheme {
                appStart.startGraph
                    .collectAsState()
                    .value
                    ?.let {
                        StayFocusedNavigation(startGraph = it, onFinishOnboarding = appStart::completeOnboarding)
                    }
            }
        }
    }
}

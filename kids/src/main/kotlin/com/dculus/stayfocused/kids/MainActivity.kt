package com.dculus.stayfocused.kids

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val start: KidsStartViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            StayFocusedTheme {
                start.startRoute
                    .collectAsState()
                    .value
                    ?.let { KidsNavigation(startRoute = it, onSetupComplete = start::completeSetup) }
            }
        }
    }
}

package com.dculus.stayfocused.kids

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.dculus.stayfocused.kids.monitoring.MonitoringLauncher
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val start: KidsStartViewModel by viewModels()

    @Inject
    lateinit var monitoringLauncher: MonitoringLauncher

    override fun onStart() {
        super.onStart()
        // A foreground start is always allowed from a visible activity: recovers a start refused at process launch
        // and picks up notifications being turned back on.
        monitoringLauncher.sync()
    }

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

package com.dculus.stayfocused

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.dculus.stayfocused.core.blocking.engine.EngineHealthMonitor
import com.dculus.stayfocused.core.blocking.permissions.PermissionsRepository
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.dculus.stayfocused.navigation.AppStartViewModel
import com.dculus.stayfocused.navigation.StartGraph
import com.dculus.stayfocused.navigation.StayFocusedNavigation
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@Composable
private fun BlockingRecoveryGuidance(
    permissions: PermissionsRepository,
    health: EngineHealthMonitor,
) {
    val permissionState = permissions.observe().collectAsState(initial = permissions.current()).value
    val engineState = health.health.collectAsState().value
    if (!permissionState.accessibility || !engineState.serviceBound) {
        val context = LocalContext.current
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                if (!permissionState.accessibility) "Accessibility is off" else "Blocking service is not running",
            )
            Text("Turn on accessibility access to resume app blocking.")
            Button(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                Text("Turn on accessibility")
            }
        }
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appStart: AppStartViewModel by viewModels()

    @Inject lateinit var permissions: PermissionsRepository
    @Inject lateinit var engineHealth: EngineHealthMonitor

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
                        Column(Modifier.fillMaxSize()) {
                            if (it == StartGraph.MAIN) {
                                BlockingRecoveryGuidance(permissions, engineHealth)
                            }
                            StayFocusedNavigation(
                                startGraph = it,
                                onFinishOnboarding = appStart::completeOnboarding,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
            }
        }
    }
}

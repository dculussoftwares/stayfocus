package com.dculus.stayfocused.feature.home

import android.content.ActivityNotFoundException
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dculus.stayfocused.core.model.durationLabel
import com.dculus.stayfocused.core.ui.components.SfBottomSheet
import com.dculus.stayfocused.core.ui.components.SfToastHost
import com.dculus.stayfocused.core.ui.components.ToastController
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import timber.log.Timber

private const val DEFAULT_BREAK_MINS = 30

/** Where each tappable part of Home leads. Supplied by the app's navigation graph. */
class HomeDestinations(
    val onOpenAccount: () -> Unit,
    /** "+ New block": the wizard with target "This phone". */
    val onNewBlock: () -> Unit,
    /** "AI Describe": the Block tab with the describe box open. */
    val onAiDescribe: () -> Unit,
    val onOpenInsights: () -> Unit,
    /** "Manage": the Block tab. */
    val onOpenBlock: () -> Unit,
    val onOpenDevices: () -> Unit,
    val onOpenDevice: (deviceId: String) -> Unit,
)

/** Everything the dashboard can do, bound to the ViewModel in [HomeRoute]. */
internal class HomeActions(
    val destinations: HomeDestinations,
    val onAllowUsageAccess: () -> Unit,
    val onSetBlockEnabled: (id: String, enabled: Boolean) -> Unit,
    val onStartBreak: (mins: Int) -> Unit,
    val onEndBreak: () -> Unit,
)

@Composable
internal fun HomeRoute(
    destinations: HomeDestinations,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val breakUi = viewModel.breakUi.collectAsStateWithLifecycle().value
    val resources = LocalResources.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = remember(scope) { ToastController(scope) }
    // Back from the system settings the permission may have changed.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshUsageAccess() }
    LaunchedEffect(viewModel, toast, resources) {
        viewModel.events.collect { event ->
            toast.show(
                when (event) {
                    is HomeEvent.BreakStarted -> {
                        resources.getString(R.string.break_toast_started, durationLabel(event.mins))
                    }

                    HomeEvent.BreakFailed -> {
                        resources.getString(R.string.break_toast_failed)
                    }

                    HomeEvent.BlockToggleFailed -> {
                        resources.getString(R.string.home_toast_block_failed)
                    }
                },
            )
        }
    }
    val actions =
        HomeActions(
            destinations = destinations,
            onAllowUsageAccess = {
                try {
                    context.startActivity(viewModel.usageSettingsIntent())
                } catch (e: ActivityNotFoundException) {
                    // Some devices have no usage-access screen; nothing else can be done here.
                    Timber.w(e, "Usage access settings not available")
                }
            },
            onSetBlockEnabled = viewModel::setBlockEnabled,
            onStartBreak = viewModel::startBreak,
            onEndBreak = viewModel::endBreak,
        )
    Box(Modifier.fillMaxSize()) {
        HomeScreen(state = state, breakUi = breakUi, actions = actions)
        SfToastHost(toast, bottomPadding = 24.dp)
    }
}

/**
 * The Home tab: header, actions, screen-time gauge, break card, this phone's blocks and the linked phones.
 * Status (gauge, devices) and actions (new block, break) are separate cards.
 */
@Composable
internal fun HomeScreen(
    state: HomeUiState,
    breakUi: BreakUi,
    actions: HomeActions,
) {
    var sheetVisible by rememberSaveable { mutableStateOf(false) }
    var mins by rememberSaveable { mutableIntStateOf(DEFAULT_BREAK_MINS) }
    val destinations = actions.destinations
    LazyColumn(
        state = rememberLazyListState(),
        modifier = Modifier.fillMaxSize().background(StayFocusedTheme.colors.background).testTag(HOME_LIST_TAG),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header") { HomeHeader(state.header, destinations.onOpenAccount) }
        item(key = "actions") { HomeActionRow(destinations.onNewBlock, destinations.onAiDescribe) }
        item(key = "gauge") {
            GaugeCard(state.gauge, destinations.onOpenInsights, actions.onAllowUsageAccess)
        }
        item(key = "break") {
            when (breakUi) {
                BreakUi.Idle -> BreakIdleCard(onClick = { sheetVisible = true })
                is BreakUi.Active -> BreakActiveCard(breakUi, onEnd = actions.onEndBreak)
            }
        }
        item(key = "blocks") {
            ActiveBlocksSection(state.blocks, destinations.onOpenBlock, actions.onSetBlockEnabled)
        }
        item(key = "devices") {
            LinkedPhonesSection(
                state.devices,
                destinations.onOpenDevices,
                destinations.onOpenDevice,
                destinations.onOpenDevices,
            )
        }
    }
    SfBottomSheet(visible = sheetVisible, onDismiss = { sheetVisible = false }) {
        BreakSheetContent(
            mins = mins,
            onMinsChange = { mins = it },
            onStart = {
                // A second tap while the sheet slides out must not start the break twice.
                if (sheetVisible) {
                    sheetVisible = false
                    actions.onStartBreak(mins)
                }
            },
        )
    }
}

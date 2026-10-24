package com.dculus.stayfocused.feature.home

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
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
import com.dculus.stayfocused.core.model.v5Duration
import com.dculus.stayfocused.core.ui.components.LocalTabBarPadding
import com.dculus.stayfocused.core.ui.components.ToastController
import com.dculus.stayfocused.core.ui.components.V5BottomSheet
import com.dculus.stayfocused.core.ui.components.V5ToastHost
import com.dculus.stayfocused.core.ui.theme.V5Screen
import timber.log.Timber

internal const val HOME_LIST_TAG = "home_list"

private const val DEFAULT_BREAK_MINS = 30

/** Where each tappable part of Home leads. Supplied by the app's navigation graph. */
class HomeDestinations(
    val onOpenAccount: () -> Unit,
    /** "Block apps": the wizard with target "This phone". */
    val onNewBlock: () -> Unit,
    /** "Describe a block in your words": the Block tab with the describe box open. */
    val onAiDescribe: () -> Unit,
    val onOpenInsights: () -> Unit,
    /** "Manage": the Block tab. */
    val onOpenBlock: () -> Unit,
    /** The Family tab (the bell when no phone needs attention). */
    val onOpenDevices: () -> Unit,
    val onOpenDevice: (deviceId: String) -> Unit,
    /** "Link a child's phone": the link flow (its sign-in gate applies). */
    val onLinkPhone: () -> Unit,
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
    // Kept as State (not read here): only the break tile reads it, once per second.
    val breakState: State<BreakUi> = viewModel.breakUi.collectAsStateWithLifecycle()
    val breakUi = remember(breakState) { { breakState.value } }
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
                        resources.getString(R.string.break_toast_started, v5Duration(event.mins))
                    }

                    HomeEvent.BreakEnded -> {
                        resources.getString(R.string.break_toast_ended)
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
        V5ToastHost(toast)
    }
}

/**
 * The Home tab (v5): header, brand-gradient hero, "Block apps" and "Take a break" tiles, the AI pill and a card per
 * linked phone. [breakUi] is a lambda so only the break tile reads the per-second countdown.
 */
@Composable
internal fun HomeScreen(
    state: HomeUiState,
    breakUi: () -> BreakUi,
    actions: HomeActions,
) {
    var sheetVisible by rememberSaveable { mutableStateOf(false) }
    var mins by rememberSaveable { mutableIntStateOf(DEFAULT_BREAK_MINS) }
    val destinations = actions.destinations
    V5Screen(darkContent = false) {
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxSize().testTag(HOME_LIST_TAG),
            // The app shell already insets the status bar; the prototype's 54 dp top is 8 dp below it.
            contentPadding =
                PaddingValues(
                    start = 14.dp,
                    end = 14.dp,
                    top = 8.dp,
                    bottom = 24.dp + LocalTabBarPadding.current,
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") {
                val attention = state.attentionDevice
                HomeHeader(
                    header = state.header,
                    showBell = state.devices.isNotEmpty(),
                    bellBadge = attention != null,
                    onAvatar = destinations.onOpenAccount,
                    onBell = { attention?.let { destinations.onOpenDevice(it.id) } ?: destinations.onOpenDevices() },
                )
            }
            item(key = "hero") {
                HomeHero(state.gauge, destinations.onOpenInsights, actions.onAllowUsageAccess)
            }
            item(key = "tiles") {
                HomeTiles(
                    state = state,
                    breakUi = breakUi,
                    breakLengthMins = mins,
                    onNewBlock = destinations.onNewBlock,
                    onOpenBreakSheet = { sheetVisible = true },
                    onEndBreak = actions.onEndBreak,
                )
            }
            if (state.aiAvailable) item(key = "ai") { AiPill(destinations.onAiDescribe) }
            if (state.devices.isEmpty()) {
                item(key = "link") { LinkChildRow(destinations.onLinkPhone) }
            } else {
                items(state.devices, key = { it.id }) { device ->
                    ChildCard(device, onClick = { destinations.onOpenDevice(device.id) })
                }
            }
            // Shipped Home content the prototype does not show: today vs average, launches/unlocks, block switches.
            (state.gauge as? GaugeUi.Ready)?.let { gauge -> item(key = "stats") { TodayStatsCard(gauge) } }
            item(key = "blocks") {
                ActiveBlocksCard(state.blocks, actions.onSetBlockEnabled, destinations.onOpenBlock)
            }
        }
        V5BottomSheet(visible = sheetVisible, onDismiss = { sheetVisible = false }) {
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
}

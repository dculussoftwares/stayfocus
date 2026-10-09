package com.dculus.stayfocused.feature.block

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.navigation.Block
import com.dculus.stayfocused.core.navigation.BlockWizard
import com.dculus.stayfocused.core.ui.components.SfToastHost
import com.dculus.stayfocused.core.ui.components.ToastController
import kotlinx.coroutines.flow.MutableStateFlow

fun NavController.navigateToBlock(navOptions: NavOptions? = null) = navigate(Block, navOptions)

/** Opens the block wizard for [target] (a child device id, null = this phone), optionally from [prefill]. */
fun NavController.navigateToBlockWizard(
    target: String? = null,
    prefill: String? = null,
    navOptions: NavOptions? = null,
) = navigate(BlockWizard(target = target, prefill = prefill), navOptions)

/** Block tab. [onOpenWizard] opens the wizard for a target (child device id, null = this phone) and template. */
fun NavGraphBuilder.blockScreen(
    onOpenWizard: (target: String?, prefill: String?) -> Unit,
    /** Supplies the ViewModel; null uses Hilt. Lets navigation tests run without a Hilt application. */
    viewModelProvider: (@Composable () -> BlockViewModel)? = null,
) {
    composable<Block> { entry ->
        BlockRoute(onOpenWizard, viewModelProvider?.invoke() ?: hiltViewModel(), entry.savedStateHandle)
    }
}

/** Saved-state key the wizard sets on the Block entry to open the Block tab in AI mode. */
internal const val OPEN_AI_KEY = "block_open_ai"

private fun NavController.onWizard(): Boolean = currentDestination?.hasRoute<BlockWizard>() == true

/** Closes the wizard. A repeated call (double tap) is ignored once the wizard is gone. */
fun NavController.closeBlockWizard() {
    if (onWizard()) popBackStack()
}

/** Leaves the wizard for the Block tab with the "Describe" panel open. Ignored once the wizard is gone. */
fun NavController.openBlockInAiMode() {
    if (!onWizard()) return
    if (!popBackStack<Block>(inclusive = false)) navigate(Block)
    runCatching { getBackStackEntry<Block>() }.getOrNull()?.savedStateHandle?.set(OPEN_AI_KEY, true)
}

/** Saved-state keys the wizard sets on the Block entry after "Turn on block": the target to select and the toast. */
internal const val SELECT_TARGET_KEY = "block_select_target"
internal const val SAVED_TOAST_KEY = "block_saved_toast"

/**
 * Leaves the wizard for the Block tab with [deviceId] (null = this phone) selected, and shows [message] there.
 * Ignored once the wizard is gone.
 */
fun NavController.finishBlockWizard(
    deviceId: String?,
    message: String,
) {
    if (!onWizard()) return
    if (!popBackStack<Block>(inclusive = false)) navigate(Block)
    runCatching { getBackStackEntry<Block>() }.getOrNull()?.savedStateHandle?.let {
        it[SELECT_TARGET_KEY] = deviceId.orEmpty()
        it[SAVED_TOAST_KEY] = message
    }
}

/** The 3-step block wizard, a sub-screen without a tab bar. */
fun NavGraphBuilder.blockWizardScreen(
    onClose: () -> Unit,
    onDescribeWithAi: () -> Unit,
    onFinish: (deviceId: String?, message: String) -> Unit,
    /** Supplies the ViewModel; null uses Hilt. Lets navigation tests run without a Hilt application. */
    viewModelProvider: (@Composable () -> WizardViewModel)? = null,
) {
    composable<BlockWizard> {
        BlockWizardRoute(onClose, onDescribeWithAi, onFinish, viewModelProvider?.invoke() ?: hiltViewModel())
    }
}

@Composable
internal fun BlockRoute(
    onOpenWizard: (target: String?, prefill: String?) -> Unit,
    viewModel: BlockViewModel,
    savedStateHandle: SavedStateHandle? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openAi by (savedStateHandle?.getStateFlow(OPEN_AI_KEY, false) ?: remember { MutableStateFlow(false) })
        .collectAsStateWithLifecycle()
    LaunchedEffect(openAi) {
        if (openAi) {
            viewModel.openAi()
            savedStateHandle?.set(OPEN_AI_KEY, false)
        }
    }
    val savedTarget by (
        savedStateHandle?.getStateFlow<String?>(SELECT_TARGET_KEY, null)
            ?: remember { MutableStateFlow(null) }
    ).collectAsStateWithLifecycle()
    val savedToast by (
        savedStateHandle?.getStateFlow<String?>(SAVED_TOAST_KEY, null)
            ?: remember { MutableStateFlow(null) }
    ).collectAsStateWithLifecycle()
    val toast = rememberToastController()
    LaunchedEffect(savedTarget, savedToast) {
        savedTarget?.let {
            viewModel.selectTarget(if (it.isEmpty()) BlockTarget.ThisPhone else BlockTarget.Device(it))
            savedStateHandle?.set(SELECT_TARGET_KEY, null)
        }
        savedToast?.let {
            toast.show(it)
            savedStateHandle?.set(SAVED_TOAST_KEY, null)
        }
    }
    val currentOpenWizard by rememberUpdatedState(onOpenWizard)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is BlockEvent.OpenWizard -> currentOpenWizard(event.target, event.prefill)
            }
        }
    }
    val actions =
        remember(viewModel) {
            BlockActions(
                onSelectTarget = viewModel::selectTarget,
                onNewBlock = viewModel::newBlock,
                onToggleAi = viewModel::toggleAi,
                onAiText = viewModel::setAiText,
                onSelectTab = viewModel::selectTab,
                onSetEnabled = viewModel::setBlockEnabled,
                onTemplate = viewModel::useTemplate,
                onToggleAppLock = viewModel::toggleAppLock,
            )
        }
    Box(Modifier.fillMaxSize()) {
        BlockScreen(state, actions)
        SfToastHost(toast)
    }
}

@Composable
internal fun BlockWizardRoute(
    onClose: () -> Unit,
    onDescribeWithAi: () -> Unit,
    onFinish: (deviceId: String?, message: String) -> Unit,
    viewModel: WizardViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentClose by rememberUpdatedState(onClose)
    val currentDescribe by rememberUpdatedState(onDescribeWithAi)
    val currentSaved by rememberUpdatedState(onFinish)
    val toast = rememberToastController()
    val pickOneApp = stringResource(R.string.block_wizard_pick_one_app)
    val saveFailed = stringResource(R.string.block_wizard_save_failed)
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                WizardEvent.Close -> {
                    currentClose()
                }

                WizardEvent.DescribeWithAi -> {
                    currentDescribe()
                }

                WizardEvent.PickAtLeastOneApp -> {
                    toast.show(pickOneApp)
                }

                WizardEvent.SaveFailed -> {
                    toast.show(saveFailed)
                }

                is WizardEvent.Saved -> {
                    val target = event.targetName ?: resources.getString(R.string.block_this_phone)
                    currentSaved(event.deviceId, resources.getString(R.string.block_wizard_saved, event.name, target))
                }
            }
        }
    }
    BackHandler(onBack = viewModel::back)
    val actions =
        remember(viewModel) {
            WizardActions(
                onBack = viewModel::back,
                onPickType = viewModel::pickType,
                onToggleApp = viewModel::toggleApp,
                onNext = viewModel::next,
                onDescribe = viewModel::describeWithAi,
                onSelectTarget = viewModel::selectTarget,
                onPeriod = viewModel::setPeriod,
                onMins = viewModel::setMins,
                onNow = viewModel::setNow,
                onCycleValue = viewModel::setCycleValue,
                onCycleEdit = viewModel::selectCycleEdit,
                onRange = viewModel::setRange,
                onToggleDay = viewModel::toggleDay,
                onChangeType = viewModel::changeType,
            )
        }
    Box(Modifier.fillMaxSize()) {
        WizardScreen(state, actions)
        SfToastHost(toast, bottomPadding = 96.dp)
    }
}

@Composable
private fun rememberToastController(): ToastController {
    val scope = rememberCoroutineScope()
    return remember(scope) { ToastController(scope) }
}

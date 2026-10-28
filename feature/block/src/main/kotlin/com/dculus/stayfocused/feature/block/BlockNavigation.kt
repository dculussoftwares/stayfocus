@file:Suppress("TooManyFunctions") // The Block tab's whole navigation surface lives here.

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
import com.dculus.stayfocused.core.navigation.BlockDescribe
import com.dculus.stayfocused.core.navigation.BlockWizard
import com.dculus.stayfocused.core.ui.components.ToastController
import com.dculus.stayfocused.core.ui.components.V5ToastHost
import kotlinx.coroutines.flow.MutableStateFlow

fun NavController.navigateToBlock(navOptions: NavOptions? = null) = navigate(Block, navOptions)

/** Opens the block wizard for [target] (a child device id, null = this phone), optionally from [prefill]. */
fun NavController.navigateToBlockWizard(
    target: String? = null,
    prefill: String? = null,
    navOptions: NavOptions? = null,
) = navigate(BlockWizard(target = target, prefill = prefill), navOptions)

/** Opens the wizard in edit mode, at step 3, for [blockId] on [target] (a child device id, null = this phone). */
fun NavController.navigateToBlockEditor(
    blockId: String,
    target: String? = null,
    navOptions: NavOptions? = null,
) = navigate(BlockWizard(target = target, blockId = blockId), navOptions)

/** Block tab. [onOpenWizard] opens the wizard for a target (child device id, null = this phone) and template. */
fun NavGraphBuilder.blockScreen(
    onOpenWizard: (target: String?, prefill: String?) -> Unit,
    onDescribe: (target: String?) -> Unit,
    /** Opens the wizard in edit mode for a block id on a target (child device id, null = this phone). */
    onEditBlock: (blockId: String, target: String?) -> Unit = { _, _ -> },
    /** Supplies the ViewModel; null uses Hilt. Lets navigation tests run without a Hilt application. */
    viewModelProvider: (@Composable () -> BlockViewModel)? = null,
) {
    composable<Block> { entry ->
        BlockRoute(
            onOpenWizard,
            onDescribe,
            viewModelProvider?.invoke() ?: hiltViewModel(),
            entry.savedStateHandle,
            onEditBlock,
        )
    }
}

private fun NavController.onWizard(): Boolean = currentDestination?.hasRoute<BlockWizard>() == true

/** Closes the wizard. A repeated call (double tap) is ignored once the wizard is gone. */
fun NavController.closeBlockWizard() {
    if (onWizard()) popBackStack()
}

/** Opens the "Describe a block" screen for [target] (a child device id, null = this phone). */
fun NavController.navigateToBlockDescribe(
    target: String? = null,
    navOptions: NavOptions? = null,
) = navigate(BlockDescribe(target = target), navOptions)

/** Replaces the wizard with the describe screen (the wizard's "Rather just describe it?"). Ignored once it is gone. */
fun NavController.replaceWizardWithDescribe(target: String?) {
    if (!onWizard()) return
    navigate(BlockDescribe(target = target)) { popUpTo<BlockWizard> { inclusive = true } }
}

private fun NavController.onDescribe(): Boolean = currentDestination?.hasRoute<BlockDescribe>() == true

/** Replaces the describe screen with the wizard on step 3 under the AI DRAFT banner. Ignored once it is gone. */
fun NavController.openWizardFromDescribe(target: String?) {
    if (!onDescribe()) return
    navigate(BlockWizard(target = target, prefill = AI_PREFILL)) { popUpTo<BlockDescribe> { inclusive = true } }
}

/** Closes the describe screen. A repeated call (double tap) is ignored once it is gone. */
fun NavController.closeBlockDescribe() {
    if (onDescribe()) popBackStack()
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
    onDescribeWithAi: (target: String?) -> Unit,
    onFinish: (deviceId: String?, message: String) -> Unit,
    /** Supplies the ViewModel; null uses Hilt. Lets navigation tests run without a Hilt application. */
    viewModelProvider: (@Composable () -> WizardViewModel)? = null,
) {
    composable<BlockWizard> {
        BlockWizardRoute(onClose, onDescribeWithAi, onFinish, viewModelProvider?.invoke() ?: hiltViewModel())
    }
}

/** "Describe a block" (AI), a sub-screen without a tab bar. */
fun NavGraphBuilder.blockDescribeScreen(
    onBack: () -> Unit,
    onOpenWizard: (target: String?) -> Unit,
    /** Supplies the ViewModel; null uses Hilt. Lets navigation tests run without a Hilt application. */
    viewModelProvider: (@Composable () -> DescribeViewModel)? = null,
) {
    composable<BlockDescribe> {
        BlockDescribeRoute(onBack, onOpenWizard, viewModelProvider?.invoke() ?: hiltViewModel())
    }
}

@Composable
internal fun BlockDescribeRoute(
    onBack: () -> Unit,
    onOpenWizard: (target: String?) -> Unit,
    viewModel: DescribeViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toast = rememberToastController()
    val linkFirst = stringResource(R.string.block_wizard_link_first)
    val currentOpenWizard by rememberUpdatedState(onOpenWizard)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DescribeEvent.OpenWizard -> currentOpenWizard(event.target)
                DescribeEvent.NoLinkedPhone -> toast.show(linkFirst)
            }
        }
    }
    val actions =
        remember(viewModel, onBack) {
            DescribeActions(
                onBack = onBack,
                onFlipTarget = viewModel::flipTarget,
                onText = viewModel::setText,
                onBuild = viewModel::build,
            )
        }
    Box(Modifier.fillMaxSize()) {
        DescribeScreen(state, actions)
        V5ToastHost(toast, bottomOffset = 24.dp)
    }
}

@Composable
internal fun BlockRoute(
    onOpenWizard: (target: String?, prefill: String?) -> Unit,
    onDescribe: (target: String?) -> Unit,
    viewModel: BlockViewModel,
    savedStateHandle: SavedStateHandle? = null,
    onEditBlock: (blockId: String, target: String?) -> Unit = { _, _ -> },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
    val paused = stringResource(R.string.block_toast_paused)
    val turnedOn = stringResource(R.string.block_toast_on)
    val currentOpenWizard by rememberUpdatedState(onOpenWizard)
    val currentEditBlock by rememberUpdatedState(onEditBlock)
    val currentDescribe by rememberUpdatedState(onDescribe)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is BlockEvent.OpenWizard -> currentOpenWizard(event.target, event.prefill)
                is BlockEvent.EditBlock -> currentEditBlock(event.blockId, event.target)
                is BlockEvent.OpenDescribe -> currentDescribe(event.target)
            }
        }
    }
    val actions =
        remember(viewModel) {
            BlockActions(
                onSelectTarget = viewModel::selectTarget,
                onNewBlock = viewModel::newBlock,
                onDescribe = viewModel::describe,
                onSelectTab = viewModel::selectTab,
                onSetEnabled = { id, enabled ->
                    viewModel.setBlockEnabled(id, enabled)
                    toast.show(if (enabled) turnedOn else paused)
                },
                onEdit = viewModel::editBlock,
                onTemplate = viewModel::useTemplate,
                onToggleAppLock = viewModel::toggleAppLock,
            )
        }
    Box(Modifier.fillMaxSize()) {
        BlockScreen(state, actions)
        V5ToastHost(toast)
    }
}

@Composable
internal fun BlockWizardRoute(
    onClose: () -> Unit,
    onDescribeWithAi: (target: String?) -> Unit,
    onFinish: (deviceId: String?, message: String) -> Unit,
    viewModel: WizardViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentClose by rememberUpdatedState(onClose)
    val currentDescribe by rememberUpdatedState(onDescribeWithAi)
    val currentSaved by rememberUpdatedState(onFinish)
    val toast = rememberToastController()
    val pickOneApp = stringResource(R.string.block_wizard_pick_one_app)
    val linkFirst = stringResource(R.string.block_wizard_link_first)
    val saveFailed = stringResource(R.string.block_wizard_save_failed)
    val deleted = stringResource(R.string.block_deleted)
    val updateFailed = stringResource(R.string.block_wizard_update_failed)
    val deleteFailed = stringResource(R.string.block_delete_failed)
    val reportSent = stringResource(R.string.block_report_sent)
    val reportFailed = stringResource(R.string.block_report_failed)
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                WizardEvent.Close -> {
                    currentClose()
                }

                is WizardEvent.DescribeWithAi -> {
                    currentDescribe(event.target)
                }

                WizardEvent.PickAtLeastOneApp -> {
                    toast.show(pickOneApp)
                }

                WizardEvent.NoLinkedPhone -> {
                    toast.show(linkFirst)
                }

                WizardEvent.SaveFailed -> {
                    toast.show(saveFailed)
                }

                is WizardEvent.Saved -> {
                    val message =
                        when {
                            event.edited -> resources.getString(R.string.block_wizard_saved_changes, event.name)
                            event.targetName == null -> resources.getString(R.string.block_toast_on)
                            else -> resources.getString(R.string.block_wizard_sent, event.targetName)
                        }
                    currentSaved(event.deviceId, message)
                }

                WizardEvent.UpdateFailed -> {
                    toast.show(updateFailed)
                }

                WizardEvent.DeleteFailed -> {
                    toast.show(deleteFailed)
                }

                WizardEvent.ReportSent -> {
                    toast.show(reportSent)
                }

                WizardEvent.ReportFailed -> {
                    toast.show(reportFailed)
                }

                is WizardEvent.Deleted -> {
                    currentSaved(event.deviceId, deleted)
                }
            }
        }
    }
    BackHandler(onBack = viewModel::back)
    val actions =
        remember(viewModel) {
            WizardActions(
                onBack = viewModel::back,
                onClose = viewModel::close,
                onPickType = viewModel::pickType,
                onToggleApp = viewModel::toggleApp,
                onNext = viewModel::next,
                onDescribe = viewModel::describeWithAi,
                onFlipTarget = viewModel::flipTarget,
                onPeriod = viewModel::setPeriod,
                onMins = viewModel::setMins,
                onNow = viewModel::setNow,
                onPreset = viewModel::pickSchedulePreset,
                onDays = viewModel::setDays,
                onCycleValue = viewModel::setCycleValue,
                onCycleEdit = viewModel::selectCycleEdit,
                onRange = viewModel::setRange,
                onChangeType = viewModel::changeType,
                onDelete = viewModel::deleteBlock,
                onEditApps = viewModel::editApps,
                onKeepEditing = viewModel::keepEditing,
                onDiscard = viewModel::discardChanges,
                onReport = viewModel::reportBadResult,
            )
        }
    Box(Modifier.fillMaxSize()) {
        WizardScreen(state, actions)
        V5ToastHost(toast, bottomOffset = 24.dp)
    }
}

@Composable
private fun rememberToastController(): ToastController {
    val scope = rememberCoroutineScope()
    return remember(scope) { ToastController(scope) }
}

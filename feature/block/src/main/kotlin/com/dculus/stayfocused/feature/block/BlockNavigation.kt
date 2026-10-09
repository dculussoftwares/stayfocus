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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
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

/** Leaves the wizard for the Block tab with the "Describe" panel open. */
fun NavController.openBlockInAiMode() {
    if (!popBackStack<Block>(inclusive = false)) navigate(Block)
    runCatching { getBackStackEntry<Block>() }.getOrNull()?.savedStateHandle?.set(OPEN_AI_KEY, true)
}

/** The 3-step block wizard, a sub-screen without a tab bar. */
fun NavGraphBuilder.blockWizardScreen(
    onClose: () -> Unit,
    onDescribeWithAi: () -> Unit,
    /** Supplies the ViewModel; null uses Hilt. Lets navigation tests run without a Hilt application. */
    viewModelProvider: (@Composable () -> WizardViewModel)? = null,
) {
    composable<BlockWizard> {
        BlockWizardRoute(onClose, onDescribeWithAi, viewModelProvider?.invoke() ?: hiltViewModel())
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
            )
        }
    BlockScreen(state, actions)
}

@Composable
internal fun BlockWizardRoute(
    onClose: () -> Unit,
    onDescribeWithAi: () -> Unit,
    viewModel: WizardViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentClose by rememberUpdatedState(onClose)
    val currentDescribe by rememberUpdatedState(onDescribeWithAi)
    val toast = rememberToastController()
    val pickOneApp = stringResource(R.string.block_wizard_pick_one_app)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                WizardEvent.Close -> currentClose()
                WizardEvent.DescribeWithAi -> currentDescribe()
                WizardEvent.PickAtLeastOneApp -> toast.show(pickOneApp)
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

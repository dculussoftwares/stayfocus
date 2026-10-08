package com.dculus.stayfocused.feature.block

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.dculus.stayfocused.core.navigation.Block
import com.dculus.stayfocused.core.navigation.BlockWizard
import com.dculus.stayfocused.core.ui.components.NavPlaceholderScreen

fun NavController.navigateToBlock(navOptions: NavOptions? = null) = navigate(Block, navOptions)

/** Opens the block wizard for [target] (a child device id, null = this phone), optionally from [prefill]. */
fun NavController.navigateToBlockWizard(
    target: String? = null,
    prefill: String? = null,
    navOptions: NavOptions? = null,
) = navigate(BlockWizard(target = target, prefill = prefill), navOptions)

/** Block tab. [onOpenWizard] opens the wizard for a target (child device id, null = this phone) and template. */
fun NavGraphBuilder.blockScreen(onOpenWizard: (target: String?, prefill: String?) -> Unit) {
    composable<Block> { BlockRoute(onOpenWizard) }
}

/** The 3-step block wizard, a sub-screen without a tab bar. */
fun NavGraphBuilder.blockWizardScreen() {
    composable<BlockWizard> { BlockWizardRoute() }
}

@Composable
internal fun BlockRoute(
    onOpenWizard: (target: String?, prefill: String?) -> Unit,
    viewModel: BlockViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
internal fun BlockWizardRoute() {
    NavPlaceholderScreen(title = stringResource(R.string.block_wizard_title))
}

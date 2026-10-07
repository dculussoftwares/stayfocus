package com.dculus.stayfocused.feature.block

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.dculus.stayfocused.core.navigation.Block
import com.dculus.stayfocused.core.navigation.BlockWizard
import com.dculus.stayfocused.core.ui.components.NavPlaceholderScreen
import com.dculus.stayfocused.core.ui.components.PlaceholderAction

fun NavController.navigateToBlock(navOptions: NavOptions? = null) = navigate(Block, navOptions)

/** Opens the block wizard for [target] (a child device id, null = this phone), optionally from [prefill]. */
fun NavController.navigateToBlockWizard(
    target: String? = null,
    prefill: String? = null,
    navOptions: NavOptions? = null,
) = navigate(BlockWizard(target = target, prefill = prefill), navOptions)

/** Block tab. [onNewBlock] opens the wizard for this phone. */
fun NavGraphBuilder.blockScreen(onNewBlock: () -> Unit) {
    composable<Block> { BlockRoute(onNewBlock) }
}

/** The 3-step block wizard, a sub-screen without a tab bar. */
fun NavGraphBuilder.blockWizardScreen() {
    composable<BlockWizard> { BlockWizardRoute() }
}

@Composable
internal fun BlockRoute(onNewBlock: () -> Unit) {
    val newBlock = stringResource(R.string.block_placeholder_new_block)
    val actions = remember(newBlock, onNewBlock) { listOf(PlaceholderAction(newBlock, onNewBlock)) }
    NavPlaceholderScreen(title = stringResource(R.string.block_title), actions = actions)
}

@Composable
internal fun BlockWizardRoute() {
    NavPlaceholderScreen(title = stringResource(R.string.block_wizard_title))
}

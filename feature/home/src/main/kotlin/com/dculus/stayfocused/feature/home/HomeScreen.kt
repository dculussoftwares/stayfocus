package com.dculus.stayfocused.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dculus.stayfocused.core.model.durationLabel
import com.dculus.stayfocused.core.ui.components.NavPlaceholderScreen
import com.dculus.stayfocused.core.ui.components.PlaceholderAction
import com.dculus.stayfocused.core.ui.components.SfBottomSheet
import com.dculus.stayfocused.core.ui.components.SfToastHost
import com.dculus.stayfocused.core.ui.components.ToastController

private const val DEFAULT_BREAK_MINS = 30

@Composable
internal fun HomeRoute(
    onOpenAccount: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val breakUi = viewModel.breakUi.collectAsStateWithLifecycle().value
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val toast = remember(scope) { ToastController(scope) }
    Box(Modifier.fillMaxSize()) {
        HomeScreen(
            breakUi = breakUi,
            onOpenAccount = onOpenAccount,
            onStartBreak = { mins ->
                viewModel.startBreak(mins)
                toast.show(resources.getString(R.string.break_toast_started, durationLabel(mins)))
            },
            onEndBreak = viewModel::endBreak,
        )
        SfToastHost(toast, bottomPadding = 24.dp)
    }
}

/** Home tab. Only the break card exists so far; the rest is still the navigation placeholder. */
@Composable
internal fun HomeScreen(
    breakUi: BreakUi,
    onOpenAccount: () -> Unit,
    onStartBreak: (mins: Int) -> Unit,
    onEndBreak: () -> Unit,
) {
    var sheetVisible by rememberSaveable { mutableStateOf(false) }
    var mins by rememberSaveable { mutableIntStateOf(DEFAULT_BREAK_MINS) }
    val openAccount = stringResource(R.string.home_placeholder_open_account)
    val actions = remember(openAccount, onOpenAccount) { listOf(PlaceholderAction(openAccount, onOpenAccount)) }
    NavPlaceholderScreen(
        title = stringResource(R.string.home_title),
        actions = actions,
        header = {
            when (breakUi) {
                BreakUi.Idle -> BreakIdleCard(onClick = { sheetVisible = true })
                is BreakUi.Active -> BreakActiveCard(breakUi, onEnd = onEndBreak)
            }
        },
    )
    SfBottomSheet(visible = sheetVisible, onDismiss = { sheetVisible = false }) {
        BreakSheetContent(
            mins = mins,
            onMinsChange = { mins = it },
            onStart = {
                sheetVisible = false
                onStartBreak(mins)
            },
        )
    }
}

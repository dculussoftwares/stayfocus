package com.dculus.stayfocused.core.blocking.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dculus.stayfocused.core.blocking.evaluator.Decision
import kotlinx.coroutines.delay
import java.time.Instant

private const val TICK_MS = 1000L

/**
 * [BlockScreen] wired to a [BlockDecisionSource]: re-evaluates every second, updates the countdown and calls
 * [onAllow] once the decision turns to Allow. Shared by [BlockActivity] and the accessibility-overlay fallback.
 */
@Composable
internal fun BlockHost(
    request: BlockRequest,
    decisionSource: BlockDecisionSource,
    extras: BlockScreenExtras?,
    onGoHome: () -> Unit,
    onAllow: () -> Unit,
) {
    var decision by remember(request) { mutableStateOf<Decision>(request.decision) }
    var now by remember(request) { mutableStateOf(Instant.now()) }
    val allow by rememberUpdatedState(onAllow)
    LaunchedEffect(request) {
        while (true) {
            now = Instant.now()
            decision = decisionSource.current(request.pkg, request.decision, now)
            if (decision is Decision.Allow) {
                allow()
                return@LaunchedEffect
            }
            delay(TICK_MS)
        }
    }
    val block = decision as? Decision.Block ?: return
    BlockScreen(
        pkg = request.pkg,
        appLabel = request.appLabel,
        reason = block.reason,
        timeLine = timeLineFor(block, now),
        onGoHome = onGoHome,
        extraActions = { extras?.Content(request.pkg, block, Modifier) },
    )
}

package com.dculus.stayfocused.kids

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.dculus.stayfocused.core.ui.components.LedDot
import com.dculus.stayfocused.core.ui.components.MonoLabel
import com.dculus.stayfocused.core.ui.components.OutlineButton
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme

/** Kids: Link. Shows the QR with the one-time token and moves on once the parent has scanned it. */
@Composable
fun KidsLinkRoute(
    onClaim: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: KidsLinkViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val currentOnClaim by rememberUpdatedState(onClaim)
    LaunchedEffect(state) {
        if (state == KidsLinkState.Claimed) currentOnClaim()
    }
    KidsLinkScreen(state = state, onRetry = viewModel::retry, modifier = modifier)
}

@Composable
internal fun KidsLinkScreen(
    state: KidsLinkState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StayFocusedTheme.colors
    Column(
        modifier.fillMaxSize().padding(StayFocusedTheme.spacing.gap22),
        verticalArrangement = Arrangement.spacedBy(StayFocusedTheme.spacing.gap22),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(StayFocusedTheme.spacing.gap8)) {
            Text(
                stringResource(R.string.kids_link_heading),
                style = StayFocusedTheme.type.displayL,
                color = colors.text,
            )
            Text(stringResource(R.string.kids_link_body), style = StayFocusedTheme.type.body, color = colors.secondary)
        }
        when (state) {
            is KidsLinkState.Waiting -> {
                QrCard(state.uri, Modifier.align(Alignment.CenterHorizontally))
                Row(
                    Modifier.align(Alignment.CenterHorizontally),
                    horizontalArrangement = Arrangement.spacedBy(StayFocusedTheme.spacing.gap8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LedDot(pulse = true)
                    MonoLabel(stringResource(R.string.kids_link_waiting, state.remaining))
                }
            }

            KidsLinkState.Failed -> {
                Text(
                    stringResource(R.string.kids_link_failed),
                    style = StayFocusedTheme.type.body,
                    color = colors.warning,
                )
                OutlineButton(text = stringResource(R.string.kids_link_retry), onClick = onRetry)
            }

            KidsLinkState.Loading, KidsLinkState.Claimed -> {
                Unit
            }
        }
    }
}

/** Ink modules on a light card; a stock camera app needs the strong contrast and the quiet zone. */
@Composable
private fun QrCard(
    uri: String,
    modifier: Modifier = Modifier,
) {
    val colors = StayFocusedTheme.colors
    val ink = colors.background.toArgb()
    val light = colors.text.toArgb()
    val bitmap = remember(uri, ink, light) { QrCodes.bitmap(uri, ink, light) }
    val description = stringResource(R.string.kids_link_qr_description)
    Image(
        bitmap = bitmap,
        contentDescription = description,
        filterQuality = FilterQuality.None,
        modifier =
            modifier
                .fillMaxWidth(QR_WIDTH_FRACTION)
                .aspectRatio(1f)
                .background(colors.text, StayFocusedTheme.shapes.hero),
    )
}

private const val QR_WIDTH_FRACTION = 0.8f

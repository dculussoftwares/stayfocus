@file:Suppress("MagicNumber")

package com.dculus.stayfocused.kids

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dculus.stayfocused.core.ui.components.PillButton
import com.dculus.stayfocused.core.ui.components.PillButtonSize
import com.dculus.stayfocused.core.ui.components.PillButtonVariant
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.dculus.stayfocused.core.ui.theme.V5Gradients
import com.dculus.stayfocused.core.ui.theme.V5Screen
import com.dculus.stayfocused.core.ui.theme.grainOverlay
import com.dculus.stayfocused.core.ui.theme.v5NavigationBarPadding
import com.dculus.stayfocused.core.ui.theme.v5StatusBarPadding

/** Kids: show code. Shows the QR with the one-time token and moves on once the parent has scanned it. */
@Composable
fun KidsLinkRoute(
    onClaim: (token: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: KidsLinkViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val currentOnClaim by rememberUpdatedState(onClaim)
    LaunchedEffect(state) {
        (state as? KidsLinkState.Claimed)?.let { currentOnClaim(it.token) }
    }
    KidsLinkScreen(state = state, onRetry = viewModel::retry, modifier = modifier)
}

@Composable
internal fun KidsLinkScreen(
    state: KidsLinkState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    V5Screen(darkContent = true, modifier = modifier, background = V5Gradients.kidsQr.adaptive) {
        Box(Modifier.fillMaxSize().grainOverlay())
        val colors = StayFocusedTheme.colors
        val type = StayFocusedTheme.type
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .v5StatusBarPadding(extra = 18.dp)
                .v5NavigationBarPadding(extra = 36.dp)
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.app_name),
                style = type.label.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onGradient,
            )
            Text(
                stringResource(R.string.kids_link_title),
                style = type.headline.copy(lineHeight = 36.7.sp),
                color = colors.onGradient,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 20.dp),
            )
            Text(
                stringResource(R.string.kids_link_subtitle),
                style = type.body.copy(fontWeight = FontWeight.Medium),
                color = colors.onGradient,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            when (state) {
                is KidsLinkState.Waiting -> {
                    QrCard(state.uri, Modifier.padding(top = 30.dp))
                    Text(
                        stringResource(R.string.kids_link_expires, state.remaining),
                        style = type.body.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.onGradient,
                        modifier = Modifier.padding(top = 22.dp),
                    )
                }

                KidsLinkState.Failed -> {
                    Text(
                        stringResource(R.string.kids_link_failed),
                        style = type.body.copy(fontWeight = FontWeight.Medium),
                        color = colors.onGradient,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 30.dp),
                    )
                    PillButton(
                        text = stringResource(R.string.kids_link_retry),
                        onClick = onRetry,
                        variant = PillButtonVariant.Glass,
                        size = PillButtonSize.Sheet,
                        modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
                    )
                }

                KidsLinkState.Loading, is KidsLinkState.Claimed -> {
                    Unit
                }
            }
        }
    }
}

/** Ink modules on a white r38 card (padding 20, 210 dp code); a stock camera app needs the contrast and quiet zone. */
@Composable
private fun QrCard(
    uri: String,
    modifier: Modifier = Modifier,
) {
    val colors = StayFocusedTheme.colors
    val ink = colors.ink.toArgb()
    val light = Color.White.toArgb()
    val bitmap = remember(uri, ink, light) { QrCodes.bitmap(uri, ink, light) }
    val description = stringResource(R.string.kids_link_qr_description)
    val shape = StayFocusedTheme.shapes.hero
    val shadow = colors.ink.copy(alpha = 0.5f)
    Box(
        modifier
            .shadow(30.dp, shape, ambientColor = shadow, spotColor = shadow)
            .background(colors.surface, shape)
            .padding(20.dp),
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = description,
            filterQuality = FilterQuality.None,
            modifier = Modifier.size(210.dp),
        )
    }
}

@file:Suppress("MagicNumber")

package com.dculus.stayfocused.feature.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dculus.stayfocused.core.ui.components.PrimaryButton
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme

internal const val LINK_ADD_SCAN_TAG = "link_add_scan"
internal const val LINK_ADD_SIGN_IN_TAG = "link_add_sign_in"
internal const val LINK_BACK_TAG = "link_back"

@Composable
internal fun LinkAddRoute(
    onBack: () -> Unit,
    onScan: () -> Unit,
    onSignIn: () -> Unit,
    viewModel: LinkAddViewModel,
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    LinkAddScreen(state = state, onBack = onBack, onScan = onScan, onSignIn = onSignIn)
}

@Composable
internal fun LinkAddScreen(
    state: LinkAddState,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onSignIn: () -> Unit,
) {
    val colors = StayFocusedTheme.colors
    val type = StayFocusedTheme.type
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LinkBackButton(onBack = onBack, light = false)
        when (state) {
            LinkAddState.Loading -> {
                Unit
            }

            LinkAddState.SignedOut -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.link_signin_title), style = type.displayL, color = colors.text)
                    Text(stringResource(R.string.link_signin_body), style = type.body, color = colors.secondary)
                }
                PrimaryButton(
                    text = stringResource(R.string.link_signin_cta),
                    onClick = onSignIn,
                    modifier = Modifier.fillMaxWidth().testTag(LINK_ADD_SIGN_IN_TAG),
                )
            }

            LinkAddState.SignedIn -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.link_add_heading), style = type.displayL, color = colors.text)
                    Text(stringResource(R.string.link_add_body), style = type.body, color = colors.secondary)
                }
                val label = stringResource(R.string.link_add_scan_title)
                Row(
                    Modifier
                        .testTag(LINK_ADD_SCAN_TAG)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(colors.accent)
                        .clickable(role = Role.Button, onClick = onScan)
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(label, style = type.title, color = colors.onAccent)
                        Text(
                            stringResource(R.string.link_add_scan_subtitle),
                            style = type.bodyS.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.onAccent.copy(alpha = 0.7f),
                        )
                    }
                    Text("→", style = type.title.copy(fontSize = 22.sp), color = colors.onAccent)
                }
            }
        }
    }
}

/** The 42 dp rounded back tile used on sub-screens. [light] is the translucent variant over the camera. */
@Composable
internal fun LinkBackButton(
    onBack: () -> Unit,
    light: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = StayFocusedTheme.colors
    val back = stringResource(R.string.link_back)
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .testTag(LINK_BACK_TAG)
            .size(42.dp)
            .clip(shape)
            .background(if (light) colors.text.copy(alpha = 0.1f) else colors.panel)
            .then(if (light) Modifier else Modifier.border(1.dp, colors.hairline10, shape))
            .clickable(onClickLabel = back, role = Role.Button, onClick = onBack)
            .semantics { contentDescription = back },
        contentAlignment = Alignment.Center,
    ) {
        Text("←", color = colors.text, style = StayFocusedTheme.type.bodyL)
    }
}

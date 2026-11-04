@file:Suppress("MagicNumber")

package com.dculus.vesper.feature.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dculus.vesper.core.ui.components.PillButton
import com.dculus.vesper.core.ui.components.RoundIconButton
import com.dculus.vesper.core.ui.components.V5Card
import com.dculus.vesper.core.ui.icons.V5Icons
import com.dculus.vesper.core.ui.theme.V5Screen
import com.dculus.vesper.core.ui.theme.VesperTheme
import com.dculus.vesper.core.ui.theme.v5NavigationBarPadding
import com.dculus.vesper.core.ui.theme.v5StatusBarPadding

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
    V5Screen(darkContent = false) {
        val colors = VesperTheme.colors
        Column(
            Modifier
                .fillMaxSize()
                .v5StatusBarPadding(extra = 10.dp)
                .v5NavigationBarPadding(extra = 36.dp)
                .padding(horizontal = VesperTheme.spacing.screenOnboarding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RoundIconButton(
                icon = V5Icons.back,
                contentDescription = stringResource(R.string.link_back),
                onClick = onBack,
                modifier = Modifier.testTag(LINK_BACK_TAG),
            )
            when (state) {
                LinkAddState.Loading -> {
                    Unit
                }

                LinkAddState.SignedOut -> {
                    Title(stringResource(R.string.link_signin_title))
                    Body(stringResource(R.string.link_signin_body))
                    PillButton(
                        text = stringResource(R.string.link_signin_cta),
                        onClick = onSignIn,
                        modifier = Modifier.fillMaxWidth().testTag(LINK_ADD_SIGN_IN_TAG),
                    )
                }

                LinkAddState.SignedIn -> {
                    Title(stringResource(R.string.link_add_heading))
                    Body(stringResource(R.string.link_add_body))
                    V5Card(
                        modifier = Modifier.fillMaxWidth().testTag(LINK_ADD_SCAN_TAG),
                        onClick = onScan,
                        onClickLabel = stringResource(R.string.link_add_scan_title),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    stringResource(R.string.link_add_scan_title),
                                    style = VesperTheme.type.body.copy(fontSize = 17.sp),
                                    color = colors.ink,
                                )
                                Text(
                                    stringResource(R.string.link_add_scan_subtitle),
                                    style = VesperTheme.type.body.copy(fontSize = 13.sp),
                                    color = colors.textSecondary,
                                )
                            }
                            Icon(V5Icons.arrow, contentDescription = null, tint = colors.ink)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(
        text,
        style = VesperTheme.type.headline.copy(fontSize = 34.sp),
        color = VesperTheme.colors.ink,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun Body(text: String) {
    Text(text, style = VesperTheme.type.body.copy(fontSize = 15.sp), color = VesperTheme.colors.textSecondary)
}

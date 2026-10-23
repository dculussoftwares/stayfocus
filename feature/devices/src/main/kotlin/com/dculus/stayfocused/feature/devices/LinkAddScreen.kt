@file:Suppress("MagicNumber")

package com.dculus.stayfocused.feature.devices

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
import com.dculus.stayfocused.core.ui.v5.components.PillButton
import com.dculus.stayfocused.core.ui.v5.components.RoundIconButton
import com.dculus.stayfocused.core.ui.v5.components.V5Card
import com.dculus.stayfocused.core.ui.v5.icons.V5Icons
import com.dculus.stayfocused.core.ui.v5.theme.V5Screen
import com.dculus.stayfocused.core.ui.v5.theme.V5Theme
import com.dculus.stayfocused.core.ui.v5.theme.v5NavigationBarPadding
import com.dculus.stayfocused.core.ui.v5.theme.v5StatusBarPadding

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
        val colors = V5Theme.colors
        Column(
            Modifier
                .fillMaxSize()
                .v5StatusBarPadding(extra = 10.dp)
                .v5NavigationBarPadding(extra = 36.dp)
                .padding(horizontal = V5Theme.spacing.screenOnboarding),
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
                                    style = V5Theme.type.body.copy(fontSize = 17.sp),
                                    color = colors.ink,
                                )
                                Text(
                                    stringResource(R.string.link_add_scan_subtitle),
                                    style = V5Theme.type.body.copy(fontSize = 13.sp),
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
        style = V5Theme.type.headline.copy(fontSize = 34.sp),
        color = V5Theme.colors.ink,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun Body(text: String) {
    Text(text, style = V5Theme.type.body.copy(fontSize = 15.sp), color = V5Theme.colors.textSecondary)
}

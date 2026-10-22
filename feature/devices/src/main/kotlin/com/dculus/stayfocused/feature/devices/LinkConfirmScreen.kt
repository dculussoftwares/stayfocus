@file:Suppress("MagicNumber", "LongMethod")

package com.dculus.stayfocused.feature.devices

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dculus.stayfocused.core.sync.LinkFailure
import com.dculus.stayfocused.core.ui.v5.components.DotMatrixText
import com.dculus.stayfocused.core.ui.v5.components.PillButton
import com.dculus.stayfocused.core.ui.v5.components.RoundIconButton
import com.dculus.stayfocused.core.ui.v5.components.V5Card
import com.dculus.stayfocused.core.ui.v5.components.V5TextField
import com.dculus.stayfocused.core.ui.v5.icons.V5Icons
import com.dculus.stayfocused.core.ui.v5.theme.V5Screen
import com.dculus.stayfocused.core.ui.v5.theme.V5Theme
import com.dculus.stayfocused.core.ui.v5.theme.v5NavigationBarPadding
import com.dculus.stayfocused.core.ui.v5.theme.v5StatusBarPadding

internal const val LINK_CONFIRM_CODE_TAG = "link_confirm_code"
internal const val LINK_CONFIRM_NAME_TAG = "link_confirm_name"
internal const val LINK_CONFIRM_WAITING_TAG = "link_confirm_waiting"
internal const val LINK_CONFIRM_RETRY_TAG = "link_confirm_retry"
internal const val LINK_CONFIRM_ERROR_TAG = "link_confirm_error"
internal const val LINK_CONFIRM_LINK_TAG = "link_confirm_link"

/** Confirm link: the same 6 digits as on the child's phone, the phone's name, then waiting for the child's Allow. */
@Composable
internal fun LinkConfirmRoute(
    onBack: () -> Unit,
    onLink: (deviceId: String, message: String) -> Unit,
    viewModel: LinkConfirmViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val name by viewModel.name.collectAsStateWithLifecycle()
    val trimmed = name.trim()
    val toast =
        if (trimmed.isEmpty()) {
            stringResource(R.string.link_confirm_linked_toast_unnamed)
        } else {
            stringResource(R.string.link_confirm_linked_toast, trimmed)
        }
    val currentOnLinked by rememberUpdatedState(onLink)
    val currentToast by rememberUpdatedState(toast)
    LaunchedEffect(state) {
        (state as? LinkConfirmState.Linked)?.let { currentOnLinked(it.deviceId, currentToast) }
    }
    val leave = {
        viewModel.cancel()
        onBack()
    }
    // The system Back gesture withdraws the claim just like the on-screen button.
    BackHandler(onBack = leave)
    LinkConfirmScreen(
        state = state,
        name = name,
        onNameChange = viewModel::onNameChange,
        onBack = leave,
        onRetry = viewModel::retry,
    )
}

@Composable
internal fun LinkConfirmScreen(
    state: LinkConfirmState,
    name: String,
    onNameChange: (String) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    V5Screen(darkContent = false) {
        val colors = V5Theme.colors
        // The parent's tap on "Codes match, link it": the claim is already held, so linking finishes when the child
        // taps Allow. Reset when the code changes (a retry).
        var confirmed by rememberSaveable { mutableStateOf(false) }
        Column(
            Modifier
                .fillMaxSize()
                // The prototype's 56 dp top padding includes its 46 dp fake status bar.
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
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    stringResource(R.string.link_confirm_title),
                    style = V5Theme.type.headline.copy(fontSize = 34.sp),
                    color = colors.ink,
                    modifier = Modifier.padding(top = 8.dp),
                )
                when (state) {
                    LinkConfirmState.Claiming, is LinkConfirmState.Linked -> {
                        Unit
                    }

                    is LinkConfirmState.Waiting -> {
                        WaitingBody(state.code, name, onNameChange)
                    }

                    // v5-extra: M13-05 (declined, expired, used and network states)
                    LinkConfirmState.Declined -> {
                        Failure(R.string.link_confirm_declined_title, R.string.link_confirm_declined_body)
                    }

                    is LinkConfirmState.Failed -> {
                        val (title, body) = state.reason.copy()
                        Failure(title, body)
                    }
                }
            }
            when (state) {
                is LinkConfirmState.Waiting -> {
                    PillButton(
                        text = stringResource(R.string.link_confirm_cta),
                        onClick = { confirmed = true },
                        loading = confirmed,
                        // v5-extra: M13-05 (waiting for the child to tap Allow)
                        loadingLabel = stringResource(R.string.link_confirm_waiting),
                        modifier = Modifier.testTag(if (confirmed) LINK_CONFIRM_WAITING_TAG else LINK_CONFIRM_LINK_TAG),
                    )
                }

                is LinkConfirmState.Failed -> {
                    if (state.reason.canRetry()) {
                        PillButton(
                            text = stringResource(R.string.link_confirm_retry),
                            onClick = {
                                confirmed = false
                                onRetry()
                            },
                            modifier = Modifier.testTag(LINK_CONFIRM_RETRY_TAG),
                        )
                    }
                }

                else -> {
                    Unit
                }
            }
        }
    }
}

@Composable
private fun WaitingBody(
    code: String,
    name: String,
    onNameChange: (String) -> Unit,
) {
    val colors = V5Theme.colors
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            stringResource(R.string.link_confirm_subtitle),
            style = V5Theme.type.body,
            color = colors.textSecondary,
        )
        V5Card(
            Modifier.fillMaxWidth().testTag(LINK_CONFIRM_CODE_TAG),
            radius = 34.dp,
            padding = 18.dp,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val grouped = code.chunked(3).joinToString(" ")
                DotMatrixText(
                    text = grouped,
                    contentDescription = code.toList().joinToString(" "),
                    dotSize = 5.dp,
                    color = colors.ink,
                )
            }
        }
        Text(
            stringResource(R.string.link_confirm_name_label),
            style = V5Theme.type.body.copy(fontSize = 14.sp),
            color = colors.textSecondary,
            modifier = Modifier.padding(start = 8.dp, top = 4.dp),
        )
        V5TextField(
            value = name,
            onValueChange = onNameChange,
            placeholder = stringResource(R.string.link_confirm_name_label),
            modifier = Modifier.testTag(LINK_CONFIRM_NAME_TAG),
        )
        Text(
            stringResource(R.string.link_confirm_note),
            style = V5Theme.type.body.copy(fontSize = 14.sp),
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun Failure(
    title: Int,
    body: Int,
) {
    Column(Modifier.testTag(LINK_CONFIRM_ERROR_TAG), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), style = V5Theme.type.heading, color = V5Theme.colors.ink)
        Text(stringResource(body), style = V5Theme.type.body, color = V5Theme.colors.textSecondary)
    }
}

/** Only a failed connection or an unexpected error can work the second time; a dead code needs a new QR code. */
private fun LinkFailure.canRetry(): Boolean = this == LinkFailure.Network || this == LinkFailure.Other

private fun LinkFailure.copy(): Pair<Int, Int> =
    when (this) {
        LinkFailure.Expired -> {
            R.string.link_confirm_expired_title to R.string.link_confirm_expired_body
        }

        LinkFailure.Used, LinkFailure.Invalid, LinkFailure.NotClaimed -> {
            R.string.link_confirm_used_title to
                R.string.link_confirm_used_body
        }

        LinkFailure.Network -> {
            R.string.link_confirm_network_title to R.string.link_confirm_network_body
        }

        LinkFailure.Other -> {
            R.string.link_confirm_other_title to R.string.link_confirm_other_body
        }
    }

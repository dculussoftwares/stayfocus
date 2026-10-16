package com.dculus.stayfocused.feature.devices

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dculus.stayfocused.core.sync.LinkFailure
import com.dculus.stayfocused.core.ui.components.LedDot
import com.dculus.stayfocused.core.ui.components.LinkCodeCard
import com.dculus.stayfocused.core.ui.components.MonoLabel
import com.dculus.stayfocused.core.ui.components.OutlineButton
import com.dculus.stayfocused.core.ui.components.SfTextField
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme

internal const val LINK_CONFIRM_CODE_TAG = "link_confirm_code"
internal const val LINK_CONFIRM_NAME_TAG = "link_confirm_name"
internal const val LINK_CONFIRM_WAITING_TAG = "link_confirm_waiting"
internal const val LINK_CONFIRM_RETRY_TAG = "link_confirm_retry"
internal const val LINK_CONFIRM_ERROR_TAG = "link_confirm_error"

/** Confirm link: the same 6 digits as on the child's phone, the phone's name, then waiting for the child's Allow. */
@Composable
internal fun LinkConfirmRoute(
    onBack: () -> Unit,
    onLink: (deviceId: String, message: String) -> Unit,
    viewModel: LinkConfirmViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val name by viewModel.name.collectAsStateWithLifecycle()
    val toast = stringResource(R.string.link_confirm_linked_toast)
    val currentOnLinked by rememberUpdatedState(onLink)
    LaunchedEffect(state) {
        (state as? LinkConfirmState.Linked)?.let { currentOnLinked(it.deviceId, toast) }
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
    val colors = StayFocusedTheme.colors
    val type = StayFocusedTheme.type
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        LinkBackButton(onBack = onBack, light = false)
        when (state) {
            LinkConfirmState.Claiming, is LinkConfirmState.Linked -> {
                Text(stringResource(R.string.link_confirm_title), style = type.displayL, color = colors.text)
            }

            is LinkConfirmState.Waiting -> {
                Text(stringResource(R.string.link_confirm_title), style = type.displayL, color = colors.text)
                LinkCodeCard(
                    label = stringResource(R.string.link_confirm_code_label),
                    code = state.code,
                    modifier = Modifier.testTag(LINK_CONFIRM_CODE_TAG),
                )
                SfTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = stringResource(R.string.link_confirm_name_label),
                    modifier = Modifier.testTag(LINK_CONFIRM_NAME_TAG),
                )
                Text(stringResource(R.string.link_confirm_note), style = type.bodyS, color = colors.secondary)
                Row(
                    Modifier.testTag(LINK_CONFIRM_WAITING_TAG),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LedDot(pulse = true)
                    MonoLabel(stringResource(R.string.link_confirm_waiting))
                }
            }

            LinkConfirmState.Declined -> {
                Failure(R.string.link_confirm_declined_title, R.string.link_confirm_declined_body, null)
            }

            is LinkConfirmState.Failed -> {
                val (title, body) = state.reason.copy()
                Failure(title, body, if (state.reason.canRetry()) onRetry else null)
            }
        }
    }
}

@Composable
private fun Failure(
    title: Int,
    body: Int,
    onRetry: (() -> Unit)?,
) {
    val colors = StayFocusedTheme.colors
    val type = StayFocusedTheme.type
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(
            Modifier.testTag(LINK_CONFIRM_ERROR_TAG),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(title), style = type.displayL, color = colors.text)
            Text(stringResource(body), style = type.body, color = colors.secondary)
        }
        if (onRetry != null) {
            OutlineButton(
                text = stringResource(R.string.link_confirm_retry),
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth().testTag(LINK_CONFIRM_RETRY_TAG),
            )
        }
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

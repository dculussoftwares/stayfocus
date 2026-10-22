@file:Suppress("MagicNumber")

package com.dculus.stayfocused.kids

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dculus.stayfocused.core.ui.components.formatLinkCode
import com.dculus.stayfocused.core.ui.v5.components.DotMatrixText
import com.dculus.stayfocused.core.ui.v5.components.PillButton
import com.dculus.stayfocused.core.ui.v5.components.PillButtonSize
import com.dculus.stayfocused.core.ui.v5.components.PillButtonVariant
import com.dculus.stayfocused.core.ui.v5.components.V5Card
import com.dculus.stayfocused.core.ui.v5.theme.V5Screen
import com.dculus.stayfocused.core.ui.v5.theme.V5Theme
import com.dculus.stayfocused.core.ui.v5.theme.v5NavigationBarPadding
import com.dculus.stayfocused.core.ui.v5.theme.v5StatusBarPadding

internal const val KIDS_CONSENT_ALLOW_TAG = "kids_consent_allow"
internal const val KIDS_CONSENT_CANCEL_TAG = "kids_consent_cancel"
internal const val KIDS_CONSENT_NEW_CODE_TAG = "kids_consent_new_code"

/** Kids: allow parent. Shows the code to match, what the parent can do, and "Allow and link". */
@Composable
fun KidsConsentRoute(
    onLink: (parent: String) -> Unit,
    onShowNewCode: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: KidsConsentViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnLinked by rememberUpdatedState(onLink)
    val currentOnShowNewCode by rememberUpdatedState(onShowNewCode)
    val fallbackName = stringResource(R.string.kids_parent_fallback)
    val parentName = state.parentName ?: fallbackName
    LaunchedEffect(state.outcome) {
        when (state.outcome) {
            ConsentOutcome.Linked -> currentOnLinked(parentName)
            ConsentOutcome.ShowNewCode -> currentOnShowNewCode()
            null -> Unit
        }
    }
    // System Back says no to the parent, like the Cancel button.
    BackHandler(onBack = { viewModel.cancel(fallbackName) })
    V5Screen(darkContent = false, modifier = modifier) {
        KidsConsentScreen(
            state = state,
            onAllow = { viewModel.allow(fallbackName) },
            onCancel = { viewModel.cancel(fallbackName) },
            onShowNewCode = viewModel::showNewCode,
            onRetry = viewModel::retryConsent,
        )
    }
}

@Composable
internal fun KidsConsentScreen(
    state: KidsConsentUiState,
    onAllow: () -> Unit,
    onCancel: () -> Unit,
    onShowNewCode: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
) {
    val colors = V5Theme.colors
    val type = V5Theme.type
    val parent = state.parentName ?: stringResource(R.string.kids_parent_fallback)
    // Fills the screen when it fits and scrolls on short screens or at large font scales.
    BoxWithConstraints(modifier.fillMaxSize().v5StatusBarPadding()) {
        val minHeight = maxHeight
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = minHeight)
                .padding(start = 18.dp, end = 18.dp, top = 18.dp)
                .v5NavigationBarPadding(extra = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(R.string.app_name),
                style = type.label.copy(fontWeight = FontWeight.SemiBold),
                color = colors.pinkText,
            )
            Text(
                stringResource(R.string.kids_consent_heading, parent),
                style = type.headline,
                color = colors.ink,
            )
            if (state.code != null) {
                CodeCard(parent, state.code)
                AbilitiesCard(parent)
                Text(
                    stringResource(R.string.kids_consent_note, parent),
                    style = type.label.copy(fontWeight = FontWeight.Normal, lineHeight = 20.3.sp),
                    color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
            state.error?.let { error ->
                // v5-extra: M13-06 (shipped error messages, v5-styled)
                Text(
                    stringResource(error.messageRes()),
                    style = type.body,
                    color = colors.error,
                    modifier = Modifier.padding(horizontal = 6.dp).testTag(KIDS_CONSENT_ERROR_TAG),
                )
            }
            Spacer(Modifier.weight(1f))
            Actions(state, onAllow, onCancel, onShowNewCode, onRetry)
        }
    }
}

@Composable
private fun CodeCard(
    parent: String,
    code: String,
) {
    val colors = V5Theme.colors
    V5Card(radius = 34.dp, padding = 0.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.kids_consent_match, parent),
                style = V5Theme.type.label.copy(fontWeight = FontWeight.Normal),
                color = colors.textSecondary,
            )
            val shown = formatLinkCode(code)
            DotMatrixText(
                text = shown,
                contentDescription = code.toList().joinToString(" "),
                dotSize = 5.dp,
                color = colors.ink,
            )
        }
    }
}

@Composable
private fun AbilitiesCard(parent: String) {
    val colors = V5Theme.colors
    V5Card(radius = 30.dp, padding = 0.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.kids_consent_can_do, parent),
                style = V5Theme.type.body.copy(fontWeight = FontWeight.SemiBold),
                color = colors.ink,
            )
            stringArrayResource(R.array.kids_consent_list).forEach { item ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.padding(top = 7.dp).size(6.dp).background(colors.pink, CircleShape))
                    Text(
                        item,
                        style = V5Theme.type.body.copy(lineHeight = 20.25.sp),
                        color = colors.ink,
                    )
                }
            }
        }
    }
}

@Composable
private fun Actions(
    state: KidsConsentUiState,
    onAllow: () -> Unit,
    onCancel: () -> Unit,
    onShowNewCode: () -> Unit,
    onRetry: () -> Unit,
) = Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    // v5-extra: M13-06 (retry and "Show a new code" for the shipped error and expired states)
    if (state.code == null && state.error != null && state.error != ConsentError.Expired) {
        PillButton(
            text = stringResource(R.string.kids_link_retry),
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().testTag(KIDS_CONSENT_RETRY_TAG),
        )
    }
    if (state.error == ConsentError.Expired || (state.code == null && state.error != null)) {
        PillButton(
            text = stringResource(R.string.kids_consent_new_code),
            onClick = onShowNewCode,
            modifier = Modifier.fillMaxWidth().testTag(KIDS_CONSENT_NEW_CODE_TAG),
        )
    } else if (state.code != null) {
        PillButton(
            text = stringResource(R.string.kids_consent_allow),
            onClick = onAllow,
            enabled = !state.working,
            loading = state.working,
            modifier = Modifier.fillMaxWidth().testTag(KIDS_CONSENT_ALLOW_TAG),
        )
        PillButton(
            text = stringResource(R.string.kids_consent_cancel),
            onClick = onCancel,
            variant = PillButtonVariant.Text,
            size = PillButtonSize.Small,
            enabled = !state.working,
            modifier = Modifier.fillMaxWidth().testTag(KIDS_CONSENT_CANCEL_TAG),
        )
    }
}

internal const val KIDS_CONSENT_ERROR_TAG = "kids_consent_error"
internal const val KIDS_CONSENT_RETRY_TAG = "kids_consent_retry"

private fun ConsentError.messageRes(): Int =
    when (this) {
        ConsentError.Network -> R.string.kids_consent_network
        ConsentError.Expired -> R.string.kids_consent_expired
        ConsentError.Other -> R.string.kids_consent_failed
    }

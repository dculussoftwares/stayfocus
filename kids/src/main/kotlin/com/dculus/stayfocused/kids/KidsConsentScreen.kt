package com.dculus.stayfocused.kids

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dculus.stayfocused.core.ui.components.GhostButton
import com.dculus.stayfocused.core.ui.components.LinkCodeCard
import com.dculus.stayfocused.core.ui.components.MonoLabel
import com.dculus.stayfocused.core.ui.components.PrimaryButton
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme

internal const val KIDS_CONSENT_ALLOW_TAG = "kids_consent_allow"
internal const val KIDS_CONSENT_CANCEL_TAG = "kids_consent_cancel"
internal const val KIDS_CONSENT_NEW_CODE_TAG = "kids_consent_new_code"

/** Kids: Consent. Shows the code to match, what the parent can do, and "Allow and link". */
@Composable
fun KidsConsentRoute(
    onLink: () -> Unit,
    onShowNewCode: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: KidsConsentViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnLinked by rememberUpdatedState(onLink)
    val currentOnShowNewCode by rememberUpdatedState(onShowNewCode)
    LaunchedEffect(state.outcome) {
        when (state.outcome) {
            ConsentOutcome.Linked -> currentOnLinked()
            ConsentOutcome.ShowNewCode -> currentOnShowNewCode()
            null -> Unit
        }
    }
    val fallbackName = stringResource(R.string.kids_parent_fallback)
    // System Back says no to the parent, like the Cancel button.
    BackHandler(onBack = viewModel::cancel)
    KidsConsentScreen(
        state = state,
        onAllow = { viewModel.allow(fallbackName) },
        onCancel = viewModel::cancel,
        onShowNewCode = viewModel::showNewCode,
        onRetry = viewModel::retryConsent,
        modifier = modifier,
    )
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
    val colors = StayFocusedTheme.colors
    val type = StayFocusedTheme.type
    val parent = state.parentName ?: stringResource(R.string.kids_parent_fallback)
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(StayFocusedTheme.spacing.gap22),
        verticalArrangement = Arrangement.spacedBy(StayFocusedTheme.spacing.gap16),
    ) {
        Text(stringResource(R.string.kids_consent_heading, parent), style = type.displayL, color = colors.text)
        if (state.code != null) {
            LinkCodeCard(
                label = stringResource(R.string.kids_consent_match, parent),
                code = state.code,
            )
            Column(verticalArrangement = Arrangement.spacedBy(StayFocusedTheme.spacing.gap12)) {
                MonoLabel(stringResource(R.string.kids_consent_can_do, parent))
                stringArrayResource(R.array.kids_consent_list).forEach { item -> ConsentItem(item) }
            }
            Text(
                stringResource(R.string.kids_consent_note, parent),
                style = type.bodyS,
                color = colors.secondary,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(colors.panel, StayFocusedTheme.shapes.input)
                        .padding(StayFocusedTheme.spacing.gap14),
            )
        }
        state.error?.let { error ->
            Text(
                stringResource(error.messageRes()),
                style = type.body,
                color = colors.warning,
                modifier = Modifier.testTag(KIDS_CONSENT_ERROR_TAG),
            )
        }
        if (state.code == null && state.error != null && state.error != ConsentError.Expired) {
            PrimaryButton(
                text = stringResource(R.string.kids_link_retry),
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth().testTag(KIDS_CONSENT_RETRY_TAG),
            )
        }
        if (state.error == ConsentError.Expired || (state.code == null && state.error != null)) {
            PrimaryButton(
                text = stringResource(R.string.kids_consent_new_code),
                onClick = onShowNewCode,
                modifier = Modifier.fillMaxWidth().testTag(KIDS_CONSENT_NEW_CODE_TAG),
            )
        } else if (state.code != null) {
            PrimaryButton(
                text = stringResource(R.string.kids_consent_allow),
                onClick = onAllow,
                enabled = !state.working,
                loading = state.working,
                modifier = Modifier.fillMaxWidth().testTag(KIDS_CONSENT_ALLOW_TAG),
            )
            GhostButton(
                text = stringResource(R.string.kids_consent_cancel),
                onClick = onCancel,
                enabled = !state.working,
                modifier = Modifier.fillMaxWidth().testTag(KIDS_CONSENT_CANCEL_TAG),
            )
        }
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

@Composable
private fun ConsentItem(text: String) {
    val colors = StayFocusedTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(StayFocusedTheme.spacing.gap12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(20.dp).background(colors.accent.copy(alpha = 0.14f), StayFocusedTheme.shapes.chip),
            contentAlignment = Alignment.Center,
        ) {
            Text("✓", style = StayFocusedTheme.type.labelS, color = colors.accent)
        }
        Text(text, style = StayFocusedTheme.type.body.copy(fontWeight = FontWeight.Normal), color = colors.text)
    }
}

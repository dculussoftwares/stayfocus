package com.dculus.stayfocused.kids

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dculus.stayfocused.core.sync.KidsRule
import com.dculus.stayfocused.core.sync.KidsRuleType
import com.dculus.stayfocused.core.ui.components.GhostButton
import com.dculus.stayfocused.core.ui.components.MonoLabel
import com.dculus.stayfocused.core.ui.components.OutlineButton
import com.dculus.stayfocused.core.ui.components.PrimaryButton
import com.dculus.stayfocused.core.ui.components.SfBottomSheet
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme

internal const val KIDS_HOME_UNLINK_TAG = "kids_home_unlink"
internal const val KIDS_HOME_CONFIRM_TAG = "kids_home_unlink_confirm"
internal const val KIDS_HOME_KEEP_TAG = "kids_home_unlink_keep"
internal const val KIDS_HOME_ERROR_TAG = "kids_home_error"
internal const val KIDS_HOME_TODAY_TAG = "kids_home_today"

/** Kids: Home. [onDone] is called once with how the screen ended; the caller goes back to Link. */
@Composable
fun KidsHomeRoute(
    onDone: (outcome: HomeOutcome, parentName: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: KidsHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnDone by rememberUpdatedState(onDone)
    val fallback = stringResource(R.string.kids_parent_fallback)
    LaunchedEffect(state.outcome) {
        state.outcome?.let { currentOnDone(it, state.parentName ?: fallback) }
    }
    KidsHomeScreen(
        state = state,
        onUnlink = viewModel::askToUnlink,
        onConfirm = viewModel::confirmUnlink,
        onKeep = viewModel::dismissConfirm,
        modifier = modifier,
    )
}

@Composable
internal fun KidsHomeScreen(
    state: KidsHomeUiState,
    onUnlink: () -> Unit,
    onConfirm: () -> Unit,
    onKeep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StayFocusedTheme.colors
    val type = StayFocusedTheme.type
    val parent = state.parentName ?: stringResource(R.string.kids_parent_fallback)
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = StayFocusedTheme.spacing.gap8),
        verticalArrangement = Arrangement.spacedBy(StayFocusedTheme.spacing.gap16),
    ) {
        NoticePreview(parent)
        Column(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MonoLabel(stringResource(R.string.kids_home_today))
            Text(
                state.todayMins?.let { duration(it) } ?: "",
                style = type.displayXL.copy(fontWeight = FontWeight.SemiBold),
                color = colors.text,
                modifier = Modifier.testTag(KIDS_HOME_TODAY_TAG),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(StayFocusedTheme.spacing.gap8)) {
            MonoLabel(
                stringResource(R.string.kids_home_rules, parent),
                modifier = Modifier.padding(start = 4.dp),
            )
            if (state.rules.isEmpty()) {
                Text(
                    stringResource(R.string.kids_home_no_rules, parent),
                    style = type.bodyS,
                    color = colors.secondary,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            state.rules.forEach { RuleRow(it) }
        }
        state.error?.let { error ->
            Text(
                stringResource(
                    if (error == UnlinkError.Network) R.string.kids_unlink_network else R.string.kids_unlink_failed,
                ),
                style = type.body,
                color = colors.warning,
                modifier = Modifier.testTag(KIDS_HOME_ERROR_TAG),
            )
        }
        GhostButton(
            text = stringResource(R.string.kids_home_unlink, parent),
            onClick = onUnlink,
            enabled = !state.working,
            loading = state.working,
            modifier = Modifier.fillMaxWidth().testTag(KIDS_HOME_UNLINK_TAG),
        )
    }
    SfBottomSheet(visible = state.confirming, onDismiss = onKeep) {
        Column(
            Modifier.fillMaxWidth().padding(StayFocusedTheme.spacing.gap22),
            verticalArrangement = Arrangement.spacedBy(StayFocusedTheme.spacing.gap16),
        ) {
            Text(stringResource(R.string.kids_unlink_title, parent), style = type.displayS, color = colors.text)
            Text(stringResource(R.string.kids_unlink_body, parent), style = type.body, color = colors.secondary)
            OutlineButton(
                text = stringResource(R.string.kids_unlink_confirm),
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth().testTag(KIDS_HOME_CONFIRM_TAG),
            )
            PrimaryButton(
                text = stringResource(R.string.kids_unlink_keep),
                onClick = onKeep,
                modifier = Modifier.fillMaxWidth().testTag(KIDS_HOME_KEEP_TAG),
            )
        }
    }
}

/** What the always-on notification says, so the child can see what the parent's phone sees of this one. */
@Composable
private fun NoticePreview(parent: String) {
    val colors = StayFocusedTheme.colors
    val type = StayFocusedTheme.type
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.track, StayFocusedTheme.shapes.card)
            .border(1.dp, colors.hairline06, StayFocusedTheme.shapes.card)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(18.dp).background(colors.accent, StayFocusedTheme.shapes.chip),
                contentAlignment = Alignment.Center,
            ) {
                Text("SF", style = type.labelS, color = colors.onAccent)
            }
            Text(stringResource(R.string.kids_home_notice_app), style = type.bodyS, color = colors.secondary)
        }
        Text(
            stringResource(R.string.kids_home_notice_title, parent),
            style = type.body.copy(fontWeight = FontWeight.Bold),
            color = colors.text,
        )
        Text(stringResource(R.string.kids_home_notice_body, parent), style = type.bodyS, color = colors.secondary)
    }
}

@Composable
private fun RuleRow(rule: KidsRule) {
    val colors = StayFocusedTheme.colors
    val type = StayFocusedTheme.type
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.panel, StayFocusedTheme.shapes.input)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(rule.name, style = type.bodyL.copy(fontWeight = FontWeight.Bold), color = colors.text)
        Text(ruleSummary(rule), style = type.bodyS, color = colors.secondary)
    }
}

@Composable
private fun ruleSummary(rule: KidsRule): String =
    when (rule.type) {
        KidsRuleType.Limit -> {
            stringResource(
                if (rule.hourly) R.string.kids_rule_limit_hourly else R.string.kids_rule_limit_daily,
                rule.limitMins ?: 0,
            )
        }

        KidsRuleType.Cycle -> {
            stringResource(R.string.kids_rule_cycle, rule.useMins ?: 0, rule.restMins ?: 0)
        }

        KidsRuleType.Schedule -> {
            stringResource(R.string.kids_rule_schedule, rule.start.orEmpty(), rule.end.orEmpty())
        }

        KidsRuleType.Now -> {
            stringResource(R.string.kids_rule_now, rule.durationMins ?: 0)
        }
    }

/** "1h 12m", or "48m" under an hour. */
@Composable
internal fun duration(mins: Int): String =
    if (mins >= 60) {
        stringResource(R.string.kids_duration_hm, mins / 60, mins % 60)
    } else {
        stringResource(R.string.kids_duration_m, mins)
    }

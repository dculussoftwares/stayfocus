package com.dculus.stayfocused.kids

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dculus.stayfocused.core.model.DaysOfWeek
import com.dculus.stayfocused.core.sync.KidsRule
import com.dculus.stayfocused.core.sync.KidsRuleType
import com.dculus.stayfocused.core.ui.components.DotMatrixText
import com.dculus.stayfocused.core.ui.components.GlowCard
import com.dculus.stayfocused.core.ui.components.PillButton
import com.dculus.stayfocused.core.ui.components.PillButtonSize
import com.dculus.stayfocused.core.ui.components.PillButtonVariant
import com.dculus.stayfocused.core.ui.components.V5BottomSheet
import com.dculus.stayfocused.core.ui.components.V5Card
import com.dculus.stayfocused.core.ui.components.daysLabel
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.dculus.stayfocused.core.ui.theme.V5Gradients
import com.dculus.stayfocused.core.ui.theme.V5Screen
import com.dculus.stayfocused.core.ui.theme.v5NavigationBarPadding
import com.dculus.stayfocused.core.ui.theme.v5StatusBarPadding

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
    V5Screen(darkContent = false, modifier = modifier) {
        val colors = StayFocusedTheme.colors
        val type = StayFocusedTheme.type
        val parent = state.parentName ?: stringResource(R.string.kids_parent_fallback)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .v5StatusBarPadding(extra = 14.dp)
                .v5NavigationBarPadding(extra = 24.dp)
                .padding(horizontal = StayFocusedTheme.spacing.gap14),
            verticalArrangement = Arrangement.spacedBy(StayFocusedTheme.spacing.gap14),
        ) {
            TodayHero(state.todayMins)
            NoticePreview(parent)
            Text(
                stringResource(R.string.kids_home_notice_caption).uppercase(),
                style = type.tag,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = StayFocusedTheme.spacing.gap6),
            )
            Text(
                stringResource(R.string.kids_home_rules, parent).uppercase(),
                style = type.label,
                color = colors.textSecondary,
                modifier = Modifier.padding(start = StayFocusedTheme.spacing.gap6, top = StayFocusedTheme.spacing.gap6),
            )
            if (state.rules.isEmpty()) {
                Text(
                    stringResource(R.string.kids_home_no_rules, parent),
                    style = type.body,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(start = StayFocusedTheme.spacing.gap6),
                )
            }
            state.rules.forEach { RuleRow(it) }
            state.error?.let { error ->
                Text(
                    stringResource(
                        if (error == UnlinkError.Network) R.string.kids_unlink_network else R.string.kids_unlink_failed,
                    ),
                    style = type.body,
                    color = colors.error,
                    modifier = Modifier.testTag(KIDS_HOME_ERROR_TAG),
                )
            }
            PillButton(
                text = stringResource(R.string.kids_home_unlink, parent),
                onClick = onUnlink,
                variant = PillButtonVariant.Secondary,
                size = PillButtonSize.Medium,
                enabled = !state.working,
                loading = state.working,
                modifier = Modifier.fillMaxWidth().testTag(KIDS_HOME_UNLINK_TAG),
            )
        }
        V5BottomSheet(visible = state.confirming, onDismiss = onKeep) {
            Text(stringResource(R.string.kids_unlink_title, parent), style = type.heading, color = colors.ink)
            Text(stringResource(R.string.kids_unlink_body, parent), style = type.body, color = colors.textSecondary)
            PillButton(
                text = stringResource(R.string.kids_unlink_confirm),
                onClick = onConfirm,
                variant = PillButtonVariant.Secondary,
                size = PillButtonSize.Sheet,
                modifier = Modifier.fillMaxWidth().testTag(KIDS_HOME_CONFIRM_TAG),
            )
            PillButton(
                text = stringResource(R.string.kids_unlink_keep),
                onClick = onKeep,
                size = PillButtonSize.Sheet,
                modifier = Modifier.fillMaxWidth().testTag(KIDS_HOME_KEEP_TAG),
            )
        }
    }
}

/** Pink hero with today's total as dot-matrix `h:mm`; TalkBack reads the same words as before ("1h 12m"). */
@Composable
private fun TodayHero(todayMins: Int?) {
    GlowCard(
        gradient = V5Gradients.pinkHero,
        modifier = Modifier.fillMaxWidth(),
        radius = 38.dp,
        padding = 22.dp,
    ) {
        Text(
            stringResource(R.string.kids_home_today).uppercase(),
            style = StayFocusedTheme.type.label,
            color = StayFocusedTheme.colors.onGradient,
        )
        if (todayMins != null) {
            DotMatrixText(
                text = "${todayMins / 60}:${(todayMins % 60).toString().padStart(2, '0')}",
                contentDescription = duration(todayMins),
                dotSize = 9.dp,
                color = StayFocusedTheme.colors.onGradient,
                modifier = Modifier.padding(top = 16.dp).testTag(KIDS_HOME_TODAY_TAG),
            )
        }
    }
}

/** What the always-on notification says, so the child can see what the parent's phone sees of this one. */
@Composable
private fun NoticePreview(parent: String) {
    val colors = StayFocusedTheme.colors
    val type = StayFocusedTheme.type
    V5Card(Modifier.fillMaxWidth(), padding = 16.dp) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(StayFocusedTheme.spacing.gap8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(18.dp).background(colors.pink, StayFocusedTheme.shapes.tag),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.kids_home_notice_badge), style = type.tag, color = colors.onGradient)
            }
            Text(stringResource(R.string.kids_home_notice_app), style = type.body, color = colors.textSecondary)
        }
        Text(
            stringResource(R.string.kids_home_notice_title, parent),
            style = type.cardTitle,
            color = colors.ink,
            modifier = Modifier.padding(top = StayFocusedTheme.spacing.gap6),
        )
        Text(
            stringResource(R.string.kids_home_notice_body, parent),
            style = type.body,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = StayFocusedTheme.spacing.gap4),
        )
    }
}

/** A rule the parent set, read-only: name and summary in a compact card. */
@Composable
private fun RuleRow(rule: KidsRule) {
    V5Card(Modifier.fillMaxWidth(), padding = 16.dp) {
        Text(rule.name, style = StayFocusedTheme.type.cardTitle, color = StayFocusedTheme.colors.ink)
        Text(
            ruleSummary(rule),
            style = StayFocusedTheme.type.body,
            color = StayFocusedTheme.colors.textSecondary,
            modifier = Modifier.padding(top = StayFocusedTheme.spacing.gap4),
        )
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
            stringResource(
                R.string.kids_rule_schedule,
                daysLabel(DaysOfWeek(rule.days)),
                rule.start.orEmpty(),
                rule.end.orEmpty(),
            )
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

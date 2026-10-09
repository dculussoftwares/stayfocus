@file:Suppress("MagicNumber", "LongMethod", "LongParameterList", "TooManyFunctions")

package com.dculus.stayfocused.feature.block

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dculus.stayfocused.core.model.BlockDraft
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.model.BlockType
import com.dculus.stayfocused.core.model.DialConfig
import com.dculus.stayfocused.core.model.DialConfigs
import com.dculus.stayfocused.core.model.LimitPeriod
import com.dculus.stayfocused.core.model.TimeRange
import com.dculus.stayfocused.core.model.durationLabel
import com.dculus.stayfocused.core.model.formatMinutes
import com.dculus.stayfocused.core.ui.components.DayChips
import com.dculus.stayfocused.core.ui.components.DialPresets
import com.dculus.stayfocused.core.ui.components.MonoLabel
import com.dculus.stayfocused.core.ui.components.PrimaryButton
import com.dculus.stayfocused.core.ui.components.RotaryDial
import com.dculus.stayfocused.core.ui.components.SegmentedTabs
import com.dculus.stayfocused.core.ui.icon.AppIcon
import com.dculus.stayfocused.core.ui.theme.StayFocusedColors
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import java.time.DayOfWeek

const val WIZARD_BACK_TAG = "wizard_back"
const val WIZARD_CTA_TAG = "wizard_cta"
const val WIZARD_DESCRIBE_TAG = "wizard_describe"
const val WIZARD_RULES_TAG = "wizard_rules"
const val WIZARD_SUMMARY_TAG = "wizard_summary"
const val WIZARD_AI_BANNER_TAG = "wizard_ai_banner"
const val WIZARD_RANGE_CUSTOM_TAG = "wizard_range_custom"

fun wizardRangeTag(range: String): String = "wizard_range_$range"

fun wizardTypeTag(type: BlockType): String = "wizard_type_${type.name.lowercase()}"

fun wizardAppTag(pkg: String): String = "wizard_app_$pkg"

/** Callbacks of the wizard; grouped so the screen stays a stateless function of [WizardUiState]. */
data class WizardActions(
    val onBack: () -> Unit = {},
    val onPickType: (BlockType) -> Unit = {},
    val onToggleApp: (String) -> Unit = {},
    val onNext: () -> Unit = {},
    val onDescribe: () -> Unit = {},
    val onSelectTarget: (BlockTarget) -> Unit = {},
    val onPeriod: (LimitPeriod) -> Unit = {},
    val onMins: (Int) -> Unit = {},
    val onNow: (Int) -> Unit = {},
    val onCycleValue: (Int) -> Unit = {},
    val onCycleEdit: (CycleEdit) -> Unit = {},
    val onRange: (TimeRange) -> Unit = {},
    val onToggleDay: (DayOfWeek) -> Unit = {},
    val onChangeType: () -> Unit = {},
)

private val WizardTypes = BlockType.entries

private fun BlockType.chipColor(c: StayFocusedColors): Color =
    when (this) {
        BlockType.LIMIT -> c.appTints[0]
        BlockType.CYCLE -> c.accent
        BlockType.SCHEDULE -> c.appTints[6]
        BlockType.NOW -> c.warning
    }

private fun BlockType.chipRes(): Int =
    when (this) {
        BlockType.LIMIT -> R.string.block_template_code_limit
        BlockType.CYCLE -> R.string.block_template_code_cycle
        BlockType.SCHEDULE -> R.string.block_template_code_schedule
        BlockType.NOW -> R.string.block_wizard_chip_now
    }

private fun BlockType.nameRes(): Int =
    when (this) {
        BlockType.LIMIT -> R.string.block_wizard_type_limit_name
        BlockType.CYCLE -> R.string.block_wizard_type_cycle_name
        BlockType.SCHEDULE -> R.string.block_wizard_type_schedule_name
        BlockType.NOW -> R.string.block_wizard_type_now_name
    }

private fun BlockType.descRes(): Int =
    when (this) {
        BlockType.LIMIT -> R.string.block_wizard_type_limit_desc
        BlockType.CYCLE -> R.string.block_wizard_type_cycle_desc
        BlockType.SCHEDULE -> R.string.block_wizard_type_schedule_desc
        BlockType.NOW -> R.string.block_wizard_type_now_desc
    }

private fun BlockType.step3TitleRes(): Int =
    when (this) {
        BlockType.LIMIT -> R.string.block_wizard_step3_limit
        BlockType.CYCLE -> R.string.block_wizard_step3_cycle
        BlockType.SCHEDULE -> R.string.block_wizard_step3_schedule
        BlockType.NOW -> R.string.block_wizard_step3_now
    }

@Composable
fun WizardScreen(
    state: WizardUiState,
    actions: WizardActions,
    modifier: Modifier = Modifier,
) {
    val c = StayFocusedTheme.colors
    val spacing = StayFocusedTheme.spacing
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(c.background)
                .padding(start = spacing.screen, end = spacing.screen, top = 10.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.screen),
    ) {
        WizardHeader(state.step, actions.onBack)
        TargetPills(state.targets, state.draft.target, actions.onSelectTarget)
        Box(Modifier.weight(1f)) {
            if (state.ready) {
                when (state.step) {
                    WIZARD_STEP_TYPE -> TypeStep(state.draft.type, state.aiAvailable, actions)
                    WIZARD_STEP_APPS -> AppsStep(state, actions.onToggleApp)
                    else -> RulesStep(state, actions)
                }
            }
        }
        PrimaryButton(
            text = ctaLabel(state),
            onClick = actions.onNext,
            modifier = Modifier.fillMaxWidth().testTag(WIZARD_CTA_TAG),
            enabled = state.ready,
        )
    }
}

@Composable
private fun ctaLabel(state: WizardUiState): String =
    when (state.step) {
        WIZARD_STEP_RULES -> {
            stringResource(R.string.block_wizard_cta_turn_on)
        }

        WIZARD_STEP_APPS -> {
            pluralStringResource(R.plurals.block_wizard_cta_continue_apps, state.selectedCount, state.selectedCount)
        }

        else -> {
            stringResource(R.string.block_wizard_cta_continue)
        }
    }

@Composable
private fun WizardHeader(
    step: Int,
    onBack: () -> Unit,
) {
    val c = StayFocusedTheme.colors
    val backLabel = stringResource(R.string.block_wizard_back)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        val shape = RoundedCornerShape(14.dp)
        Box(
            modifier =
                Modifier
                    .size(42.dp)
                    .clip(shape)
                    .background(c.panel)
                    .border(1.dp, c.hairline10, shape)
                    .testTag(WIZARD_BACK_TAG)
                    .clickable(role = Role.Button, onClick = onBack)
                    .semantics { contentDescription = backLabel },
            contentAlignment = Alignment.Center,
        ) {
            Text("←", style = StayFocusedTheme.type.title, color = c.text)
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in WIZARD_STEP_TYPE..WIZARD_STEP_RULES) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i <= step) c.accent else c.panel),
                )
            }
        }
        Row {
            Text(
                stringResource(R.string.block_wizard_step, step),
                style = StayFocusedTheme.type.label,
                color = c.accent,
            )
            Text(
                stringResource(R.string.block_wizard_step_total),
                style = StayFocusedTheme.type.label,
                color = c.tertiary,
            )
        }
    }
}

@Composable
private fun TargetPills(
    targets: List<WizardTargetUi>,
    selected: BlockTarget,
    onSelect: (BlockTarget) -> Unit,
) {
    val c = StayFocusedTheme.colors
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonoLabel(stringResource(R.string.block_wizard_on), Modifier.padding(end = 2.dp))
        targets.forEach { t ->
            val on = t.target == selected
            val shape = RoundedCornerShape(10.dp)
            Text(
                text = t.device?.name ?: stringResource(R.string.block_this_phone),
                style = StayFocusedTheme.type.bodyS,
                color = if (on) c.onAccent else c.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .height(32.dp)
                        .widthIn(max = 160.dp)
                        .alpha(if (t.enabled) 1f else 0.4f)
                        .clip(shape)
                        .background(if (on) c.text else Color.Transparent)
                        .border(1.dp, if (on) c.text else c.hairline14, shape)
                        .selectable(
                            selected = on,
                            enabled = t.enabled,
                            role = Role.RadioButton,
                            onClick = { onSelect(t.target) },
                        ).padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun TypeStep(
    selected: BlockType,
    aiAvailable: Boolean,
    actions: WizardActions,
) {
    val c = StayFocusedTheme.colors
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(
                stringResource(R.string.block_wizard_step1_title),
                style = StayFocusedTheme.type.display,
                color = c.text,
            )
        }
        if (aiAvailable) {
            item {
                val shape = RoundedCornerShape(16.dp)
                Row(
                    modifier =
                        Modifier
                            .padding(bottom = 4.dp)
                            .fillMaxWidth()
                            .clip(shape)
                            .background(c.accent.copy(alpha = 0.06f))
                            .border(1.dp, c.accent.copy(alpha = 0.4f), shape)
                            .testTag(WIZARD_DESCRIBE_TAG)
                            .clickable(role = Role.Button, onClick = actions.onDescribe)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.block_ai_badge),
                        style = StayFocusedTheme.type.labelS,
                        color = c.onAccent,
                        modifier =
                            Modifier
                                .clip(
                                    RoundedCornerShape(5.dp),
                                ).background(c.accent)
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                    Text(
                        stringResource(R.string.block_wizard_describe),
                        style = StayFocusedTheme.type.body,
                        color = c.text,
                        modifier = Modifier.weight(1f),
                    )
                    Text("→", color = c.accent)
                }
            }
        }
        items(WizardTypes, key = { it.name }) { type ->
            TypeCard(type, selected = type == selected) { actions.onPickType(type) }
        }
    }
}

@Composable
private fun TypeCard(
    type: BlockType,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = StayFocusedTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(c.panel)
                .border(BorderStroke(1.5.dp, if (selected) c.accent else c.hairline06), shape)
                .testTag(wizardTypeTag(type))
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(type.chipColor(c)),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(type.chipRes()), style = StayFocusedTheme.type.labelS, color = c.onAccent)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(type.nameRes()), style = StayFocusedTheme.type.title, color = c.text)
            Text(stringResource(type.descRes()), style = StayFocusedTheme.type.bodyS, color = c.secondary)
        }
        Text("→", color = c.tertiary)
    }
}

@Composable
private fun AppsStep(
    state: WizardUiState,
    onToggle: (String) -> Unit,
) {
    val c = StayFocusedTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                stringResource(R.string.block_wizard_step2_title),
                style = StayFocusedTheme.type.display,
                color = c.text,
            )
            Text(
                stringResource(R.string.block_wizard_selected, state.selectedCount).uppercase(),
                style = StayFocusedTheme.type.label,
                color = c.accent,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.apps, key = { it.pkg }) { app ->
                AppTile(app, selected = app.pkg in state.draft.apps) { onToggle(app.pkg) }
            }
        }
    }
}

@Composable
private fun AppTile(
    app: TargetApp,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val c = StayFocusedTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier =
            Modifier
                .clip(shape)
                .background(if (selected) c.accent.copy(alpha = 0.1f) else c.panel)
                .border(1.5.dp, if (selected) c.accent else c.hairline06, shape)
                .testTag(wizardAppTag(app.pkg))
                .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() }),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 14.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppIcon(app.pkg, label = app.label, size = 44.dp)
            Text(
                app.label,
                style = StayFocusedTheme.type.bodyS,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(formatMinutes(app.todayMins), style = StayFocusedTheme.type.labelS, color = c.secondary)
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(16.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (selected) c.accent else Color.Transparent)
                    .border(1.dp, if (selected) c.accent else c.hairline14, RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Text("✓", color = c.onAccent, style = StayFocusedTheme.type.labelS)
        }
    }
}

private val RangePresets =
    listOf(
        "09:00–17:00" to R.string.block_wizard_range_work,
        "16:00–20:00" to R.string.block_wizard_range_study,
        "22:00–07:00" to R.string.block_wizard_range_night,
    )

/** Step 3: the rules of the chosen type, the day chips and the plain-language summary. */
@Composable
private fun RulesStep(
    state: WizardUiState,
    actions: WizardActions,
) {
    val c = StayFocusedTheme.colors
    val draft = state.draft
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag(WIZARD_RULES_TAG),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(if (draft.fromAi) R.string.block_wizard_step3_ai else draft.type.step3TitleRes()),
            style = StayFocusedTheme.type.displayL,
            color = c.text,
        )
        if (draft.fromAi) AiDraftBanner(draft, actions.onChangeType)
        when (draft.type) {
            BlockType.LIMIT -> {
                SegmentedTabs(
                    options =
                        listOf(
                            stringResource(R.string.block_wizard_period_day),
                            stringResource(R.string.block_wizard_period_hour),
                        ),
                    selectedIndex = if (draft.period == LimitPeriod.DAILY) 0 else 1,
                    onSelect = { actions.onPeriod(if (it == 0) LimitPeriod.DAILY else LimitPeriod.HOURLY) },
                )
                DialWithPresets(draft.mins, DialConfigs.Limit, actions.onMins)
            }

            BlockType.CYCLE -> {
                CycleTabs(draft, state.cycleEdit, actions.onCycleEdit)
                if (state.cycleEdit == CycleEdit.USE) {
                    DialWithPresets(draft.use, DialConfigs.CycleUse, actions.onCycleValue)
                } else {
                    DialWithPresets(draft.rest, DialConfigs.CycleRest, actions.onCycleValue)
                }
            }

            BlockType.SCHEDULE -> {
                RangeOptions(draft.range, actions.onRange)
            }

            BlockType.NOW -> {
                DialWithPresets(draft.now, DialConfigs.BlockNow, actions.onNow)
            }
        }
        if (draft.type != BlockType.NOW) {
            DayChips(
                selected = DayOfWeek.entries.filterTo(hashSetOf()) { it in draft.days },
                onToggle = actions.onToggleDay,
            )
        }
        Text(
            state.summary,
            style = StayFocusedTheme.type.body,
            color = c.text,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .dashedBorder(c.hairline14, 16.dp)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .testTag(WIZARD_SUMMARY_TAG),
        )
    }
}

/** The "AI DRAFT" banner above the rules; filled by the AI flow (M9-05) through `BlockDraft.fromAi` / `aiNote`. */
@Composable
private fun AiDraftBanner(
    draft: BlockDraft,
    onChange: () -> Unit,
) {
    val c = StayFocusedTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(c.accent.copy(alpha = 0.08f))
                .border(1.dp, c.accent.copy(alpha = 0.3f), shape)
                .padding(14.dp)
                .testTag(WIZARD_AI_BANNER_TAG),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.block_wizard_ai_draft),
                style = StayFocusedTheme.type.labelS,
                color = c.onAccent,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(c.accent)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
            )
            Text(stringResource(draft.type.nameRes()), style = StayFocusedTheme.type.bodyS, color = c.text)
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.block_wizard_ai_change),
                style = StayFocusedTheme.type.bodyS,
                color = c.accent,
                modifier = Modifier.clickable(role = Role.Button, onClick = onChange).padding(vertical = 8.dp),
            )
        }
        Text(
            draft.aiNote.ifBlank { stringResource(R.string.block_wizard_ai_note_default) },
            style = StayFocusedTheme.type.bodyS,
            color = c.secondary,
        )
    }
}

@Composable
private fun CycleTabs(
    draft: BlockDraft,
    edit: CycleEdit,
    onSelect: (CycleEdit) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CycleTab(
            label = stringResource(R.string.block_wizard_cycle_use),
            mins = draft.use,
            selected = edit == CycleEdit.USE,
            onClick = { onSelect(CycleEdit.USE) },
            modifier = Modifier.weight(1f),
        )
        CycleTab(
            label = stringResource(R.string.block_wizard_cycle_rest),
            mins = draft.rest,
            selected = edit == CycleEdit.REST,
            onClick = { onSelect(CycleEdit.REST) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CycleTab(
    label: String,
    mins: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = StayFocusedTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier =
            modifier
                .clip(shape)
                .background(if (selected) c.accent.copy(alpha = 0.1f) else c.panel)
                .border(1.5.dp, if (selected) c.accent else c.hairline06, shape)
                .selectable(selected = selected, role = Role.Tab, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = StayFocusedTheme.type.labelS, color = if (selected) c.accent else c.secondary)
        Text(durationLabel(mins), style = StayFocusedTheme.type.title, color = if (selected) c.accent else c.text)
    }
}

@Composable
private fun DialWithPresets(
    value: Int,
    config: DialConfig,
    onChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RotaryDial(value = value, onValueChange = onChange, config = config)
        DialPresets(config = config, value = value, onPick = onChange)
    }
}

@Composable
private fun RangeOptions(
    range: TimeRange,
    onRange: (TimeRange) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    val formatted = range.format()
    val custom = RangePresets.none { it.first == formatted }
    val customText = stringResource(R.string.block_wizard_range_custom)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RangePresets.forEach { (text, hintRes) ->
            RangeRow(text, stringResource(hintRes), selected = text == formatted, tag = wizardRangeTag(text)) {
                onRange(TimeRange.parse(text))
            }
        }
        RangeRow(
            label = if (custom) formatted else customText,
            hint = if (custom) customText else stringResource(R.string.block_wizard_range_pick),
            selected = custom,
            tag = WIZARD_RANGE_CUSTOM_TAG,
        ) { picking = true }
    }
    if (picking) {
        TimeRangePickerDialog(
            initial = range,
            onDismiss = { picking = false },
            onConfirm = {
                picking = false
                onRange(it)
            },
        )
    }
}

@Composable
private fun RangeRow(
    label: String,
    hint: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    val c = StayFocusedTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(shape)
                .background(if (selected) c.accent else c.panel)
                .border(1.5.dp, if (selected) c.accent else c.hairline06, shape)
                .testTag(tag)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = StayFocusedTheme.type.title, color = if (selected) c.onAccent else c.text)
        Text(hint, style = StayFocusedTheme.type.bodyS, color = if (selected) c.onAccent else c.secondary)
    }
}

private fun Modifier.dashedBorder(
    color: Color,
    radius: Dp,
): Modifier =
    drawBehind {
        val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
        drawRoundRect(
            color = color,
            cornerRadius = CornerRadius(radius.toPx()),
            style = Stroke(width = 1.dp.toPx(), pathEffect = dash),
        )
    }

@file:Suppress("MagicNumber", "LongMethod", "LongParameterList", "TooManyFunctions")

package com.dculus.stayfocused.feature.block

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.dculus.stayfocused.core.model.BlockDraft
import com.dculus.stayfocused.core.model.BlockType
import com.dculus.stayfocused.core.model.CycleScope
import com.dculus.stayfocused.core.model.DaysOfWeek
import com.dculus.stayfocused.core.model.LimitPeriod
import com.dculus.stayfocused.core.model.RulerReadout
import com.dculus.stayfocused.core.model.RulerScale
import com.dculus.stayfocused.core.model.RulerScales
import com.dculus.stayfocused.core.model.TimeRange
import com.dculus.stayfocused.core.model.rulerReadout
import com.dculus.stayfocused.core.model.v5Duration
import com.dculus.stayfocused.core.sync.AiReport
import com.dculus.stayfocused.core.ui.components.AppIconTile
import com.dculus.stayfocused.core.ui.components.DayChips
import com.dculus.stayfocused.core.ui.components.GlowCard
import com.dculus.stayfocused.core.ui.components.PillButton
import com.dculus.stayfocused.core.ui.components.PillButtonVariant
import com.dculus.stayfocused.core.ui.components.RoundIconButton
import com.dculus.stayfocused.core.ui.components.RulerLabels
import com.dculus.stayfocused.core.ui.components.SegmentedPill
import com.dculus.stayfocused.core.ui.components.SegmentedPillSize
import com.dculus.stayfocused.core.ui.components.SegmentedPillStyle
import com.dculus.stayfocused.core.ui.components.StatusTag
import com.dculus.stayfocused.core.ui.components.StatusTagKind
import com.dculus.stayfocused.core.ui.components.TargetTag
import com.dculus.stayfocused.core.ui.components.TickRuler
import com.dculus.stayfocused.core.ui.components.TickRulerColors
import com.dculus.stayfocused.core.ui.components.V5BottomSheet
import com.dculus.stayfocused.core.ui.components.V5Card
import com.dculus.stayfocused.core.ui.icons.V5Icons
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.dculus.stayfocused.core.ui.theme.V5Gradient
import com.dculus.stayfocused.core.ui.theme.V5Gradients
import com.dculus.stayfocused.core.ui.theme.V5Screen
import com.dculus.stayfocused.core.ui.theme.v5NavigationBarPadding
import com.dculus.stayfocused.core.ui.theme.v5StatusBarPadding
import java.time.DayOfWeek

const val WIZARD_BACK_TAG = "wizard_back"
const val WIZARD_CLOSE_TAG = "wizard_close"
const val WIZARD_TARGET_TAG = "wizard_target"
const val WIZARD_CTA_TAG = "wizard_cta"
const val WIZARD_DESCRIBE_TAG = "wizard_describe"
const val WIZARD_RULES_TAG = "wizard_rules"
const val WIZARD_SUMMARY_TAG = "wizard_summary"
const val WIZARD_AI_BANNER_TAG = "wizard_ai_banner"
const val WIZARD_RULER_TAG = "wizard_ruler"
const val WIZARD_RANGE_TEXT_TAG = "wizard_range_text"
const val WIZARD_RANGE_CUSTOM_TAG = "wizard_range_custom"
const val WIZARD_DELETE_TAG = "wizard_delete"
const val WIZARD_DELETE_CONFIRM_TAG = "wizard_delete_confirm"
const val WIZARD_DELETE_KEEP_TAG = "wizard_delete_keep"
const val WIZARD_APPS_ROW_TAG = "wizard_apps_row"
const val WIZARD_DISCARD_TAG = "wizard_discard"
const val WIZARD_KEEP_EDITING_TAG = "wizard_keep_editing"
const val WIZARD_REPORT_TAG = "wizard_report"
const val WIZARD_REPORT_CONFIRM_TAG = "wizard_report_confirm"
const val WIZARD_REPORT_SENTENCE_TAG = "wizard_report_sentence"
const val WIZARD_REPORT_DRAFT_TAG = "wizard_report_draft"

fun wizardPresetTag(preset: SchedulePreset): String = "wizard_preset_${preset.name.lowercase()}"

fun wizardTypeTag(type: BlockType): String = "wizard_type_${type.name.lowercase()}"

fun wizardAppTag(pkg: String): String = "wizard_app_$pkg"

/** Callbacks of the wizard; grouped so the screen stays a stateless function of [WizardUiState]. */
data class WizardActions(
    val onBack: () -> Unit = {},
    val onClose: () -> Unit = {},
    val onPickType: (BlockType) -> Unit = {},
    val onToggleApp: (String) -> Unit = {},
    val onNext: () -> Unit = {},
    val onDescribe: () -> Unit = {},
    val onFlipTarget: () -> Unit = {},
    val onPeriod: (LimitPeriod) -> Unit = {},
    val onMins: (Int) -> Unit = {},
    val onNow: (Int) -> Unit = {},
    val onPreset: (SchedulePreset) -> Unit = {},
    val onDays: (DaysOfWeek) -> Unit = {},
    val onCycleValue: (Int) -> Unit = {},
    val onCycleEdit: (CycleEdit) -> Unit = {},
    val onCycleScope: (CycleScope) -> Unit = {},
    val onRange: (TimeRange) -> Unit = {},
    val onChangeType: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onEditApps: () -> Unit = {},
    val onKeepEditing: () -> Unit = {},
    val onDiscard: () -> Unit = {},
    val onReport: () -> Unit = {},
)

private val WizardTypes = BlockType.entries
private val ScreenPadding = 14.dp
private val TypeTileHeight = 178.dp
private val TypeTileRadius = 32.dp
private val AppTileRadius = 28.dp

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

/** The block wizard (v5, V5_SPEC §5). */
@Composable
fun WizardScreen(
    state: WizardUiState,
    actions: WizardActions,
    modifier: Modifier = Modifier,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmReport by remember { mutableStateOf(false) }
    V5Screen(darkContent = false, modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .v5StatusBarPadding(extra = 10.dp)
                    .v5NavigationBarPadding(),
            verticalArrangement = Arrangement.spacedBy(StayFocusedTheme.spacing.gap12),
        ) {
            WizardTopBar(actions.onBack, actions.onClose)
            WizardTitle(state, actions.onFlipTarget)
            if (state.step == WIZARD_STEP_RULES && state.draft.fromAi) {
                AiDraftBanner(state.draft, actions.onChangeType, onReport = { confirmReport = true })
            }
            Box(Modifier.weight(1f)) {
                if (state.ready) {
                    when (state.step) {
                        WIZARD_STEP_TYPE -> TypeStep(state, actions)
                        WIZARD_STEP_APPS -> AppsStep(state, actions)
                        else -> RulesStep(state, actions, onDelete = { confirmDelete = true })
                    }
                }
            }
        }
    }
    V5BottomSheet(visible = state.confirmDiscard, onDismiss = actions.onKeepEditing) {
        DiscardSheetContent(onDiscard = actions.onDiscard, onKeep = actions.onKeepEditing)
    }
    V5BottomSheet(visible = confirmDelete, onDismiss = { confirmDelete = false }) {
        DeleteSheetContent(
            onDelete = {
                // A second tap while the sheet slides out must not delete twice.
                if (confirmDelete) {
                    confirmDelete = false
                    actions.onDelete()
                }
            },
            onKeep = { confirmDelete = false },
        )
    }
    V5BottomSheet(visible = confirmReport, onDismiss = { confirmReport = false }) {
        ReportSheetContent(
            report = AiReport(state.aiSentence, state.aiOriginal ?: state.draft),
            sending = state.reporting,
            onSend = {
                // A second tap while the sheet slides out must not send twice.
                if (confirmReport) {
                    confirmReport = false
                    actions.onReport()
                }
            },
            onCancel = { confirmReport = false },
        )
    }
}

/** Confirm sheet of "Report a bad result": shows exactly what is sent (the sentence and the draft fields). */
@Composable
private fun ColumnScope.ReportSheetContent(
    report: AiReport,
    sending: Boolean,
    onSend: () -> Unit,
    onCancel: () -> Unit,
) {
    val c = StayFocusedTheme.colors
    Text(stringResource(R.string.block_report_title), style = StayFocusedTheme.type.heading, color = c.ink)
    Text(stringResource(R.string.block_report_body), style = StayFocusedTheme.type.body, color = c.textSecondary)
    // Scrolls on its own so a long sentence never pushes "Send report" off the screen.
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.block_report_sentence_label),
            style = StayFocusedTheme.type.label,
            color = c.textSecondary,
        )
        Text(
            report.sentence,
            style = StayFocusedTheme.type.body,
            color = c.ink,
            modifier = Modifier.testTag(WIZARD_REPORT_SENTENCE_TAG),
        )
        Text(
            stringResource(R.string.block_report_draft_label),
            style = StayFocusedTheme.type.label,
            color = c.textSecondary,
        )
        Text(
            report.draftFields.entries.joinToString("\n") { (key, value) -> "$key: $value" },
            style = StayFocusedTheme.type.body.copy(fontSize = 13.sp),
            color = c.ink,
            modifier = Modifier.testTag(WIZARD_REPORT_DRAFT_TAG),
        )
    }
    PillButton(
        text = stringResource(R.string.block_report_send),
        onClick = onSend,
        enabled = !sending,
        modifier = Modifier.fillMaxWidth().testTag(WIZARD_REPORT_CONFIRM_TAG),
    )
    PillButton(
        text = stringResource(R.string.block_report_cancel),
        onClick = onCancel,
        variant = PillButtonVariant.Text,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ColumnScope.DiscardSheetContent(
    onDiscard: () -> Unit,
    onKeep: () -> Unit,
) {
    Text(
        stringResource(R.string.block_discard_title),
        style = StayFocusedTheme.type.title,
        color = StayFocusedTheme.colors.ink,
    )
    Text(
        stringResource(R.string.block_discard_body),
        style = StayFocusedTheme.type.body,
        color = StayFocusedTheme.colors.textSecondary,
    )
    PillButton(
        text = stringResource(R.string.block_discard_confirm),
        onClick = onDiscard,
        modifier = Modifier.fillMaxWidth().testTag(WIZARD_DISCARD_TAG),
    )
    PillButton(
        text = stringResource(R.string.block_discard_keep),
        onClick = onKeep,
        variant = PillButtonVariant.Text,
        modifier = Modifier.fillMaxWidth().testTag(WIZARD_KEEP_EDITING_TAG),
    )
}

/** Edit mode only: the selected apps and a way back to the picker. */
@Composable
private fun AppsRow(
    state: WizardUiState,
    onClick: () -> Unit,
) {
    V5Card(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(WIZARD_APPS_ROW_TAG),
        radius = 30.dp,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.block_wizard_apps_row_title),
                    style = StayFocusedTheme.type.label.copy(fontSize = 15.sp),
                    color = StayFocusedTheme.colors.ink,
                )
                Text(
                    pluralStringResource(
                        R.plurals.block_wizard_apps_row_count,
                        state.selectedCount,
                        state.selectedCount,
                    ) +
                        " · " + state.appLabels.joinToString(),
                    style = StayFocusedTheme.type.body,
                    color = StayFocusedTheme.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                stringResource(R.string.block_wizard_ai_change),
                style = StayFocusedTheme.type.label,
                color = StayFocusedTheme.colors.ink,
            )
        }
    }
}

@Composable
private fun DeleteButton(onClick: () -> Unit) {
    PillButton(
        text = stringResource(R.string.block_delete_action),
        onClick = onClick,
        variant = PillButtonVariant.Text,
        textColor = StayFocusedTheme.colors.destructive,
        modifier = Modifier.fillMaxWidth().testTag(WIZARD_DELETE_TAG),
    )
}

@Composable
private fun ColumnScope.DeleteSheetContent(
    onDelete: () -> Unit,
    onKeep: () -> Unit,
) {
    Text(
        stringResource(R.string.block_delete_title),
        style = StayFocusedTheme.type.heading,
        color = StayFocusedTheme.colors.ink,
    )
    Text(
        stringResource(R.string.block_delete_body),
        style = StayFocusedTheme.type.body,
        color = StayFocusedTheme.colors.textSecondary,
    )
    PillButton(
        text = stringResource(R.string.block_delete_confirm),
        onClick = onDelete,
        modifier = Modifier.fillMaxWidth().testTag(WIZARD_DELETE_CONFIRM_TAG),
    )
    PillButton(
        text = stringResource(R.string.block_delete_keep),
        onClick = onKeep,
        variant = PillButtonVariant.Text,
        modifier = Modifier.fillMaxWidth().testTag(WIZARD_DELETE_KEEP_TAG),
    )
}

@Composable
private fun ctaLabel(state: WizardUiState): String =
    when (state.step) {
        WIZARD_STEP_RULES -> {
            stringResource(if (state.editing) R.string.block_wizard_cta_save else R.string.block_wizard_cta_turn_on)
        }

        WIZARD_STEP_APPS -> {
            if (state.selectedCount == 0) {
                stringResource(R.string.block_wizard_pick_one_app)
            } else {
                pluralStringResource(R.plurals.block_wizard_cta_continue_apps, state.selectedCount, state.selectedCount)
            }
        }

        else -> {
            stringResource(R.string.block_wizard_cta_continue)
        }
    }

/** Back (one step earlier, or close on step 1) and close, two 46 dp round buttons. */
@Composable
private fun WizardTopBar(
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(
            icon = V5Icons.back,
            contentDescription = stringResource(R.string.block_wizard_back),
            onClick = onBack,
            modifier = Modifier.testTag(WIZARD_BACK_TAG),
        )
        RoundIconButton(
            icon = V5Icons.close,
            contentDescription = stringResource(R.string.block_wizard_close),
            onClick = onClose,
            modifier = Modifier.testTag(WIZARD_CLOSE_TAG),
        )
    }
}

/** Step title with the red target tag, and the "Step n of 3" line. */
@Composable
private fun WizardTitle(
    state: WizardUiState,
    onFlipTarget: () -> Unit,
) {
    val c = StayFocusedTheme.colors
    val titleRes =
        when (state.step) {
            WIZARD_STEP_TYPE -> R.string.block_wizard_step1_title
            WIZARD_STEP_APPS -> R.string.block_wizard_step2_title
            else -> R.string.block_wizard_step3_title
        }
    val targetName =
        state.targets
            .firstOrNull { it.target == state.draft.target }
            ?.device
            ?.name
            ?: stringResource(R.string.block_my_phone)
    Column(
        modifier =
            Modifier.fillMaxWidth().padding(
                start = ScreenPadding + 6.dp,
                end = ScreenPadding + 6.dp,
                top = 4.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(titleRes),
                style = StayFocusedTheme.type.heading.copy(fontSize = 30.sp, letterSpacing = (-0.01).em),
                color = c.ink,
            )
            // An existing block cannot move to another phone, so its tag is only a label.
            TargetTag(
                name = targetName,
                modifier = Modifier.testTag(WIZARD_TARGET_TAG),
                onClick = if (state.editing) null else onFlipTarget,
            )
        }
        Text(
            stringResource(R.string.block_wizard_step_hint, state.step),
            style = StayFocusedTheme.type.body.copy(fontSize = 14.sp),
            color = c.textSecondary,
        )
    }
}

@Composable
private fun TypeStep(
    state: WizardUiState,
    actions: WizardActions,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // The selection ring is drawn 6 dp outside its tile: keep room for it inside the list's clip.
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 6.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(WizardTypes.chunked(2), key = { it.first().name }) { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { type ->
                    TypeTile(
                        type = type,
                        selected = type == state.draft.type,
                        onClick = { actions.onPickType(type) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item {
            PillButton(
                text = ctaLabel(state),
                onClick = actions.onNext,
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth().testTag(WIZARD_CTA_TAG),
                enabled = state.ready,
            )
        }
        if (state.aiAvailable) {
            item {
                val c = StayFocusedTheme.colors
                Row(
                    modifier =
                        Modifier
                            .padding(top = 2.dp)
                            .fillMaxWidth()
                            .heightIn(min = StayFocusedTheme.spacing.minTouchTarget)
                            .testTag(WIZARD_DESCRIBE_TAG)
                            .clickable(role = Role.Button, onClick = actions.onDescribe),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(V5Icons.sparkle, contentDescription = null, tint = c.green, modifier = Modifier.size(15.dp))
                    Text(
                        stringResource(R.string.block_wizard_describe),
                        style = StayFocusedTheme.type.label.copy(fontSize = 15.sp),
                        color = c.ink,
                    )
                }
            }
        }
    }
}

/** A 178 dp tile: white with ink text, or [V5Gradients.brand] with white text and a green shadow when selected. */
@Composable
private fun TypeTile(
    type: BlockType,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = StayFocusedTheme.colors
    val textColor = if (selected) c.onGradient else c.ink
    val tileModifier =
        Modifier
            .fillMaxSize()
            .testTag(wizardTypeTag(type))
            .semantics { this.selected = selected }
    val content: @Composable ColumnScope.() -> Unit = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier
                    .size(30.dp)
                    .background(if (selected) c.onGradient else Color.Transparent, CircleShape)
                    .border(2.dp, textColor, CircleShape),
            )
        }
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(type.nameRes()),
                style =
                    StayFocusedTheme.type.body.copy(
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 21.sp,
                    ),
                color = textColor,
            )
            Text(
                stringResource(type.descRes()),
                style =
                    StayFocusedTheme.type.body.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 17.sp,
                    ),
                color = textColor,
            )
        }
    }
    Box(
        modifier =
            modifier
                .height(TypeTileHeight)
                .then(
                    if (selected) {
                        Modifier.shadow(
                            14.dp,
                            RoundedCornerShape(TypeTileRadius),
                            clip = false,
                            ambientColor = c.green.copy(alpha = 0.6f),
                            spotColor = c.green.copy(alpha = 0.6f),
                        )
                    } else {
                        Modifier
                    },
                ),
    ) {
        if (selected) {
            GlowCard(
                V5Gradients.brand,
                tileModifier,
                radius = TypeTileRadius,
                padding = 16.dp,
                onClick = onClick,
                content = content,
            )
        } else {
            V5Card(tileModifier, radius = TypeTileRadius, padding = 16.dp, onClick = onClick, content = content)
        }
    }
}

@Composable
private fun AppsStep(
    state: WizardUiState,
    actions: WizardActions,
) {
    // The CTA floats over the grid so it stays reachable however far the list is scrolled.
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = AppsCtaClearance),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.apps.chunked(APP_COLUMNS), key = { it.first().pkg }) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { app ->
                        AppTile(
                            app = app,
                            selected = app.pkg in state.draft.apps,
                            onToggle = { actions.onToggleApp(app.pkg) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(APP_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        PillButton(
            text = ctaLabel(state),
            onClick = actions.onNext,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = ScreenPadding, vertical = 8.dp)
                    .fillMaxWidth()
                    .testTag(WIZARD_CTA_TAG),
            enabled = state.ready && state.selectedCount > 0,
        )
    }
}

/** Bottom padding of the apps grid so the last row scrolls clear of the floating CTA. */
private val AppsCtaClearance = 88.dp

private const val APP_COLUMNS = 3

/** A white 112 dp tile: 48 dp icon and name; a selected one has a 2 dp inner ink ring and a check badge. */
@Composable
private fun AppTile(
    app: TargetApp,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = StayFocusedTheme.colors
    val shape = RoundedCornerShape(AppTileRadius)
    Box(
        modifier =
            modifier
                .heightIn(min = 112.dp)
                .clip(shape)
                .background(c.surface)
                .then(if (selected) Modifier.border(2.dp, c.ink, shape) else Modifier)
                .testTag(wizardAppTag(app.pkg))
                .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() }),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppIconTile(app.pkg, size = 48.dp, label = app.label)
            Text(
                app.label,
                style = StayFocusedTheme.type.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        if (selected) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(22.dp)
                        .background(c.ink, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(V5Icons.checkBold, contentDescription = null, tint = c.onGradient, modifier = Modifier.size(12.dp))
            }
        }
    }
}

/** The "AI draft" banner above the rules (V5_SPEC §5); filled by the AI flow through `BlockDraft.fromAi` / `aiNote`. */
@Composable
private fun AiDraftBanner(
    draft: BlockDraft,
    onChange: () -> Unit,
    onReport: () -> Unit,
) {
    val c = StayFocusedTheme.colors
    Column(
        modifier =
            Modifier
                .padding(horizontal = ScreenPadding)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(c.surface)
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .testTag(WIZARD_AI_BANNER_TAG),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusTag(StatusTagKind.AiDraft)
            Text(
                stringResource(R.string.block_wizard_ai_banner),
                style = StayFocusedTheme.type.body.copy(fontSize = 14.sp),
                color = c.textTertiary,
                modifier = Modifier.weight(1f),
            )
        }
        if (draft.aiNote.isNotBlank()) {
            Text(draft.aiNote, style = StayFocusedTheme.type.body.copy(fontSize = 14.sp), color = c.textSecondary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BannerAction(stringResource(R.string.block_wizard_ai_change), onChange)
            BannerAction(stringResource(R.string.block_report_action), onReport, Modifier.testTag(WIZARD_REPORT_TAG))
        }
    }
}

@Composable
private fun BannerAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        style = StayFocusedTheme.type.label,
        color = StayFocusedTheme.colors.ink,
        modifier =
            modifier
                .heightIn(min = StayFocusedTheme.spacing.minTouchTarget)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = 14.dp),
    )
}

/** Step 3: the rule card of the chosen type, "Repeat on", the "Summary" card and the "Turn on block" button. */
@Composable
private fun RulesStep(
    state: WizardUiState,
    actions: WizardActions,
    onDelete: () -> Unit,
) {
    val draft = state.draft
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = ScreenPadding, end = ScreenPadding, bottom = 16.dp)
                .testTag(WIZARD_RULES_TAG),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.editing) AppsRow(state, onClick = actions.onEditApps)
        RuleCard(state, actions)
        // Per-hour limits: day / hour switch under the ruler card.
        if (draft.type == BlockType.LIMIT) {
            SegmentedPill(
                options =
                    listOf(
                        stringResource(R.string.block_wizard_period_day),
                        stringResource(R.string.block_wizard_period_hour),
                    ),
                selectedIndex = if (draft.period == LimitPeriod.DAILY) 0 else 1,
                onSelect = { actions.onPeriod(if (it == 0) LimitPeriod.DAILY else LimitPeriod.HOURLY) },
                size = SegmentedPillSize.Small,
            )
        }
        // "Use, then lock": every app gets its own window (default), or all apps of the block share one.
        if (draft.type == BlockType.CYCLE) {
            SegmentedPill(
                options =
                    listOf(
                        stringResource(R.string.block_wizard_scope_per_app),
                        stringResource(R.string.block_wizard_scope_total),
                    ),
                selectedIndex = if (draft.cycleScope == CycleScope.PER_APP) 0 else 1,
                onSelect = { actions.onCycleScope(if (it == 0) CycleScope.PER_APP else CycleScope.TOTAL) },
                size = SegmentedPillSize.Small,
            )
        }
        if (draft.type != BlockType.NOW) {
            V5Card(Modifier.fillMaxWidth(), radius = 30.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.block_wizard_repeat_on),
                        style = StayFocusedTheme.type.label.copy(fontSize = 15.sp),
                        color = StayFocusedTheme.colors.ink,
                    )
                    DayChips(
                        selected = DayOfWeek.entries.filterTo(hashSetOf()) { it in draft.days },
                        onSelectedChange = { set -> actions.onDays(DaysOfWeek.of(*set.toTypedArray())) },
                    )
                }
            }
        }
        V5Card(Modifier.fillMaxWidth(), radius = 30.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.block_wizard_summary),
                    style = StayFocusedTheme.type.label.copy(fontSize = 13.sp),
                    color = StayFocusedTheme.colors.textSecondary,
                )
                Text(
                    state.summary,
                    style = StayFocusedTheme.type.body.copy(fontSize = 19.sp, lineHeight = 26.sp),
                    color = StayFocusedTheme.colors.ink,
                    modifier = Modifier.testTag(WIZARD_SUMMARY_TAG),
                )
            }
        }
        PillButton(
            text = ctaLabel(state),
            onClick = actions.onNext,
            modifier = Modifier.fillMaxWidth().testTag(WIZARD_CTA_TAG),
            enabled = state.ready && !state.saving,
        )
        if (state.editing && !state.saving) DeleteButton(onClick = onDelete)
    }
}

@Composable
private fun RuleCard(
    state: WizardUiState,
    actions: WizardActions,
) {
    val draft = state.draft
    GlowCard(
        gradient = V5Gradients.brand,
        modifier = Modifier.fillMaxWidth(),
        radius = 36.dp,
        padding = 18.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (draft.type == BlockType.SCHEDULE) {
                SchedulePicker(draft.range, actions.onPreset, actions.onRange)
            } else {
                val cycleUse = state.cycleEdit == CycleEdit.USE
                if (draft.type == BlockType.CYCLE) {
                    SegmentedPill(
                        options =
                            listOf(
                                stringResource(R.string.block_wizard_seg_use, draft.use),
                                stringResource(R.string.block_wizard_seg_rest, v5Duration(draft.rest)),
                            ),
                        selectedIndex = if (cycleUse) 0 else 1,
                        onSelect = { actions.onCycleEdit(if (it == 0) CycleEdit.USE else CycleEdit.REST) },
                        style = SegmentedPillStyle.Glass,
                        size = SegmentedPillSize.Medium,
                    )
                }
                val onChange =
                    when (draft.type) {
                        BlockType.LIMIT -> actions.onMins
                        BlockType.NOW -> actions.onNow
                        else -> actions.onCycleValue
                    }
                RulerBlock(rulerSpec(draft, cycleUse), onChange)
            }
        }
    }
}

private class RulerSpec(
    val value: Int,
    val scale: RulerScale,
    @StringRes val sub: Int,
    @StringRes val description: Int,
)

private fun rulerSpec(
    draft: BlockDraft,
    cycleUse: Boolean,
): RulerSpec =
    when {
        draft.type == BlockType.LIMIT -> {
            val hourly = draft.period == LimitPeriod.HOURLY
            RulerSpec(
                draft.mins,
                RulerScales.Limit,
                if (hourly) R.string.block_wizard_sub_limit_hour else R.string.block_wizard_sub_limit,
                R.string.block_wizard_ruler_limit,
            )
        }

        draft.type == BlockType.NOW -> {
            RulerSpec(draft.now, RulerScales.Now, R.string.block_wizard_sub_now, R.string.block_wizard_ruler_now)
        }

        cycleUse -> {
            RulerSpec(draft.use, RulerScales.Use, R.string.block_wizard_sub_use, R.string.block_wizard_ruler_use)
        }

        else -> {
            RulerSpec(draft.rest, RulerScales.Rest, R.string.block_wizard_sub_rest, R.string.block_wizard_ruler_rest)
        }
    }

/** The big readout (92 sp, weight 200) with its unit and sub-label, and the ruler with its min / max labels. */
@Composable
private fun RulerBlock(
    spec: RulerSpec,
    onChange: (Int) -> Unit,
) {
    val readout = rulerReadout(spec.value)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        RulerReadoutText(readout, spec.sub)
        TickRuler(
            value = spec.value,
            onValueChange = onChange,
            scale = spec.scale,
            colors = TickRulerColors.onGradient(),
            contentDescription = stringResource(spec.description),
            valueDescription = v5Duration(spec.value),
            modifier = Modifier.testTag(WIZARD_RULER_TAG),
        )
        RulerLabels(min = spec.scale.min, max = spec.scale.max)
    }
}

@Composable
private fun RulerReadoutText(
    readout: RulerReadout,
    @StringRes sub: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                readout.big,
                style =
                    StayFocusedTheme.type.body.copy(
                        fontSize = 92.sp,
                        fontWeight = FontWeight.ExtraLight,
                        lineHeight = 88.sp,
                        letterSpacing = (-0.04).em,
                    ),
                color = StayFocusedTheme.colors.onGradient,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                readout.unit,
                style = StayFocusedTheme.type.body.copy(fontSize = 24.sp, fontWeight = FontWeight.Light),
                color = StayFocusedTheme.colors.onGradient,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Text(
            stringResource(sub),
            style = StayFocusedTheme.type.label.copy(fontSize = 15.sp),
            color = StayFocusedTheme.colors.onGradient,
        )
    }
}

/** "Blocked from" + "{from}–{to}" (58 sp, weight 200) and the 2 x 2 preset cards. */
@Composable
private fun SchedulePicker(
    range: TimeRange,
    onPreset: (SchedulePreset) -> Unit,
    onRange: (TimeRange) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SchedulePickerHeader(range)
        SchedulePreset.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { preset ->
                    PresetCard(preset, selected = range.format() == preset.rangeText, onPreset, Modifier.weight(1f))
                }
            }
        }
        // v5 presets are four; the fifth, "Custom", opens the shipped from/to picker.
        CustomCard(
            selected = SchedulePreset.entries.none { range.format() == it.rangeText },
            onClick = { picking = true },
        )
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

/** The fifth schedule preset: "Custom · Pick times", same glass card as the four presets. */
@Composable
private fun CustomCard(
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = StayFocusedTheme.colors
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(shape)
                .background(if (selected) c.surface else c.onGradient.copy(alpha = 0.18f))
                .border(1.dp, c.onGradient.copy(alpha = 0.35f), shape)
                .testTag(WIZARD_RANGE_CUSTOM_TAG)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            stringResource(R.string.block_wizard_range_custom_link),
            style = StayFocusedTheme.type.label.copy(fontSize = 15.sp),
            color = if (selected) c.ink else c.onGradient,
        )
    }
}

private const val MIN_RANGE_SIZE = 30f
private const val RANGE_SHRINK = 0.94f

@Composable
private fun SchedulePickerHeader(range: TimeRange) {
    val c = StayFocusedTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            stringResource(R.string.block_wizard_blocked_from),
            style = StayFocusedTheme.type.label.copy(fontSize = 15.sp),
            color = c.onGradient,
        )
        // 58 sp on one line; on a narrow screen or a large font scale it shrinks until the range fits.
        var size by remember { mutableFloatStateOf(58f) }
        Text(
            range.format(),
            style =
                StayFocusedTheme.type.body.copy(
                    fontSize = size.sp,
                    fontWeight = FontWeight.ExtraLight,
                    lineHeight = size.sp,
                    letterSpacing = (-0.03).em,
                ),
            color = c.onGradient,
            maxLines = 1,
            softWrap = false,
            onTextLayout = { if (it.hasVisualOverflow && size > MIN_RANGE_SIZE) size *= RANGE_SHRINK },
            modifier = Modifier.testTag(WIZARD_RANGE_TEXT_TAG),
        )
    }
}

@Composable
private fun PresetCard(
    preset: SchedulePreset,
    selected: Boolean,
    onPick: (SchedulePreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = StayFocusedTheme.colors
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier =
            modifier
                .heightIn(min = 60.dp)
                .clip(shape)
                .background(if (selected) c.surface else c.onGradient.copy(alpha = 0.18f))
                .border(1.dp, c.onGradient.copy(alpha = 0.35f), shape)
                .testTag(wizardPresetTag(preset))
                .selectable(selected = selected, role = Role.RadioButton, onClick = { onPick(preset) })
                .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        val color = if (selected) c.ink else c.onGradient
        Text(
            stringResource(preset.nameRes()),
            style = StayFocusedTheme.type.label.copy(fontSize = 15.sp),
            color = color,
        )
        Text(
            preset.rangeText,
            style = StayFocusedTheme.type.body.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
            color = color,
        )
    }
}

private fun SchedulePreset.nameRes(): Int =
    when (this) {
        SchedulePreset.Work -> R.string.block_wizard_range_work
        SchedulePreset.School -> R.string.block_wizard_range_school
        SchedulePreset.Night -> R.string.block_wizard_range_night
        SchedulePreset.Dinner -> R.string.block_wizard_range_dinner
    }

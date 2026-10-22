@file:Suppress("MagicNumber", "LongMethod", "LongParameterList", "TooManyFunctions")

package com.dculus.stayfocused.feature.block

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.dculus.stayfocused.core.model.BlockDraft
import com.dculus.stayfocused.core.model.BlockType
import com.dculus.stayfocused.core.model.DialConfig
import com.dculus.stayfocused.core.model.DialConfigs
import com.dculus.stayfocused.core.model.LimitPeriod
import com.dculus.stayfocused.core.model.TimeRange
import com.dculus.stayfocused.core.model.durationLabel
import com.dculus.stayfocused.core.sync.AiReport
import com.dculus.stayfocused.core.ui.components.DayChips
import com.dculus.stayfocused.core.ui.components.DialPresets
import com.dculus.stayfocused.core.ui.components.MonoLabel
import com.dculus.stayfocused.core.ui.components.PrimaryButton
import com.dculus.stayfocused.core.ui.components.RotaryDial
import com.dculus.stayfocused.core.ui.components.SegmentedTabs
import com.dculus.stayfocused.core.ui.components.SfBottomSheet
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.dculus.stayfocused.core.ui.v5.components.AppIconTile
import com.dculus.stayfocused.core.ui.v5.components.GlowCard
import com.dculus.stayfocused.core.ui.v5.components.PillButton
import com.dculus.stayfocused.core.ui.v5.components.RoundIconButton
import com.dculus.stayfocused.core.ui.v5.components.StatusTag
import com.dculus.stayfocused.core.ui.v5.components.StatusTagKind
import com.dculus.stayfocused.core.ui.v5.components.TargetTag
import com.dculus.stayfocused.core.ui.v5.icons.V5Icons
import com.dculus.stayfocused.core.ui.v5.theme.V5Gradient
import com.dculus.stayfocused.core.ui.v5.theme.V5Gradients
import com.dculus.stayfocused.core.ui.v5.theme.V5Screen
import com.dculus.stayfocused.core.ui.v5.theme.V5Theme
import com.dculus.stayfocused.core.ui.v5.theme.v5NavigationBarPadding
import com.dculus.stayfocused.core.ui.v5.theme.v5StatusBarPadding
import java.time.DayOfWeek

const val WIZARD_BACK_TAG = "wizard_back"
const val WIZARD_CLOSE_TAG = "wizard_close"
const val WIZARD_TARGET_TAG = "wizard_target"
const val WIZARD_CTA_TAG = "wizard_cta"
const val WIZARD_DESCRIBE_TAG = "wizard_describe"
const val WIZARD_RULES_TAG = "wizard_rules"
const val WIZARD_SUMMARY_TAG = "wizard_summary"
const val WIZARD_AI_BANNER_TAG = "wizard_ai_banner"
const val WIZARD_RANGE_CUSTOM_TAG = "wizard_range_custom"
const val WIZARD_DELETE_TAG = "wizard_delete"
const val WIZARD_DELETE_CONFIRM_TAG = "wizard_delete_confirm"
const val WIZARD_DELETE_KEEP_TAG = "wizard_delete_keep"
const val WIZARD_REPORT_TAG = "wizard_report"
const val WIZARD_REPORT_CONFIRM_TAG = "wizard_report_confirm"
const val WIZARD_REPORT_SENTENCE_TAG = "wizard_report_sentence"
const val WIZARD_REPORT_DRAFT_TAG = "wizard_report_draft"

fun wizardRangeTag(range: String): String = "wizard_range_$range"

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
    val onCycleValue: (Int) -> Unit = {},
    val onCycleEdit: (CycleEdit) -> Unit = {},
    val onRange: (TimeRange) -> Unit = {},
    val onToggleDay: (DayOfWeek) -> Unit = {},
    val onChangeType: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onReport: () -> Unit = {},
)

private val WizardTypes = BlockType.entries
private val ScreenPadding = 14.dp
private val TypeTileHeight = 178.dp
private val TypeTileRadius = 32.dp
private val AppTileRadius = 28.dp
private val RingWidth = 3.dp

private fun BlockType.gradient(): V5Gradient =
    when (this) {
        BlockType.LIMIT -> V5Gradients.orange
        BlockType.CYCLE -> V5Gradients.typeCycle
        BlockType.SCHEDULE -> V5Gradients.typeSchedule
        BlockType.NOW -> V5Gradients.typeNow
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

/** The block wizard (v5, V5_SPEC §5): steps 1 and 2 are v5; the rules of step 3 follow in M12-11. */
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
            verticalArrangement = Arrangement.spacedBy(V5Theme.spacing.gap12),
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
                        else -> RulesStep(state, actions)
                    }
                }
            }
            if (state.step == WIZARD_STEP_RULES) {
                PillButton(
                    text = ctaLabel(state),
                    onClick = actions.onNext,
                    modifier = Modifier.padding(horizontal = ScreenPadding).fillMaxWidth().testTag(WIZARD_CTA_TAG),
                    enabled = state.ready && !state.saving,
                )
                // v5-extra: M13-03 (block edit and delete)
                if (state.editing && !state.saving) DeleteButton(onClick = { confirmDelete = true })
                Spacer(Modifier.height(8.dp))
            }
        }
    }
    // v5-extra: M13-03 (delete confirmation) and the AI report, still on the v2 sheet
    SfBottomSheet(visible = confirmDelete, onDismiss = { confirmDelete = false }) {
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
    SfBottomSheet(visible = confirmReport, onDismiss = { confirmReport = false }) {
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
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.block_report_title), style = StayFocusedTheme.type.display, color = c.text)
        Text(stringResource(R.string.block_report_body), style = StayFocusedTheme.type.body, color = c.secondary)
        // Scrolls on its own so a long sentence never pushes "Send report" off the screen.
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MonoLabel(stringResource(R.string.block_report_sentence_label))
            Text(
                report.sentence,
                style = StayFocusedTheme.type.body,
                color = c.text,
                modifier = Modifier.testTag(WIZARD_REPORT_SENTENCE_TAG),
            )
            MonoLabel(stringResource(R.string.block_report_draft_label))
            Text(
                report.draftFields.entries.joinToString("\n") { (key, value) -> "$key: $value" },
                style = StayFocusedTheme.type.bodyS,
                color = c.text,
                modifier = Modifier.testTag(WIZARD_REPORT_DRAFT_TAG),
            )
        }
        PrimaryButton(
            text = stringResource(R.string.block_report_send),
            onClick = onSend,
            enabled = !sending,
            modifier = Modifier.fillMaxWidth().testTag(WIZARD_REPORT_CONFIRM_TAG),
        )
        Text(
            stringResource(R.string.block_report_cancel),
            style = StayFocusedTheme.type.title,
            color = c.text,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        role = Role.Button,
                        onClick = onCancel,
                    ).padding(vertical = 12.dp),
        )
    }
}

@Composable
private fun DeleteButton(onClick: () -> Unit) {
    val c = StayFocusedTheme.colors
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.block_delete_action),
            style = StayFocusedTheme.type.title,
            color = c.alertText,
            modifier =
                Modifier
                    .testTag(WIZARD_DELETE_TAG)
                    .clickable(role = Role.Button, onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ColumnScope.DeleteSheetContent(
    onDelete: () -> Unit,
    onKeep: () -> Unit,
) {
    val c = StayFocusedTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.block_delete_title), style = StayFocusedTheme.type.display, color = c.text)
        Text(stringResource(R.string.block_delete_body), style = StayFocusedTheme.type.body, color = c.secondary)
        PrimaryButton(
            text = stringResource(R.string.block_delete_confirm),
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth().testTag(WIZARD_DELETE_CONFIRM_TAG),
        )
        Text(
            stringResource(R.string.block_delete_keep),
            style = StayFocusedTheme.type.title,
            color = c.text,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(WIZARD_DELETE_KEEP_TAG)
                    .clickable(role = Role.Button, onClick = onKeep)
                    .padding(vertical = 12.dp),
        )
    }
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
    val c = V5Theme.colors
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
                style = V5Theme.type.heading.copy(fontSize = 30.sp, letterSpacing = (-0.01).em),
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
            style = V5Theme.type.body.copy(fontSize = 14.sp),
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
                val c = V5Theme.colors
                Row(
                    modifier =
                        Modifier
                            .padding(top = 2.dp)
                            .fillMaxWidth()
                            .heightIn(min = V5Theme.spacing.minTouchTarget)
                            .testTag(WIZARD_DESCRIBE_TAG)
                            .clickable(role = Role.Button, onClick = actions.onDescribe),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(V5Icons.sparkle, contentDescription = null, tint = c.orange, modifier = Modifier.size(15.dp))
                    Text(
                        stringResource(R.string.block_wizard_describe),
                        style = V5Theme.type.label.copy(fontSize = 15.sp),
                        color = c.ink,
                    )
                }
            }
        }
    }
}

/** A 178 dp gradient tile; the selected one shows a white dot and a 3 dp ink ring after a 3 dp gap. */
@Composable
private fun TypeTile(
    type: BlockType,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = V5Theme.colors
    Box(
        modifier =
            modifier
                .height(TypeTileHeight)
                .drawBehind {
                    if (!selected) return@drawBehind
                    val outset = (RingWidth * 1.5f).toPx()
                    drawRoundRect(
                        color = c.ink,
                        topLeft = Offset(-outset, -outset),
                        size = Size(size.width + outset * 2, size.height + outset * 2),
                        cornerRadius = CornerRadius(TypeTileRadius.toPx() + outset),
                        style = Stroke(width = RingWidth.toPx()),
                    )
                },
    ) {
        GlowCard(
            gradient = type.gradient(),
            modifier =
                Modifier
                    .fillMaxSize()
                    .testTag(wizardTypeTag(type))
                    .semantics { this.selected = selected },
            radius = TypeTileRadius,
            padding = 16.dp,
            onClick = onClick,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    Modifier
                        .size(30.dp)
                        .background(if (selected) c.onGradient else Color.Transparent, CircleShape)
                        .border(2.dp, c.onGradient, CircleShape),
                )
            }
            Spacer(Modifier.weight(1f))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(type.nameRes()),
                    style =
                        V5Theme.type.body.copy(
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 21.sp,
                        ),
                    color = c.onGradient,
                )
                Text(
                    stringResource(type.descRes()),
                    style =
                        V5Theme.type.body.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 17.sp,
                        ),
                    color = c.onGradient,
                )
            }
        }
    }
}

@Composable
private fun AppsStep(
    state: WizardUiState,
    actions: WizardActions,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 40.dp),
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
        item {
            PillButton(
                text = ctaLabel(state),
                onClick = actions.onNext,
                modifier = Modifier.padding(top = 10.dp).fillMaxWidth().testTag(WIZARD_CTA_TAG),
                enabled = state.ready && state.selectedCount > 0,
            )
        }
    }
}

private const val APP_COLUMNS = 3

/** A white 112 dp tile: 48 dp icon and name; a selected one has a 2 dp inner ink ring and a check badge. */
@Composable
private fun AppTile(
    app: TargetApp,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = V5Theme.colors
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
                style = V5Theme.type.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
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
    val c = V5Theme.colors
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
                style = V5Theme.type.body.copy(fontSize = 14.sp),
                color = c.textTertiary,
                modifier = Modifier.weight(1f),
            )
        }
        // v5-extra: M13-03 (the AI's restatement, "Change" and "Report a bad result")
        if (draft.aiNote.isNotBlank()) {
            Text(draft.aiNote, style = V5Theme.type.body.copy(fontSize = 14.sp), color = c.textSecondary)
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
        style = V5Theme.type.label,
        color = V5Theme.colors.ink,
        modifier =
            modifier
                .heightIn(min = V5Theme.spacing.minTouchTarget)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = 14.dp),
    )
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
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding)
                .testTag(WIZARD_RULES_TAG),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
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

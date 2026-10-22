@file:Suppress("MagicNumber", "LongMethod", "LongParameterList", "TooManyFunctions")

package com.dculus.stayfocused.feature.block

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dculus.stayfocused.core.model.Block
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.model.BlockType
import com.dculus.stayfocused.core.model.LimitPeriod
import com.dculus.stayfocused.core.model.v5Duration
import com.dculus.stayfocused.core.ui.v5.charts.TickProgress
import com.dculus.stayfocused.core.ui.v5.components.AppIconStack
import com.dculus.stayfocused.core.ui.v5.components.GlowCard
import com.dculus.stayfocused.core.ui.v5.components.LocalTabBarPadding
import com.dculus.stayfocused.core.ui.v5.components.PillButton
import com.dculus.stayfocused.core.ui.v5.components.PillButtonVariant
import com.dculus.stayfocused.core.ui.v5.components.SegmentedPill
import com.dculus.stayfocused.core.ui.v5.components.SegmentedPillSize
import com.dculus.stayfocused.core.ui.v5.components.V5Card
import com.dculus.stayfocused.core.ui.v5.components.V5Toggle
import com.dculus.stayfocused.core.ui.v5.icons.V5Icons
import com.dculus.stayfocused.core.ui.v5.theme.V5Gradients
import com.dculus.stayfocused.core.ui.v5.theme.V5Screen
import com.dculus.stayfocused.core.ui.v5.theme.V5Theme
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

fun blockRowTag(id: String): String = "block_row_$id"

const val AI_CREATE_TAG = "block_ai_create"
const val NEW_BLOCK_FAB_TAG = "block_new_fab"
const val AI_FAB_TAG = "block_ai_fab"

private val AiExamples =
    listOf(R.string.block_ai_example_1, R.string.block_ai_example_2, R.string.block_ai_example_3)

/** Callbacks of the Block tab; grouped so the screen stays a stateless function of [BlockUiState]. */
data class BlockActions(
    val onSelectTarget: (BlockTarget) -> Unit = {},
    val onNewBlock: () -> Unit = {},
    val onToggleAi: () -> Unit = {},
    val onAiText: (String) -> Unit = {},
    val onAiSubmit: () -> Unit = {},
    val onSelectTab: (BlockTab) -> Unit = {},
    val onSetEnabled: (id: String, enabled: Boolean) -> Unit = { _, _ -> },
    val onEdit: (id: String) -> Unit = {},
    val onTemplate: (BlockTemplate) -> Unit = {},
    val onToggleAppLock: (pkg: String) -> Unit = {},
)

/** The Blocks tab (v5, V5_SPEC §5): title, target pill, "New block" card, count line, block cards and the FABs. */
@Composable
fun BlockScreen(
    state: BlockUiState,
    actions: BlockActions,
    modifier: Modifier = Modifier,
) {
    V5Screen(darkContent = false, modifier = modifier) {
        val spacing = V5Theme.spacing
        val myPhone = stringResource(R.string.block_my_phone)
        val targetName = state.selectedTarget.device?.name ?: myPhone
        val tabBarPadding = LocalTabBarPadding.current
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding =
                PaddingValues(
                    start = spacing.screenTabs,
                    end = spacing.screenTabs,
                    // The prototype's 58 dp top padding includes its 46 dp fake status bar.
                    top = 12.dp,
                    bottom = 72.dp + tabBarPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(spacing.gap12),
        ) {
            item {
                Text(
                    stringResource(R.string.block_title),
                    style = V5Theme.type.title,
                    color = V5Theme.colors.ink,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
            item {
                SegmentedPill(
                    options = state.targets.map { it.device?.name ?: myPhone },
                    selectedIndex = state.targets.indexOfFirst { it.target == state.selected }.coerceAtLeast(0),
                    onSelect = { actions.onSelectTarget(state.targets[it].target) },
                )
            }
            item { NewBlockCard(targetName, state.aiAvailable, actions.onNewBlock, actions.onToggleAi) }
            // v5-extra: M13-03 (AI describe panel stays inline until the describe screen lands)
            if (state.aiOpen) {
                item { AiPanel(targetName, state.aiText, state.aiBusy, actions.onAiText, actions.onAiSubmit) }
            }
            // v5-extra: M13-03 (All apps tab with quick lock)
            item {
                SegmentedPill(
                    options =
                        listOf(
                            stringResource(R.string.block_tab_blocks),
                            stringResource(R.string.block_tab_all_apps),
                        ),
                    selectedIndex = state.tab.ordinal,
                    onSelect = { actions.onSelectTab(BlockTab.entries[it]) },
                    size = SegmentedPillSize.Medium,
                )
            }
            if (state.tab == BlockTab.ALL_APPS) {
                items(state.apps, key = { it.pkg }) { app -> AppRow(app) { actions.onToggleAppLock(app.pkg) } }
            } else {
                item { CountLine(state.blocks) }
                items(state.blocks, key = { it.block.id }) { row ->
                    BlockCard(row, onEdit = { actions.onEdit(row.block.id) }) {
                        actions.onSetEnabled(row.block.id, it)
                    }
                }
                // v5-extra: M13-03 (templates)
                item {
                    Text(
                        stringResource(R.string.block_templates_header),
                        style = V5Theme.type.label,
                        color = V5Theme.colors.textSecondary,
                        modifier = Modifier.padding(start = 8.dp, top = 10.dp),
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(BlockTemplate.entries, key = { it.id }) { t -> TemplateCard(t) { actions.onTemplate(t) } }
                    }
                }
            }
        }
        Fabs(
            aiAvailable = state.aiAvailable,
            onAi = actions.onToggleAi,
            onNew = actions.onNewBlock,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = tabBarPadding + 4.dp),
        )
    }
}

/** Orange "New block / on {target}" card with the glass "Or describe it in words" row (prototype 168–176). */
@Composable
private fun NewBlockCard(
    targetName: String,
    aiAvailable: Boolean,
    onNew: () -> Unit,
    onAi: () -> Unit,
) {
    val c = V5Theme.colors
    GlowCard(V5Gradients.orangeCard, Modifier.fillMaxWidth(), padding = 0.dp) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onNew)
                    .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.block_new_block),
                    style = V5Theme.type.heading.copy(lineHeight = 29.sp),
                    color = c.onGradient,
                )
                Text(
                    stringResource(R.string.block_new_block_on, targetName),
                    style = V5Theme.type.label.copy(fontSize = 15.sp),
                    color = c.onGradient,
                )
            }
            Box(Modifier.size(54.dp).clip(CircleShape).background(c.surface), contentAlignment = Alignment.Center) {
                Icon(V5Icons.plus, contentDescription = null, tint = c.ink, modifier = Modifier.size(20.dp))
            }
        }
        if (aiAvailable) {
            val glass = RoundedCornerShape(26.dp)
            Row(
                modifier =
                    Modifier
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(glass)
                        .background(c.onGradient.copy(alpha = 0.2f))
                        .border(1.dp, c.onGradient.copy(alpha = 0.35f), glass)
                        .clickable(role = Role.Button, onClick = onAi)
                        .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(V5Icons.sparkle, contentDescription = null, tint = c.onGradient, modifier = Modifier.size(17.dp))
                Text(
                    stringResource(R.string.block_ai_cta),
                    style = V5Theme.type.label.copy(fontSize = 15.sp),
                    color = c.onGradient,
                )
            }
        }
    }
}

@Composable
private fun CountLine(blocks: List<BlockRowUi>) {
    val text =
        if (blocks.isEmpty()) {
            stringResource(R.string.block_empty_title)
        } else {
            stringResource(R.string.block_count, blocks.count { it.block.enabled }, blocks.size)
        }
    Text(
        text,
        style = V5Theme.type.label,
        color = V5Theme.colors.textSecondary,
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp),
    )
}

/** AI sparkle FAB (white) and the orange "New block" pill, floating above the tab bar (prototype 464–469). */
@Composable
private fun Fabs(
    aiAvailable: Boolean,
    onAi: () -> Unit,
    onNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = V5Theme.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (aiAvailable) {
            val describe = stringResource(R.string.block_ai_cta)
            Box(
                modifier =
                    Modifier
                        .testTag(AI_FAB_TAG)
                        .size(V5Theme.spacing.fabSize)
                        .shadow(
                            14.dp,
                            CircleShape,
                            ambientColor = c.ink.copy(alpha = 0.45f),
                            spotColor = c.ink.copy(alpha = 0.45f),
                        ).clip(CircleShape)
                        .background(c.surface)
                        .clickable(role = Role.Button, onClick = onAi)
                        .semantics { contentDescription = describe },
                contentAlignment = Alignment.Center,
            ) {
                Icon(V5Icons.sparkle, contentDescription = null, tint = c.orange, modifier = Modifier.size(20.dp))
            }
        }
        val pill = V5Theme.shapes.pill
        Row(
            modifier =
                Modifier
                    .testTag(NEW_BLOCK_FAB_TAG)
                    .heightIn(min = V5Theme.spacing.fabSize)
                    .shadow(
                        14.dp,
                        pill,
                        ambientColor = c.orange.copy(alpha = 0.6f),
                        spotColor = c.orange.copy(alpha = 0.6f),
                    ).clip(pill)
                    .background(c.orange)
                    .clickable(role = Role.Button, onClick = onNew)
                    .padding(start = 18.dp, end = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(V5Icons.plusBold, contentDescription = null, tint = c.onGradient, modifier = Modifier.size(18.dp))
            Text(
                stringResource(R.string.block_new_block),
                style = V5Theme.type.bodyL.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                color = c.onGradient,
            )
        }
    }
}

/** One block (prototype `viewBlock`): icon stack, title, type label, switch, rule sentence and the usage meter. */
@Composable
private fun BlockCard(
    row: BlockRowUi,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val c = V5Theme.colors
    val block = row.block
    val title = blockTitle(row.appLabels)
    val editLabel = stringResource(R.string.block_edit_description, title)
    V5Card(
        modifier = Modifier.fillMaxWidth().alpha(if (block.enabled) 1f else 0.6f).testTag(blockRowTag(block.id)),
        radius = 32.dp,
        padding = 16.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                // v5-extra: M13-03 (tap a block to edit or delete it)
                Row(
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clickable(onClickLabel = editLabel, role = Role.Button, onClick = onEdit),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIconStack(
                        pkgs = block.apps.toList(),
                        size = 38.dp,
                        labels = block.apps.zip(row.appLabels).toMap(),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            title,
                            style = V5Theme.type.bodyL.copy(fontSize = 16.sp),
                            color = c.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(typeLabel(block.type)),
                            style = V5Theme.type.body.copy(fontSize = 13.sp),
                            color = c.textSecondary,
                        )
                    }
                }
                V5Toggle(
                    checked = block.enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.semantics { contentDescription = title },
                )
            }
            Text(
                ruleSentence(block, rememberZone()),
                style = V5Theme.type.rule,
                color = c.ink,
                modifier = Modifier.clickable(onClickLabel = editLabel, role = Role.Button, onClick = onEdit),
            )
            row.progress?.let { Meter(block.type, it) }
        }
    }
}

@Composable
private fun Meter(
    type: BlockType,
    progress: BlockProgress,
) {
    val usedText: String
    val maxText: String
    if (type == BlockType.LIMIT) {
        usedText = stringResource(R.string.block_used_today, v5Duration(progress.usedMins))
        maxText = v5Duration(progress.maxMins)
    } else {
        usedText = stringResource(R.string.block_used_cycle, progress.usedMins, progress.maxMins)
        maxText = stringResource(R.string.block_max_cycle, progress.maxMins)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TickProgress(
            fraction = progress.fraction,
            contentDescription = stringResource(R.string.block_progress_description, usedText, maxText),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val style = V5Theme.type.body.copy(fontSize = 13.sp)
            Text(usedText, style = style, color = V5Theme.colors.textSecondary)
            Text(maxText, style = style, color = V5Theme.colors.textSecondary)
        }
    }
}

private fun typeLabel(type: BlockType): Int =
    when (type) {
        BlockType.LIMIT -> R.string.block_type_limit
        BlockType.CYCLE -> R.string.block_type_cycle
        BlockType.SCHEDULE -> R.string.block_type_schedule
        BlockType.NOW -> R.string.block_type_now
    }

@Composable
private fun rememberZone(): ZoneId = remember { ZoneId.systemDefault() }

/** The 22/300 sentence of a block card; the rules are in V5_SPEC §5. */
@Composable
internal fun ruleSentence(
    block: Block,
    zone: ZoneId,
): String =
    when (block.type) {
        BlockType.LIMIT -> {
            val mins = v5Duration(block.limitMins ?: 0)
            // v5-extra: M13-03 (hourly limits)
            stringResource(
                if (block.period == LimitPeriod.HOURLY) R.string.block_rule_limit_hourly else R.string.block_rule_limit,
                mins,
            )
        }

        BlockType.CYCLE -> {
            stringResource(R.string.block_rule_cycle, block.useMins ?: 0, v5Duration(block.restMins ?: 0))
        }

        BlockType.SCHEDULE -> {
            val range = block.range
            val times = if (range == null) "" else "${range.start}–${range.end}"
            stringResource(R.string.block_rule_schedule, times, daysText(block))
        }

        BlockType.NOW -> {
            val ends = nowBlockEnds(block, zone)
            if (ends != null) {
                stringResource(R.string.block_rule_now, ends.toString())
            } else {
                stringResource(R.string.block_rule_now_for, v5Duration(block.durationMins ?: 0))
            }
        }
    }

@Composable
private fun daysText(block: Block): String =
    when (daysStyle(block.days)) {
        DaysStyle.EVERY_DAY -> {
            stringResource(R.string.block_days_every)
        }

        DaysStyle.WEEKDAYS -> {
            stringResource(R.string.block_days_weekdays)
        }

        DaysStyle.WEEKENDS -> {
            stringResource(R.string.block_days_weekends)
        }

        DaysStyle.NONE -> {
            stringResource(R.string.block_days_none)
        }

        DaysStyle.LIST -> {
            DayOfWeek.entries
                .filter { it in block.days }
                .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
        }
    }

/** The describe panel (only reachable once the AI flag is on): sentence, example chips and the CTA. */
@Composable
private fun AiPanel(
    targetName: String,
    text: String,
    busy: Boolean,
    onText: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val c = V5Theme.colors
    V5Card(Modifier.fillMaxWidth(), padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.block_ai_intro, targetName),
                style = V5Theme.type.body,
                color = c.textSecondary,
            )
            val fieldShape = RoundedCornerShape(20.dp)
            val placeholder = stringResource(R.string.block_ai_placeholder)
            BasicTextField(
                value = text,
                onValueChange = onText,
                minLines = 3,
                textStyle = V5Theme.type.bodyL.copy(fontWeight = FontWeight.Normal, color = c.ink),
                cursorBrush = SolidColor(c.ink),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(fieldShape)
                        .background(c.surfaceRaised)
                        .padding(14.dp)
                        .semantics { contentDescription = placeholder },
                decorationBox = { inner ->
                    if (text.isEmpty()) {
                        Text(
                            placeholder,
                            style = V5Theme.type.bodyL.copy(fontWeight = FontWeight.Normal),
                            color = c.textSecondary,
                        )
                    }
                    inner()
                },
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(AiExamples) { res ->
                    val label = stringResource(res)
                    Text(
                        label,
                        style = V5Theme.type.label,
                        color = c.ink,
                        modifier =
                            Modifier
                                .heightIn(min = 48.dp)
                                .clip(V5Theme.shapes.pill)
                                .background(c.track)
                                .clickable(role = Role.Button) { onText(label) }
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                    )
                }
            }
            PillButton(
                text = stringResource(R.string.block_ai_create),
                onClick = onSubmit,
                variant = PillButtonVariant.Orange,
                loading = busy,
                loadingLabel = stringResource(R.string.block_ai_reading),
                modifier = Modifier.fillMaxWidth().testTag(AI_CREATE_TAG),
            )
        }
    }
}

@Composable
private fun TemplateCard(
    template: BlockTemplate,
    onClick: () -> Unit,
) {
    val c = V5Theme.colors
    val (name, desc) =
        when (template) {
            BlockTemplate.MINDFUL_SCROLLING -> {
                stringResource(R.string.block_template_mindful_name) to
                    stringResource(R.string.block_template_mindful_desc)
            }

            BlockTemplate.STUDY_TIME -> {
                stringResource(R.string.block_template_study_name) to stringResource(R.string.block_template_study_desc)
            }

            BlockTemplate.SOCIAL_LIMIT -> {
                stringResource(R.string.block_template_social_name) to
                    stringResource(R.string.block_template_social_desc)
            }

            BlockTemplate.BEDTIME -> {
                stringResource(R.string.block_template_bedtime_name) to
                    stringResource(R.string.block_template_bedtime_desc)
            }
        }
    val code =
        stringResource(
            when (template.type) {
                BlockType.CYCLE -> R.string.block_template_code_cycle
                BlockType.LIMIT -> R.string.block_template_code_limit
                else -> R.string.block_template_code_schedule
            },
        )
    V5Card(
        modifier = Modifier.width(168.dp).height(176.dp),
        radius = 28.dp,
        padding = 16.dp,
        onClick = onClick,
    ) {
        Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom)) {
            Text(code, style = V5Theme.type.tag, color = template.type.tint(c))
            Text(name, style = V5Theme.type.cardTitle, color = c.ink)
            Text(desc, style = V5Theme.type.body.copy(fontSize = 13.sp), color = c.textSecondary)
        }
    }
}

private fun BlockType.tint(c: com.dculus.stayfocused.core.ui.v5.theme.V5Colors): Color =
    when (this) {
        BlockType.LIMIT -> c.orange
        BlockType.CYCLE -> c.green
        BlockType.SCHEDULE -> c.pink
        BlockType.NOW -> c.ink
    }

@Preview(widthDp = 390, heightDp = 844)
@Composable
internal fun BlockScreenPreview() {
    BlockScreen(BlockUiState(aiAvailable = true), BlockActions())
}

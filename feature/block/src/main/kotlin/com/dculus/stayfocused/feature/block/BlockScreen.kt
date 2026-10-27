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
import com.dculus.stayfocused.core.ui.charts.TickProgress
import com.dculus.stayfocused.core.ui.components.AppIconStack
import com.dculus.stayfocused.core.ui.components.DescribeRow
import com.dculus.stayfocused.core.ui.components.LocalTabBarPadding
import com.dculus.stayfocused.core.ui.components.PillButton
import com.dculus.stayfocused.core.ui.components.PillButtonVariant
import com.dculus.stayfocused.core.ui.components.SegmentedPill
import com.dculus.stayfocused.core.ui.components.SegmentedPillSize
import com.dculus.stayfocused.core.ui.components.SoftPill
import com.dculus.stayfocused.core.ui.components.V5Card
import com.dculus.stayfocused.core.ui.components.V5Toggle
import com.dculus.stayfocused.core.ui.icons.V5Icons
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.dculus.stayfocused.core.ui.theme.V5Screen
import com.dculus.stayfocused.core.ui.theme.fontScaledDp
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

fun blockRowTag(id: String): String = "block_row_$id"

const val NEW_BLOCK_FAB_TAG = "block_new_fab"
const val AI_FAB_TAG = "block_ai_fab"

/** Callbacks of the Block tab; grouped so the screen stays a stateless function of [BlockUiState]. */
data class BlockActions(
    val onSelectTarget: (BlockTarget) -> Unit = {},
    val onNewBlock: () -> Unit = {},
    val onDescribe: () -> Unit = {},
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
        val spacing = StayFocusedTheme.spacing
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
                    style = StayFocusedTheme.type.title,
                    color = StayFocusedTheme.colors.ink,
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
            item { NewBlockCard(targetName, actions.onNewBlock) }
            if (state.aiAvailable) {
                item { DescribeRow(stringResource(R.string.block_ai_cta), actions.onDescribe) }
            }
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
                item {
                    Text(
                        stringResource(R.string.block_templates_header),
                        style = StayFocusedTheme.type.label,
                        color = StayFocusedTheme.colors.textSecondary,
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
            onAi = actions.onDescribe,
            onNew = actions.onNewBlock,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = tabBarPadding + 4.dp),
        )
    }
}

/** White "New block / on {target}" card: title and [SoftPill] on the left, a 56 dp ink plus button on the right. */
@Composable
private fun NewBlockCard(
    targetName: String,
    onNew: () -> Unit,
) {
    val c = StayFocusedTheme.colors
    val shape = RoundedCornerShape(32.dp)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .shadow(
                    12.dp,
                    shape,
                    clip = false,
                    ambientColor = c.ink.copy(alpha = 0.45f),
                    spotColor = c.ink.copy(alpha = 0.45f),
                ).clip(shape)
                .background(c.surface)
                .clickable(role = Role.Button, onClick = onNew)
                .padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.block_new_block),
                style = StayFocusedTheme.type.heading.copy(fontSize = 26.sp, lineHeight = 27.sp),
                color = c.ink,
            )
            SoftPill(stringResource(R.string.block_new_block_on, targetName))
        }
        Box(Modifier.size(56.dp).clip(CircleShape).background(c.ink), contentAlignment = Alignment.Center) {
            Icon(V5Icons.plus, contentDescription = null, tint = c.onGradient, modifier = Modifier.size(20.dp))
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
        style = StayFocusedTheme.type.label,
        color = StayFocusedTheme.colors.textSecondary,
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp),
    )
}

/** AI sparkle FAB (white) and the green "New block" pill, floating above the tab bar (prototype 464–469). */
@Composable
private fun Fabs(
    aiAvailable: Boolean,
    onAi: () -> Unit,
    onNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = StayFocusedTheme.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (aiAvailable) {
            val describe = stringResource(R.string.block_ai_cta)
            Box(
                modifier =
                    Modifier
                        .testTag(AI_FAB_TAG)
                        .size(StayFocusedTheme.spacing.fabSize)
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
                Icon(V5Icons.sparkle, contentDescription = null, tint = c.green, modifier = Modifier.size(20.dp))
            }
        }
        val pill = StayFocusedTheme.shapes.pill
        Row(
            modifier =
                Modifier
                    .testTag(NEW_BLOCK_FAB_TAG)
                    .heightIn(min = StayFocusedTheme.spacing.fabSize)
                    .shadow(
                        14.dp,
                        pill,
                        ambientColor = c.green.copy(alpha = 0.6f),
                        spotColor = c.green.copy(alpha = 0.6f),
                    ).clip(pill)
                    .background(c.green)
                    .clickable(role = Role.Button, onClick = onNew)
                    .padding(start = 18.dp, end = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(V5Icons.plusBold, contentDescription = null, tint = c.onGradient, modifier = Modifier.size(18.dp))
            Text(
                stringResource(R.string.block_new_block),
                style = StayFocusedTheme.type.bodyL.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
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
    val c = StayFocusedTheme.colors
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
                            style = StayFocusedTheme.type.bodyL.copy(fontSize = 16.sp),
                            color = c.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(typeLabel(block.type)),
                            style = StayFocusedTheme.type.body.copy(fontSize = 13.sp),
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
                style = StayFocusedTheme.type.rule,
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
            val style = StayFocusedTheme.type.body.copy(fontSize = 13.sp)
            Text(usedText, style = style, color = StayFocusedTheme.colors.textSecondary)
            Text(maxText, style = style, color = StayFocusedTheme.colors.textSecondary)
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

@Composable
private fun TemplateCard(
    template: BlockTemplate,
    onClick: () -> Unit,
) {
    val c = StayFocusedTheme.colors
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
        modifier = Modifier.width(fontScaledDp(168.dp)).height(fontScaledDp(176.dp)),
        radius = 28.dp,
        padding = 16.dp,
        onClick = onClick,
    ) {
        Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom)) {
            Text(code, style = StayFocusedTheme.type.tag, color = template.type.tint(c))
            Text(name, style = StayFocusedTheme.type.cardTitle, color = c.ink)
            Text(desc, style = StayFocusedTheme.type.body.copy(fontSize = 13.sp), color = c.textSecondary)
        }
    }
}

private fun BlockType.tint(c: com.dculus.stayfocused.core.ui.theme.V5Colors): Color =
    when (this) {
        BlockType.LIMIT -> c.green
        BlockType.CYCLE -> c.green
        BlockType.SCHEDULE -> c.green
        BlockType.NOW -> c.ink
    }

@Preview(widthDp = 390, heightDp = 844)
@Composable
internal fun BlockScreenPreview() {
    BlockScreen(BlockUiState(aiAvailable = true), BlockActions())
}

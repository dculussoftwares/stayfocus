@file:Suppress("MagicNumber", "LongMethod", "LongParameterList")

package com.dculus.stayfocused.feature.block

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.model.BlockType
import com.dculus.stayfocused.core.ui.components.MonoLabel
import com.dculus.stayfocused.core.ui.components.SegmentedTabs
import com.dculus.stayfocused.core.ui.components.SfToggle
import com.dculus.stayfocused.core.ui.components.TypeChip
import com.dculus.stayfocused.core.ui.theme.StayFocusedColors
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme

private val AiExamples =
    listOf(R.string.block_ai_example_1, R.string.block_ai_example_2, R.string.block_ai_example_3)

/** Callbacks of the Block tab; grouped so the screen stays a stateless function of [BlockUiState]. */
data class BlockActions(
    val onSelectTarget: (BlockTarget) -> Unit = {},
    val onNewBlock: () -> Unit = {},
    val onToggleAi: () -> Unit = {},
    val onAiText: (String) -> Unit = {},
    val onSelectTab: (BlockTab) -> Unit = {},
    val onSetEnabled: (id: String, enabled: Boolean) -> Unit = { _, _ -> },
    val onTemplate: (BlockTemplate) -> Unit = {},
)

@Composable
fun BlockScreen(
    state: BlockUiState,
    actions: BlockActions,
    modifier: Modifier = Modifier,
) {
    val c = StayFocusedTheme.colors
    val spacing = StayFocusedTheme.spacing
    val targetName = state.selectedTarget.device?.name ?: stringResource(R.string.block_this_phone)
    LazyColumn(
        modifier = modifier.fillMaxSize().background(c.background),
        contentPadding = PaddingValues(horizontal = spacing.screen, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.gap14),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(stringResource(R.string.block_title), style = StayFocusedTheme.type.display, color = c.text)
                MonoLabel(stringResource(R.string.block_blocking_on), Modifier.padding(bottom = 4.dp))
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.gap8)) {
                items(state.targets, key = { it.target.key() }) { t ->
                    TargetCard(t, selected = t.target == state.selected) { actions.onSelectTarget(t.target) }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.gap8)) {
                NewBlockButton(targetName, actions.onNewBlock, Modifier.weight(1f))
                if (state.aiAvailable) AiButton(state.aiOpen, actions.onToggleAi)
            }
        }
        if (state.aiOpen) {
            item { AiPanel(targetName, state.aiText, actions.onAiText) }
        }
        item {
            SegmentedTabs(
                options =
                    listOf(
                        stringResource(R.string.block_tab_blocks),
                        stringResource(R.string.block_tab_all_apps),
                    ),
                selectedIndex = state.tab.ordinal,
                onSelect = { actions.onSelectTab(BlockTab.entries[it]) },
            )
        }
        // The All apps tab is filled in by M4-05.
        if (state.tab == BlockTab.BLOCKS) {
            if (state.blocks.isEmpty()) {
                item { EmptyState() }
            } else {
                items(state.blocks, key = { it.block.id }) { row ->
                    BlockRow(row) { actions.onSetEnabled(row.block.id, it) }
                }
            }
            item {
                MonoLabel(
                    stringResource(R.string.block_templates_header),
                    Modifier.padding(start = 4.dp, top = 10.dp),
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(BlockTemplate.entries, key = { it.id }) { t -> TemplateCard(t) { actions.onTemplate(t) } }
                }
            }
        }
    }
}

private fun BlockTarget.key(): String = (this as? BlockTarget.Device)?.deviceId ?: "me"

@Composable
private fun TargetCard(
    target: TargetUi,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = StayFocusedTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val name = target.device?.name ?: stringResource(R.string.block_this_phone)
    val initial =
        target.device
            ?.name
            ?.take(1)
            ?.uppercase() ?: stringResource(R.string.block_this_phone_initial)
    Row(
        modifier =
            Modifier
                .height(52.dp)
                .clip(shape)
                .background(if (selected) c.accent.copy(alpha = 0.1f) else c.panel)
                .border(1.5.dp, if (selected) c.accent else c.hairline08, shape)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(start = 8.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(
                        36.dp,
                    ).clip(RoundedCornerShape(11.dp))
                    .background(if (selected) c.accent else c.track),
            contentAlignment = Alignment.Center,
        ) {
            Text(initial, style = StayFocusedTheme.type.displayS, color = if (selected) c.onAccent else c.text)
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                name,
                style = StayFocusedTheme.type.title,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MonoLabel(stringResource(R.string.block_active_count, target.activeCount))
        }
    }
}

@Composable
private fun NewBlockButton(
    targetName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = StayFocusedTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier =
            modifier
                .height(64.dp)
                .clip(shape)
                .background(c.accent)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(c.onAccent),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", style = StayFocusedTheme.type.display, color = c.accent)
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(stringResource(R.string.block_new_block), style = StayFocusedTheme.type.bodyL, color = c.onAccent)
            Text(
                stringResource(R.string.block_new_block_on, targetName),
                style = StayFocusedTheme.type.bodyS,
                color = c.onAccent.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AiButton(
    open: Boolean,
    onClick: () -> Unit,
) {
    val c = StayFocusedTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier =
            Modifier
                .width(76.dp)
                .height(64.dp)
                .clip(shape)
                .background(if (open) c.accent.copy(alpha = 0.12f) else c.panel)
                .border(BorderStroke(1.5.dp, if (open) c.accent else c.hairline14), shape)
                .selectable(selected = open, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
    ) {
        Text(
            stringResource(R.string.block_ai_badge),
            style = StayFocusedTheme.type.label,
            color = c.onAccent,
            modifier =
                Modifier
                    .clip(
                        RoundedCornerShape(5.dp),
                    ).background(c.accent)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Text(stringResource(R.string.block_ai_describe), style = StayFocusedTheme.type.bodyS, color = c.text)
    }
}

/** The describe panel; submitting is wired by M9-05, so it is only reachable once the AI flag is on. */
@Composable
private fun AiPanel(
    targetName: String,
    text: String,
    onText: (String) -> Unit,
) {
    val c = StayFocusedTheme.colors
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(c.panel)
                .border(1.dp, c.accent.copy(alpha = 0.3f), shape)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.block_ai_intro, targetName),
            style = StayFocusedTheme.type.body,
            color = c.secondary,
        )
        val fieldShape = RoundedCornerShape(16.dp)
        BasicTextField(
            value = text,
            onValueChange = onText,
            minLines = 3,
            textStyle = StayFocusedTheme.type.bodyL.copy(color = c.text),
            cursorBrush = SolidColor(c.accent),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(fieldShape)
                    .background(c.background)
                    .border(1.dp, c.hairline10, fieldShape)
                    .padding(14.dp),
            decorationBox = { inner ->
                if (text.isEmpty()) {
                    Text(
                        stringResource(R.string.block_ai_placeholder),
                        style = StayFocusedTheme.type.bodyL,
                        color = c.tertiary,
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
                    style = StayFocusedTheme.type.bodyS,
                    color = c.secondary,
                    modifier =
                        Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, c.hairline10, RoundedCornerShape(10.dp))
                            .clickable(role = Role.Button) { onText(label) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    val c = StayFocusedTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(R.string.block_empty_title), style = StayFocusedTheme.type.title, color = c.text)
        Text(stringResource(R.string.block_empty_body), style = StayFocusedTheme.type.body, color = c.secondary)
    }
}

@Composable
private fun BlockRow(
    row: BlockRowUi,
    onToggle: (Boolean) -> Unit,
) {
    val c = StayFocusedTheme.colors
    val shape = StayFocusedTheme.shapes.card
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(c.panel)
                .border(1.dp, c.hairline06, shape)
                .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TypeChip(row.block.type, dimmed = !row.block.enabled)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(row.block.name, style = StayFocusedTheme.type.title, color = c.text)
            Text(blockDetail(row), style = StayFocusedTheme.type.bodyS, color = c.secondary)
        }
        SfToggle(
            checked = row.block.enabled,
            onCheckedChange = onToggle,
            modifier = Modifier.semantics { contentDescription = row.block.name },
        )
    }
}

@Composable
private fun TemplateCard(
    template: BlockTemplate,
    onClick: () -> Unit,
) {
    val c = StayFocusedTheme.colors
    val shape = RoundedCornerShape(22.dp)
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
    Column(
        modifier =
            Modifier
                .width(168.dp)
                .height(176.dp)
                .clip(shape)
                .background(c.panel)
                .border(1.dp, c.hairline06, shape)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
    ) {
        Text(code, style = StayFocusedTheme.type.label, color = template.type.tint(c))
        Text(name, style = StayFocusedTheme.type.displayS, color = c.text)
        Text(desc, style = StayFocusedTheme.type.bodyS, color = c.secondary)
    }
}

private fun BlockType.tint(c: StayFocusedColors): Color =
    when (this) {
        BlockType.LIMIT -> c.appTints[0]
        BlockType.CYCLE -> c.accent
        BlockType.SCHEDULE -> c.appTints[6]
        BlockType.NOW -> c.warning
    }

@Preview(widthDp = 360, heightDp = 780)
@Composable
internal fun BlockScreenPreview() {
    StayFocusedTheme { BlockScreen(BlockUiState(), BlockActions()) }
}

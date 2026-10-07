@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dculus.stayfocused.core.model.BlockType
import com.dculus.stayfocused.core.ui.R
import com.dculus.stayfocused.core.ui.theme.StayFocusedColors
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import java.time.DayOfWeek
import java.time.format.TextStyle

private val MinTouch = 48.dp

private fun BlockType.chipColor(c: StayFocusedColors): Color =
    when (this) {
        BlockType.LIMIT -> c.appTints[0]
        BlockType.CYCLE -> c.accent
        BlockType.SCHEDULE -> c.appTints[6]
        BlockType.NOW -> c.warning
    }

private fun BlockType.chipLabel(): Int =
    when (this) {
        BlockType.LIMIT -> R.string.sf_chip_limit
        BlockType.CYCLE -> R.string.sf_chip_cycle
        BlockType.SCHEDULE -> R.string.sf_chip_schedule
        BlockType.NOW -> R.string.sf_chip_now
    }

/**
 * Block-type tag (LIM / CYC / HRS / NOW) in the type's colour; [dimmed] (the block is off) switches
 * to the muted track colours. Decorative: callers describe the block in their own row semantics.
 */
@Composable
fun TypeChip(
    type: BlockType,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
) {
    val c = StayFocusedTheme.colors
    Box(
        modifier =
            modifier
                .width(44.dp)
                .height(28.dp)
                .clip(StayFocusedTheme.shapes.chip)
                .background(if (dimmed) c.track else type.chipColor(c)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(type.chipLabel()),
            style = StayFocusedTheme.type.labelS,
            color = if (dimmed) c.secondary else c.onAccent,
        )
    }
}

/**
 * Single-choice chip (prototype `sel()`): lime when [selected], panel with a hairline otherwise.
 * Touch target 48 dp; exposes selected state with [role].
 */
@Composable
fun SelectChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.RadioButton,
) {
    val c = StayFocusedTheme.colors
    val shape = StayFocusedTheme.shapes.input
    Box(
        modifier =
            modifier
                .sizeIn(minWidth = MinTouch, minHeight = MinTouch)
                .clip(shape)
                .background(if (selected) c.accent else c.panel)
                .border(1.dp, if (selected) c.accent else c.hairline10, shape)
                .selectable(selected = selected, role = role, onClick = onClick)
                .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = StayFocusedTheme.type.body,
            color = if (selected) c.onAccent else c.text,
        )
    }
}

/** Segmented control (prototype `seg()`): the selected tab is filled `text`/ink, others are transparent. */
@Composable
fun SegmentedTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = StayFocusedTheme.colors
    val shape = StayFocusedTheme.shapes.input
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(c.panel)
                .border(1.dp, c.hairline10, shape)
                .padding(4.dp),
    ) {
        options.forEachIndexed { index, option ->
            SegmentedTab(option, index == selectedIndex) { onSelect(index) }
        }
    }
}

@Composable
private fun RowScope.SegmentedTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = StayFocusedTheme.colors
    Box(
        modifier =
            Modifier
                .weight(1f)
                .sizeIn(minWidth = MinTouch, minHeight = MinTouch)
                .clip(StayFocusedTheme.shapes.chip)
                .background(if (selected) c.text else c.background.copy(alpha = 0f))
                .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = StayFocusedTheme.type.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
            color = if (selected) c.onAccent else c.secondary,
        )
    }
}

private val dayLabels =
    listOf(
        DayOfWeek.MONDAY to R.string.sf_day_mon,
        DayOfWeek.TUESDAY to R.string.sf_day_tue,
        DayOfWeek.WEDNESDAY to R.string.sf_day_wed,
        DayOfWeek.THURSDAY to R.string.sf_day_thu,
        DayOfWeek.FRIDAY to R.string.sf_day_fri,
        DayOfWeek.SATURDAY to R.string.sf_day_sat,
        DayOfWeek.SUNDAY to R.string.sf_day_sun,
    )

/** Seven day toggles, M T W T F S S, Monday first. Each is a checkbox named with the full day name. */
@Composable
fun DayChips(
    selected: Set<DayOfWeek>,
    onToggle: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = StayFocusedTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        dayLabels.forEach { (day, labelRes) ->
            val on = day in selected
            val name = day.getDisplayName(TextStyle.FULL, locale)
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .sizeIn(minHeight = MinTouch)
                        .clip(StayFocusedTheme.shapes.input)
                        .background(if (on) c.accent else c.panel)
                        .border(1.dp, if (on) c.accent else c.hairline10, StayFocusedTheme.shapes.input)
                        .toggleable(value = on, role = Role.Checkbox) { onToggle(day) }
                        .semantics { contentDescription = name },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(labelRes),
                    style =
                        StayFocusedTheme.type.label.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        ),
                    color = if (on) c.onAccent else c.text,
                )
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 380)
@Composable
internal fun ChipsPreview() {
    StayFocusedTheme {
        Column(
            modifier = Modifier.background(StayFocusedTheme.colors.background).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BlockType.entries.forEach { TypeChip(it) }
                TypeChip(BlockType.LIMIT, dimmed = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectChip("Daily", selected = true, onClick = {})
                SelectChip("Hourly", selected = false, onClick = {})
            }
            SegmentedTabs(listOf("Apps", "Websites", "Keywords"), selectedIndex = 0, onSelect = {})
            DayChips(selected = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.FRIDAY), onToggle = {})
        }
    }
}

@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.dculus.stayfocused.core.ui.theme.V5Gradients

/** Where a [SegmentedPill] sits: on a light screen (ink container) or on a gradient card (glass container). */
enum class SegmentedPillStyle {
    /** `ink` container, unselected text `onInkMuted`. */
    Ink,

    /** White .2 container for use on a gradient, unselected text white. */
    Glass,
}

/** Height of one option; the container adds 4 dp above and below. */
enum class SegmentedPillSize(
    val optionHeight: Dp,
) {
    Large(46.dp),
    Medium(40.dp),
    Small(38.dp),
}

private const val GLASS_FILL_ALPHA = 0.2f
private val ContainerPadding = 4.dp

/**
 * Pill container with [options] of equal width (meant for 2–4 short labels; each option gets `width / count`, so
 * keep the count low enough for ≥ 48 dp per option); the selected one is a white pill with ink text. Each option is a
 * `Role.Tab` with its selected state. The container is at least 48 dp tall so every option is a 48 dp target
 * (for [SegmentedPillSize.Small] the container is 2 dp taller than the prototype's 46).
 */
@Composable
fun SegmentedPill(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    style: SegmentedPillStyle = SegmentedPillStyle.Ink,
    size: SegmentedPillSize = SegmentedPillSize.Large,
) {
    val c = StayFocusedTheme.colors
    val containerColor = if (style == SegmentedPillStyle.Ink) c.ink else c.onGradient.copy(alpha = GLASS_FILL_ALPHA)
    val unselectedText = if (style == SegmentedPillStyle.Ink) c.onInkMuted else c.onGradient
    val containerHeight = maxOf(size.optionHeight + ContainerPadding * 2, StayFocusedTheme.spacing.minTouchTarget)
    val verticalInset = (containerHeight - size.optionHeight) / 2
    val textSize = if (size == SegmentedPillSize.Large) 15.sp else 14.sp
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(containerHeight)
                .clip(StayFocusedTheme.shapes.segmented)
                .background(containerColor)
                .padding(horizontal = ContainerPadding - 1.dp)
                .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
                        .padding(horizontal = 1.dp, vertical = verticalInset),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(StayFocusedTheme.shapes.pill)
                            .background(if (selected) c.surface else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = StayFocusedTheme.type.label.copy(fontSize = textSize),
                        color = if (selected) c.ink else unselectedText,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 360)
@Composable
internal fun SegmentedPillPreview() {
    V5PreviewSurface(gradient = false) {
        SegmentedPill(listOf("Aarav's phone", "Mine"), selectedIndex = 0, onSelect = {})
        SegmentedPill(listOf("Aarav's phone", "Mine"), selectedIndex = 1, onSelect = {})
        SegmentedPill(listOf("Day", "Week", "Month"), selectedIndex = 1, onSelect = {}, size = SegmentedPillSize.Medium)
        SegmentedPill(listOf("Day", "Week"), selectedIndex = 0, onSelect = {}, size = SegmentedPillSize.Small)
        Box(Modifier.background(V5Gradients.violet.adaptive).padding(12.dp)) {
            SegmentedPill(
                listOf("Day", "Week"),
                selectedIndex = 1,
                onSelect = {},
                style = SegmentedPillStyle.Glass,
                size = SegmentedPillSize.Medium,
            )
        }
    }
}

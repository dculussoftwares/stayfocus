@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.v5.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dculus.stayfocused.core.ui.R
import com.dculus.stayfocused.core.ui.v5.theme.V5Theme
import java.time.DayOfWeek
import java.time.format.TextStyle

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

/**
 * The days after tapping [day]: it is added when off and removed when on, except that the last remaining day is
 * never removed (a block needs at least one day), in which case [selected] is returned unchanged.
 */
fun toggleDay(
    selected: Set<DayOfWeek>,
    day: DayOfWeek,
): Set<DayOfWeek> =
    when {
        day !in selected -> selected + day
        selected.size <= 1 -> selected
        else -> selected - day
    }

/**
 * Seven 40 dp day circles, M T W T F S S (Monday first), spread across the width. On = ink with a white letter,
 * off = `track` with an ink letter. At least one day stays on. Each chip is a 48 dp checkbox named with the full
 * day name.
 */
@Composable
fun DayChips(
    selected: Set<DayOfWeek>,
    onSelectedChange: (Set<DayOfWeek>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = V5Theme.colors
    val locale = LocalConfiguration.current.locales[0]
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        dayLabels.forEach { (day, labelRes) ->
            val on = day in selected
            val name = day.getDisplayName(TextStyle.FULL, locale)
            Box(
                modifier =
                    Modifier
                        .sizeIn(minWidth = V5Theme.spacing.minTouchTarget, minHeight = V5Theme.spacing.minTouchTarget)
                        .toggleable(value = on, role = Role.Checkbox) { onSelectedChange(toggleDay(selected, day)) }
                        .semantics { contentDescription = name },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (on) c.ink else c.track),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(labelRes),
                        style = V5Theme.type.label,
                        color = if (on) c.onGradient else c.ink,
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 200)
@Composable
internal fun DayChipsPreview() {
    V5PreviewSurface(gradient = false) {
        Column(
            Modifier.background(V5Theme.colors.surface, V5Theme.shapes.card).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DayChips(selected = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.FRIDAY), onSelectedChange = {})
            DayChips(selected = setOf(DayOfWeek.SATURDAY), onSelectedChange = {})
            DayChips(selected = DayOfWeek.entries.toSet(), onSelectedChange = {})
        }
    }
}

@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme

private val BAR_HEIGHT = 4.dp

/**
 * One row of the "most used apps" ranking: [icon] (a 38 dp `AppIconTile`), [name] (15 / 600) with [timeText] on its
 * right in secondary colour, and a 4 dp bar below — green on a track — filled to [fraction] of the row's width (clamped
 * to 0..1, NaN counts as 0). The row reads as "name, time" to TalkBack: the icon's own semantics are dropped so the
 * name isn't read twice; the bar is decorative.
 */
@Composable
fun UsageRow(
    icon: @Composable () -> Unit,
    name: String,
    timeText: String,
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val colors = StayFocusedTheme.colors
    val shown = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 10.dp).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.clearAndSetSemantics {}) { icon() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    name,
                    style = StayFocusedTheme.type.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.ink,
                )
                Text(timeText, style = StayFocusedTheme.type.body, color = colors.textSecondary)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(BAR_HEIGHT)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.track),
            ) {
                if (shown > 0f) {
                    Box(Modifier.fillMaxWidth(shown).height(BAR_HEIGHT).background(colors.green))
                }
            }
        }
    }
}

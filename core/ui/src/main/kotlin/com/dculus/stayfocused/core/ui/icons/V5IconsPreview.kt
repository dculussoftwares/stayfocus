@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.icons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme

/** Every icon with its name, in the order of the Roborazzi sheet. */
internal val v5IconSet: List<Pair<String, ImageVector>>
    get() =
        listOf(
            "back" to V5Icons.back,
            "arrow" to V5Icons.arrow,
            "close" to V5Icons.close,
            "plus" to V5Icons.plus,
            "plusMedium" to V5Icons.plusMedium,
            "plusBold" to V5Icons.plusBold,
            "bell" to V5Icons.bell,
            "block" to V5Icons.block,
            "lock" to V5Icons.lock,
            "timer" to V5Icons.timer,
            "check" to V5Icons.check,
            "checkBold" to V5Icons.checkBold,
            "sparkle" to V5Icons.sparkle,
            "sparkleTwin" to V5Icons.sparkleTwin,
        )

@Composable
private fun IconRow(
    size: Dp,
    tint: Color,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        v5IconSet.forEach { (_, icon) ->
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size))
        }
    }
}

/** All icons at 18, 20 and 24 dp, in ink on the background and white on ink. */
@Preview(widthDp = 520, heightDp = 260)
@Composable
internal fun V5IconsPreview() {
    StayFocusedTheme {
        val colors = StayFocusedTheme.colors
        Column(
            Modifier.background(colors.background).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            listOf(18.dp, 20.dp, 24.dp).forEach { IconRow(it, colors.ink) }
            Column(
                Modifier.background(colors.ink).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                listOf(18.dp, 24.dp).forEach { IconRow(it, Color.White) }
            }
        }
    }
}

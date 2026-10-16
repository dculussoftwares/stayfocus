package com.dculus.stayfocused.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme

private const val LINK_CODE_LENGTH = 6
private const val LINK_CODE_GROUP = 3

/** `482913` -> `482 913`, the way both phones show the pairing code. Anything but 6 characters is returned as is. */
fun formatLinkCode(code: String): String =
    if (code.length == LINK_CODE_LENGTH) "${code.take(LINK_CODE_GROUP)} ${code.drop(LINK_CODE_GROUP)}" else code

/**
 * The 6-digit pairing code in a lime-outlined card under a mono caps [label]. The code is read out digit by digit for
 * screen readers.
 */
@Composable
fun LinkCodeCard(
    label: String,
    code: String,
    modifier: Modifier = Modifier,
) {
    val colors = StayFocusedTheme.colors
    val shape = StayFocusedTheme.shapes.card
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.panel, shape)
            .border(1.dp, colors.accent.copy(alpha = 0.35f), shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(StayFocusedTheme.spacing.gap8),
    ) {
        MonoLabel(label)
        Text(
            formatLinkCode(code),
            style =
                StayFocusedTheme.type.numeric.copy(
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.14.em,
                ),
            color = colors.accent,
            modifier = Modifier.semantics { contentDescription = code.toList().joinToString(" ") },
        )
    }
}

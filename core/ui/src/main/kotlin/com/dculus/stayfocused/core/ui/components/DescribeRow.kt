@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dculus.stayfocused.core.ui.icons.V5Icons
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme

/**
 * The white "describe it" row (Home and Blocks): 62 dp tall, radius 31, a green sparkle, [label] in secondary text and
 * a 46 dp ink circle with a white arrow. The whole row is the button.
 */
@Composable
fun DescribeRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StayFocusedTheme.colors
    val shape = RoundedCornerShape(31.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(62.dp)
            .shadow(
                10.dp,
                shape,
                clip = false,
                ambientColor = colors.ink.copy(alpha = 0.4f),
                spotColor = colors.ink.copy(alpha = 0.4f),
            ).clip(shape)
            .background(colors.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .padding(start = 18.dp, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(V5Icons.sparkleTwin, contentDescription = null, tint = colors.green, modifier = Modifier.size(20.dp))
        Text(
            label,
            style = StayFocusedTheme.type.body.copy(fontSize = 16.sp, fontWeight = FontWeight.Normal),
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.size(46.dp).background(colors.ink, CircleShape), contentAlignment = Alignment.Center) {
            Icon(V5Icons.arrow, contentDescription = null, tint = colors.onGradient, modifier = Modifier.size(18.dp))
        }
    }
}

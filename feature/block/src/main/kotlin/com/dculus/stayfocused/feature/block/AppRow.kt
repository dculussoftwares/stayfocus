@file:Suppress("MagicNumber")

package com.dculus.stayfocused.feature.block

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dculus.stayfocused.core.model.formatMinutes
import com.dculus.stayfocused.core.ui.components.MonoLabel
import com.dculus.stayfocused.core.ui.icon.AppIcon
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme

/** One launchable app: "{time} · {n} OPENS" and the LOCK / LOCKED pill. */
@Composable
internal fun AppRow(
    app: AppRowUi,
    onToggle: () -> Unit,
) {
    val c = StayFocusedTheme.colors
    val shape = RoundedCornerShape(18.dp)
    val pillShape = RoundedCornerShape(10.dp)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(c.panel)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.pkg, label = app.label, size = 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                app.label,
                style = StayFocusedTheme.type.title,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MonoLabel(stringResource(R.string.block_app_line, formatMinutes(app.mins), app.opens))
        }
        Box(
            modifier =
                Modifier
                    .testTag(appLockTag(app.pkg))
                    .height(34.dp)
                    .clip(pillShape)
                    .background(if (app.locked) c.alert.copy(alpha = 0.16f) else Color.Transparent)
                    .border(1.dp, if (app.locked) c.alert.copy(alpha = 0.35f) else c.hairline14, pillShape)
                    .toggleable(value = app.locked, role = Role.Switch, onValueChange = { onToggle() })
                    .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(if (app.locked) R.string.block_app_locked else R.string.block_app_lock),
                style = StayFocusedTheme.type.label,
                color = if (app.locked) c.alertText else c.text,
            )
        }
    }
}

fun appLockTag(pkg: String): String = "block_app_lock_$pkg"

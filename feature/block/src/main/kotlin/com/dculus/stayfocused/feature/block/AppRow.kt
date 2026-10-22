@file:Suppress("MagicNumber")

package com.dculus.stayfocused.feature.block

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dculus.stayfocused.core.model.formatMinutes
import com.dculus.stayfocused.core.ui.v5.components.AppIconTile
import com.dculus.stayfocused.core.ui.v5.components.V5Card
import com.dculus.stayfocused.core.ui.v5.theme.V5Screen
import com.dculus.stayfocused.core.ui.v5.theme.V5Theme

/** One launchable app: "{time} · {n} OPENS" and the LOCK / LOCKED pill. */
@Composable
internal fun AppRow(
    app: AppRowUi,
    onToggle: () -> Unit,
) {
    val c = V5Theme.colors
    val pillDescription =
        stringResource(
            if (app.locked) R.string.block_app_unlock_description else R.string.block_app_lock_description,
            app.label,
        )
    V5Card(Modifier.fillMaxWidth(), radius = 24.dp, padding = 12.dp) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIconTile(app.pkg, size = 40.dp, label = app.label)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    app.label,
                    style = V5Theme.type.bodyL,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.block_app_line, formatMinutes(app.mins), app.opens),
                    style = V5Theme.type.body.copy(fontSize = 13.sp),
                    color = c.textSecondary,
                )
            }
            Box(
                modifier =
                    Modifier
                        .testTag(appLockTag(app.pkg))
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = pillDescription }
                        .toggleable(value = app.locked, role = Role.Switch, onValueChange = { onToggle() }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(if (app.locked) R.string.block_app_locked else R.string.block_app_lock),
                    style = V5Theme.type.label,
                    color = if (app.locked) c.surface else c.ink,
                    modifier =
                        Modifier
                            .clip(V5Theme.shapes.pill)
                            .background(if (app.locked) c.ink else c.track)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

fun appLockTag(pkg: String): String = "block_app_lock_$pkg"

@Preview(widthDp = 360)
@Composable
internal fun AppRowPreview() {
    V5Screen(darkContent = false) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AppRow(AppRowUi("com.instagram.android", "Instagram", 83, 12, locked = true)) {}
            AppRow(AppRowUi("com.google.android.youtube", "YouTube", 45, 6, locked = false)) {}
        }
    }
}

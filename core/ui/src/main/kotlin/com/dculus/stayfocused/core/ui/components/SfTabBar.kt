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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.dculus.stayfocused.core.ui.icon.TabIcons
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme

const val SF_TAB_BAR_TAG = "sf_tab_bar"

/** Test tag of the tab with the given [SfTabItem.id]. */
fun sfTabTag(id: String): String = "sf_tab_$id"

/** One tab: a stable [id] (also its test tag suffix), a mono [label] and a stroked [icon]. */
@Immutable
data class SfTabItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
)

/**
 * Floating tab bar (prototype `nav`): r24 panel with a hairline and shadow, mono labels, and the active tab
 * as a lime r18 pill with ink content. [selectedId] may match no tab (e.g. on Account).
 */
@Composable
fun SfTabBar(
    items: List<SfTabItem>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = StayFocusedTheme.shapes.tabBar
    Row(
        modifier =
            modifier
                .testTag(SF_TAB_BAR_TAG)
                .fillMaxWidth()
                .shadow(elevation = 12.dp, shape = shape, clip = false)
                .clip(shape)
                .background(StayFocusedTheme.colors.panelGradientStart)
                .border(1.dp, StayFocusedTheme.colors.hairline06, shape)
                .padding(6.dp),
    ) {
        items.forEach { item ->
            SfTab(item, item.id == selectedId) { onSelect(item.id) }
        }
    }
}

@Composable
private fun RowScope.SfTab(
    item: SfTabItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = StayFocusedTheme.colors
    val content = if (selected) c.onAccent else c.secondary
    Column(
        modifier =
            Modifier
                .weight(1f)
                .testTag(sfTabTag(item.id))
                .clip(TabPillShape)
                .background(if (selected) c.accent else c.accent.copy(alpha = 0f))
                .selectable(selected = selected, role = Role.Tab, onClick = onClick)
                .padding(top = 9.dp, bottom = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(imageVector = item.icon, contentDescription = null, modifier = Modifier.size(21.dp), tint = content)
        Text(
            text = item.label,
            style =
                StayFocusedTheme.type.labelS.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.06.em,
                ),
            color = content,
        )
    }
}

private val TabPillShape = RoundedCornerShape(18.dp)

@Preview(widthDp = 360, heightDp = 120)
@Composable
internal fun SfTabBarPreview() {
    StayFocusedTheme {
        val items =
            listOf(
                SfTabItem("home", "HOME", TabIcons.Home),
                SfTabItem("block", "BLOCK", TabIcons.Block),
                SfTabItem("devices", "DEVICES", TabIcons.Devices),
                SfTabItem("insights", "INSIGHTS", TabIcons.Insights),
            )
        Box(Modifier.background(StayFocusedTheme.colors.background).padding(14.dp)) {
            SfTabBar(items = items, selectedId = "block", onSelect = {})
        }
    }
}

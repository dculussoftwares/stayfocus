@file:Suppress("MagicNumber", "ktlint:compose:compositionlocal-allowlist")

package com.dculus.stayfocused.core.ui.components

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.dculus.stayfocused.core.ui.theme.v5NavigationBarPadding

const val V5_TAB_BAR_TAG = "v5_tab_bar"

/** Test tag of the tab with the given [PillTab.id]. */
fun v5TabTag(id: String): String = "v5_tab_$id"

/** One tab: a stable [id] (also its test tag suffix) and its text [label]. */
@Immutable
data class PillTab(
    val id: String,
    val label: String,
)

private val BarSideMargin = 14.dp
private val BarBottomMargin = 22.dp
private val BarPadding = 5.dp
private val TabHeight = 50.dp
private val TabGap = 2.dp
private val TabRadius = 25.dp
private val ScrollGap = 16.dp
private const val BAR_ALPHA = 0.92f
private const val MAX_FONT_SCALE = 1.3f
private val BlurRadius = 20.dp

/** Height of the bar itself: tab height plus the padding above and below. */
val PillTabBarHeight: Dp = TabHeight + BarPadding * 2

/**
 * Space a tab screen's scrolling content needs at its bottom so the last item can clear the floating bar: bar height +
 * its 22 dp bottom margin + a 16 dp gap + the navigation-bar inset ([navigationBarInset]).
 */
fun pillTabBarContentPadding(navigationBarInset: Dp): Dp =
    PillTabBarHeight + BarBottomMargin + ScrollGap + navigationBarInset

/**
 * Bottom padding that tab screens add to their scrolling content while the [PillTabBar] floats over it (zero where
 * there is no bar). Provided by the app scaffold; read it with `LocalTabBarPadding.current`.
 */
val LocalTabBarPadding = compositionLocalOf { 0.dp }

/** Current [pillTabBarContentPadding] for this window, for the scaffold to provide as [LocalTabBarPadding]. */
@Composable
fun rememberPillTabBarContentPadding(): Dp {
    val inset = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
    return pillTabBarContentPadding(inset)
}

/**
 * v5 floating tab bar (prototype `showTabs`): a text-only pill, 14 dp from the sides and 22 dp (+ navigation-bar
 * inset) from the bottom, `ink` at .92 with a drop shadow; the selected tab is a white pill with ink text, the others
 * `onInkMuted`. [selectedId] may match no tab (e.g. on Account). Place it at the bottom of the screen's `Box`
 * (`Modifier.align(Alignment.BottomCenter)`), over the content.
 *
 * Label size follows the system font scale up to 1.3x so "Insights" still fits its tab.
 *
 * [backdrop] is the recorded content the bar floats over (see [PillTabBarBackdrop]); from API 31 it is drawn blurred
 * (20 dp) under the ink fill, like the prototype's `backdrop-filter`. Without it, or below API 31, only the fill shows.
 */
@Composable
fun PillTabBar(
    tabs: List<PillTab>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: PillTabBarBackdrop? = null,
) {
    val colors = StayFocusedTheme.colors
    val shape = StayFocusedTheme.shapes.tabBar
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, minOf(density.fontScale, MAX_FONT_SCALE)),
    ) {
        Box(
            modifier =
                modifier
                    .v5NavigationBarPadding(extra = BarBottomMargin)
                    .padding(horizontal = BarSideMargin)
                    .testTag(V5_TAB_BAR_TAG)
                    .fillMaxWidth()
                    .shadow(
                        elevation = 20.dp,
                        shape = shape,
                        clip = false,
                        ambientColor = colors.ink.copy(alpha = 0.6f),
                        spotColor = colors.ink.copy(alpha = 0.6f),
                    ).clip(shape),
        ) {
            if (backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                BlurredBackdrop(backdrop, Modifier.matchParentSize())
            }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(colors.ink.copy(alpha = BAR_ALPHA))
                        .padding(BarPadding)
                        .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(TabGap),
            ) {
                tabs.forEach { tab -> PillTabItem(tab, tab.id == selectedId) { onSelect(tab.id) } }
            }
        }
    }
}

/**
 * The content a [PillTabBar] floats over, captured so the bar can draw a blurred copy of what is behind it. Make one
 * with [rememberPillTabBarBackdrop], draw the screen through [Modifier.recordBackdrop] and pass it to the bar.
 */
@Stable
class PillTabBarBackdrop internal constructor(
    internal val layer: GraphicsLayer,
) {
    internal var contentOrigin by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberPillTabBarBackdrop(): PillTabBarBackdrop = PillTabBarBackdrop(rememberGraphicsLayer())

/** Records this node's content into [backdrop] while still drawing it normally. */
fun Modifier.recordBackdrop(backdrop: PillTabBarBackdrop): Modifier =
    onGloballyPositioned { backdrop.contentOrigin = it.positionInWindow() }.drawWithContent {
        backdrop.layer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
    }

@RequiresApi(Build.VERSION_CODES.S)
@Composable
private fun BlurredBackdrop(
    backdrop: PillTabBarBackdrop,
    modifier: Modifier = Modifier,
) {
    var barOrigin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .onGloballyPositioned { barOrigin = it.positionInWindow() }
            .blur(BlurRadius, BlurredEdgeTreatment.Unbounded)
            .drawBehind {
                val shift = backdrop.contentOrigin - barOrigin
                translate(shift.x, shift.y) { drawLayer(backdrop.layer) }
            },
    )
}

@Composable
private fun RowScope.PillTabItem(
    tab: PillTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = StayFocusedTheme.colors
    Box(
        modifier =
            Modifier
                .weight(1f)
                .height(TabHeight)
                .testTag(v5TabTag(tab.id))
                .clip(RoundedCornerShape(TabRadius))
                .background(if (selected) colors.surface else Color.Transparent)
                .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = tab.label,
            style = StayFocusedTheme.type.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            color = if (selected) colors.ink else colors.onInkMuted,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(widthDp = 390, heightDp = 160)
@Composable
internal fun PillTabBarPreview() {
    StayFocusedTheme {
        Box(Modifier.fillMaxSize().background(StayFocusedTheme.colors.background)) {
            PillTabBar(
                tabs =
                    listOf(
                        PillTab("home", "Home"),
                        PillTab("block", "Blocks"),
                        PillTab("devices", "Family"),
                        PillTab("insights", "Insights"),
                    ),
                selectedId = "home",
                onSelect = {},
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

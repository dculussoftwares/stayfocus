package com.dculus.vesper.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dculus.vesper.core.ui.R
import com.dculus.vesper.core.ui.theme.VesperTheme

const val SF_PLACEHOLDER_LIST_TAG = "sf_placeholder_list"
private const val PLACEHOLDER_ROWS = 40

/** A button on a [NavPlaceholderScreen] that triggers a navigation, so every route can be reached. */
@Immutable
data class PlaceholderAction(
    val label: String,
    val onClick: () -> Unit,
)

/**
 * Stand-in for a screen that has not been built yet: its [title], optional navigation [actions] and a
 * scrollable list (the scroll position survives tab switches). Replaced by the real screen in its own story.
 */
@Composable
fun NavPlaceholderScreen(
    title: String,
    modifier: Modifier = Modifier,
    actions: List<PlaceholderAction> = emptyList(),
    /** Real content shown under the title, above the placeholder rows (screens that are being built up). */
    header: (@Composable () -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier =
            modifier
                .fillMaxSize()
                .background(VesperTheme.colors.background)
                .testTag(SF_PLACEHOLDER_LIST_TAG),
        contentPadding = PaddingValues(VesperTheme.spacing.gap16),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(text = title, style = VesperTheme.type.title, color = VesperTheme.colors.ink) }
        if (header != null) item { header() }
        items(actions) { action ->
            PillButton(
                text = action.label,
                onClick = action.onClick,
                modifier = Modifier.fillMaxWidth(),
                variant = PillButtonVariant.Secondary,
                size = PillButtonSize.Compact,
            )
        }
        items(PLACEHOLDER_ROWS) { index ->
            Text(
                text = stringResource(R.string.sf_placeholder_item, index),
                style = VesperTheme.type.body,
                color = VesperTheme.colors.textSecondary,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}

@Preview(widthDp = 360, heightDp = 480)
@Composable
internal fun NavPlaceholderScreenPreview() {
    VesperTheme {
        NavPlaceholderScreen(title = "Devices", actions = listOf(PlaceholderAction("Open a screen", {})))
    }
}

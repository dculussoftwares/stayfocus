@file:Suppress("MagicNumber")

package com.dculus.stayfocused.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.dculus.stayfocused.core.model.LinkedDevice
import com.dculus.stayfocused.core.model.formatMinutes
import com.dculus.stayfocused.core.ui.components.IconTile
import com.dculus.stayfocused.core.ui.components.LedBarChart
import com.dculus.stayfocused.core.ui.components.LedDot
import com.dculus.stayfocused.core.ui.components.MonoLabel
import com.dculus.stayfocused.core.ui.components.PrimaryButton
import com.dculus.stayfocused.core.ui.components.ScreenTimeGauge
import com.dculus.stayfocused.core.ui.components.SfToggle
import com.dculus.stayfocused.core.ui.components.TypeChip
import com.dculus.stayfocused.core.ui.components.WhiteButton
import com.dculus.stayfocused.core.ui.components.blockDetail
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme

internal const val HOME_LIST_TAG = "home_list"
internal const val HOME_AVATAR_TAG = "home_avatar"
internal const val HOME_NEW_BLOCK_TAG = "home_new_block"
internal const val HOME_AI_DESCRIBE_TAG = "home_ai_describe"
internal const val HOME_GAUGE_CARD_TAG = "home_gauge_card"
internal const val HOME_ALLOW_USAGE_TAG = "home_allow_usage"
internal const val HOME_MANAGE_TAG = "home_manage"
internal const val HOME_ALL_DEVICES_TAG = "home_all_devices"
internal const val HOME_LINK_PHONE_TAG = "home_link_phone"
internal const val HOME_BLOCKS_EMPTY_TAG = "home_blocks_empty"

/** Date line, greeting and the avatar that opens Account. */
@Composable
internal fun HomeHeader(
    header: HeaderUi,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = StayFocusedTheme.colors
    val greeting =
        when (header.greeting) {
            Greeting.MORNING -> stringResource(R.string.home_greeting_morning)
            Greeting.AFTERNOON -> stringResource(R.string.home_greeting_afternoon)
            Greeting.EVENING -> stringResource(R.string.home_greeting_evening)
        }
    val namedGreeting =
        header.firstName?.let { name ->
            when (header.greeting) {
                Greeting.MORNING -> stringResource(R.string.home_greeting_morning_named, name)
                Greeting.AFTERNOON -> stringResource(R.string.home_greeting_afternoon_named, name)
                Greeting.EVENING -> stringResource(R.string.home_greeting_evening_named, name)
            }
        } ?: greeting
    Row(
        modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                header.dateLabel,
                style = StayFocusedTheme.type.label.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold),
                color = c.secondary,
            )
            Text(namedGreeting, style = StayFocusedTheme.type.title, color = c.text)
        }
        val avatarDescription = stringResource(R.string.home_avatar_description)
        Box(
            Modifier
                .testTag(HOME_AVATAR_TAG)
                .size(48.dp)
                .clickable(role = Role.Button, onClick = onOpenAccount)
                .semantics { contentDescription = avatarDescription },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(c.panel)
                    .border(1.dp, c.hairline10, RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center,
            ) {
                val initial = header.initial
                if (initial != null) {
                    Text(
                        initial,
                        style = StayFocusedTheme.type.title.copy(fontSize = 16.sp, lineHeight = 16.sp),
                        color = c.text,
                    )
                } else {
                    PersonGlyph()
                }
            }
        }
    }
}

/** Generic person (head and shoulders) for an avatar without an account. */
@Composable
private fun PersonGlyph() {
    val color = StayFocusedTheme.colors.secondary
    Box(
        Modifier
            .size(20.dp)
            .clearAndSetSemantics { }
            .drawBehind {
                drawCircle(color, radius = size.width * 0.22f, center = Offset(size.width / 2f, size.height * 0.3f))
                drawRoundRect(
                    color,
                    topLeft = Offset(size.width * 0.12f, size.height * 0.58f),
                    size = Size(size.width * 0.76f, size.height * 0.4f),
                    cornerRadius = CornerRadius(size.width * 0.3f),
                )
            },
    )
}

/** "+ New block" (primary) and "AI Describe". */
@Composable
internal fun HomeActionRow(
    onNewBlock: () -> Unit,
    onAiDescribe: () -> Unit,
    modifier: Modifier = Modifier,
    /** False while AI is switched off in settings: "AI Describe" would lead nowhere, so it is hidden. */
    showAi: Boolean = true,
) {
    val c = StayFocusedTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WhiteButton(
            text = stringResource(R.string.home_new_block),
            onClick = onNewBlock,
            modifier = Modifier.weight(1f).testTag(HOME_NEW_BLOCK_TAG),
            leadingIcon = { IconTile { Text("+", color = c.accent, fontSize = 16.sp) } },
        )
        if (showAi) AiDescribeButton(onAiDescribe)
    }
}

@Composable
private fun AiDescribeButton(onAiDescribe: () -> Unit) {
    val c = StayFocusedTheme.colors
    run {
        val describe = stringResource(R.string.home_ai_describe_description)
        Row(
            Modifier
                .testTag(HOME_AI_DESCRIBE_TAG)
                .height(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(c.panel)
                .border(1.dp, c.hairline14, RoundedCornerShape(18.dp))
                .clickable(role = Role.Button, onClick = onAiDescribe)
                .semantics { contentDescription = describe }
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.home_ai_badge),
                style = StayFocusedTheme.type.labelS.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
                color = c.onAccent,
                modifier =
                    Modifier
                        .clearAndSetSemantics { }
                        .background(c.accent, RoundedCornerShape(5.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
            )
            Text(
                stringResource(R.string.home_ai_describe),
                style = StayFocusedTheme.type.body.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Bold),
                color = c.text,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

/** Section title with an optional accent action on the right ("Manage", "All"). */
@Composable
internal fun SectionRow(
    title: String,
    action: String,
    actionTag: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonoLabel(title, modifier = Modifier.semantics(mergeDescendants = true) { })
        Box(
            Modifier
                .testTag(actionTag)
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clickable(role = Role.Button, onClick = onAction)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                action,
                style = StayFocusedTheme.type.body.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                color = StayFocusedTheme.colors.accent,
            )
        }
    }
}

/** Header of "Active blocks · this phone"; the rows follow as separate lazy items. */
@Composable
internal fun ActiveBlocksHeader(
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionRow(
        stringResource(R.string.home_blocks_title),
        stringResource(R.string.home_blocks_manage),
        HOME_MANAGE_TAG,
        onManage,
        modifier,
    )
}

@Composable
internal fun EmptyBlocks() {
    val c = StayFocusedTheme.colors
    val shape = StayFocusedTheme.shapes.card
    Column(
        Modifier
            .testTag(HOME_BLOCKS_EMPTY_TAG)
            .fillMaxWidth()
            .clip(shape)
            .background(c.panel)
            .border(1.dp, c.hairline06, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(R.string.home_blocks_empty_title), style = StayFocusedTheme.type.title, color = c.text)
        Text(stringResource(R.string.home_blocks_empty_body), style = StayFocusedTheme.type.body, color = c.secondary)
    }
}

@Composable
internal fun BlockRow(
    row: HomeBlockUi,
    onToggle: (id: String, enabled: Boolean) -> Unit,
) {
    val c = StayFocusedTheme.colors
    val shape = StayFocusedTheme.shapes.card
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.panel)
            .border(1.dp, c.hairline06, shape)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TypeChip(row.block.type, dimmed = !row.block.enabled)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                row.block.name,
                style = StayFocusedTheme.type.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                blockDetail(row.block, row.appLabels),
                style = StayFocusedTheme.type.bodyS,
                color = c.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SfToggle(
            checked = row.block.enabled,
            onCheckedChange = { onToggle(row.block.id, it) },
            modifier = Modifier.semantics { contentDescription = row.block.name },
        )
    }
}

@file:Suppress("MagicNumber")

package com.dculus.stayfocused.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dculus.stayfocused.core.model.LinkedDevice
import com.dculus.stayfocused.core.model.UnlockRequest
import com.dculus.stayfocused.core.ui.components.GlowCard
import com.dculus.stayfocused.core.ui.components.OnlinePill
import com.dculus.stayfocused.core.ui.icons.V5Icons
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.dculus.stayfocused.core.ui.theme.V5Gradients

internal const val HOME_LINK_PHONE_TAG = "home_link_phone"
internal const val HOME_CHILD_CARD_TAG = "home_child_card"

/** Brand-gradient card of one linked phone: name, online pill, today / using now / battery and the open request. */
@Composable
internal fun ChildCard(
    device: LinkedDevice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val white = StayFocusedTheme.colors.onGradient
    GlowCard(
        V5Gradients.brand,
        modifier.fillMaxWidth().testTag("$HOME_CHILD_CARD_TAG:${device.id}"),
        radius = 36.dp,
        padding = 18.dp,
        onClick = onClick,
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    device.name,
                    style = StayFocusedTheme.type.cardTitle,
                    color = white,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                OnlinePill(online = device.online, onCard = true)
            }
            val none = stringResource(R.string.home_child_none)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChildStat(
                    stringResource(R.string.home_child_today),
                    device.todayMins?.let(::clockMins) ?: none,
                    Modifier.weight(1f),
                )
                ChildStat(
                    stringResource(R.string.home_child_using_now),
                    // Telemetry of a phone that is offline is stale, so it is not shown as activity.
                    if (device.online) device.currentApp ?: none else none,
                    Modifier.weight(1f),
                )
                ChildStat(
                    stringResource(R.string.home_child_battery),
                    device.battery?.let { stringResource(R.string.home_child_battery_value, it) } ?: none,
                    Modifier.weight(1f),
                )
            }
            device.pendingRequest()?.let { RequestRow(it) }
        }
    }
}

@Composable
private fun ChildStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = StayFocusedTheme.type.label.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
            color = StayFocusedTheme.colors.onGradient,
        )
        Text(
            value,
            style = StayFocusedTheme.type.rule.copy(fontSize = 24.sp, lineHeight = 28.sp),
            color = StayFocusedTheme.colors.onGradient,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RequestRow(request: UnlockRequest) {
    val colors = StayFocusedTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(colors.surface.copy(alpha = 0.95f), RoundedCornerShape(25.dp))
            .padding(start = 16.dp, end = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.home_child_request, request.minutes, request.app),
            style = StayFocusedTheme.type.label,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier.height(38.dp).background(colors.ink, RoundedCornerShape(19.dp)).padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.home_child_review),
                style = StayFocusedTheme.type.label.copy(fontSize = 13.sp),
                color = colors.onGradient,
            )
        }
    }
}

/** No phone linked yet: the white "Link a child's phone" row. */
@Composable
internal fun LinkChildRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StayFocusedTheme.colors
    val shape = RoundedCornerShape(36.dp)
    Row(
        modifier
            .testTag(HOME_LINK_PHONE_TAG)
            .fillMaxWidth()
            .height(72.dp)
            .clip(shape)
            .background(colors.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 20.dp, end = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.home_link_child),
            style = StayFocusedTheme.type.bodyL.copy(fontWeight = FontWeight.Medium),
            color = colors.ink,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.size(50.dp).background(colors.green, CircleShape), contentAlignment = Alignment.Center) {
            Icon(V5Icons.plus, contentDescription = null, tint = colors.onGradient, modifier = Modifier.size(18.dp))
        }
    }
}

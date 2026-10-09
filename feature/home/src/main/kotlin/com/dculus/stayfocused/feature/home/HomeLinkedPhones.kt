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
import com.dculus.stayfocused.core.model.UnlockRequestStatus
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

/** "Linked phones": a row per child phone, or a dashed "Link a child's phone" button that leads to Devices. */
@Composable
internal fun LinkedPhonesSection(
    devices: List<LinkedDevice>,
    onAll: () -> Unit,
    onOpenDevice: (String) -> Unit,
    onLinkPhone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionRow(
            stringResource(R.string.home_devices_title),
            stringResource(R.string.home_devices_all),
            HOME_ALL_DEVICES_TAG,
            onAll,
        )
        devices.forEach { device -> DeviceRow(device) { onOpenDevice(device.id) } }
        if (devices.isEmpty()) LinkPhoneButton(onLinkPhone)
    }
}

@Composable
private fun DeviceRow(
    device: LinkedDevice,
    onClick: () -> Unit,
) {
    val c = StayFocusedTheme.colors
    val shape = StayFocusedTheme.shapes.card
    val badge =
        device.alerts.count { !it.dismissed } + device.requests.count { it.status == UnlockRequestStatus.PENDING }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.panel)
            .border(1.dp, c.hairline06, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).background(c.track, RoundedCornerShape(14.dp)).clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                device.name.take(1).uppercase(),
                style = StayFocusedTheme.type.title.copy(fontSize = 16.sp, lineHeight = 16.sp),
                color = c.text,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                device.name,
                style = StayFocusedTheme.type.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                LedDot(color = if (device.online) c.accent else c.tertiary, size = 7.dp)
                Text(
                    deviceStatus(device),
                    style = StayFocusedTheme.type.label.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                    color = c.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (badge > 0) {
            Box(
                Modifier
                    .sizeIn(minWidth = 24.dp, minHeight = 24.dp)
                    .background(c.alert, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    badge.toString(),
                    style = StayFocusedTheme.type.label.copy(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold),
                    color = c.onAccent,
                )
            }
        }
    }
}

/** "80% · INSTAGRAM"; falls back to ONLINE or OFFLINE when the phone reports neither battery nor app. */
@Composable
private fun deviceStatus(device: LinkedDevice): String {
    // Telemetry of a phone that is offline is stale, so it is not shown as activity.
    if (!device.online) return stringResource(R.string.home_device_offline)
    val battery = device.battery?.let { "$it%" }
    val app = device.currentApp?.uppercase()
    return when {
        battery != null && app != null -> stringResource(R.string.home_device_status, battery, app)
        battery != null -> battery
        app != null -> app
        device.online -> stringResource(R.string.home_device_online)
        else -> stringResource(R.string.home_device_offline)
    }
}

@Composable
private fun LinkPhoneButton(onClick: () -> Unit) {
    val line = StayFocusedTheme.colors.hairline14
    Box(
        Modifier
            .testTag(HOME_LINK_PHONE_TAG)
            .fillMaxWidth()
            .height(56.dp)
            .clip(StayFocusedTheme.shapes.card)
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    color = line,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    style =
                        Stroke(
                            stroke,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                        ),
                )
            }.clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.home_devices_link),
            style = StayFocusedTheme.type.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            color = StayFocusedTheme.colors.text,
        )
    }
}

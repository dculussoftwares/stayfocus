@file:Suppress("MagicNumber")

package com.dculus.stayfocused.feature.devices

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dculus.stayfocused.core.model.LinkedDevice
import com.dculus.stayfocused.core.model.TamperAlert
import com.dculus.stayfocused.core.model.TamperKind
import com.dculus.stayfocused.core.model.UnlockRequestStatus
import com.dculus.stayfocused.core.model.formatMinutes
import com.dculus.stayfocused.core.ui.components.LedDot
import com.dculus.stayfocused.core.ui.components.WhiteButton
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme

internal const val DEVICES_LIST_TAG = "devices_list"
internal const val DEVICES_LINK_TAG = "devices_link"
internal const val DEVICES_EMPTY_TAG = "devices_empty"
internal const val DEVICES_EMPTY_LINK_TAG = "devices_empty_link"
internal const val DEVICES_THIS_PHONE_TAG = "devices_this_phone"
internal const val DEVICES_BADGE_TAG = "devices_badge"
internal const val DEVICES_ALERT_TAG = "devices_alert"

internal fun deviceCardTag(id: String) = "device_card_$id"

@Composable
internal fun DevicesRoute(
    onLinkPhone: () -> Unit,
    onOpenDevice: (deviceId: String) -> Unit,
    viewModel: DevicesViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DevicesScreen(state, Build.MODEL.orEmpty(), onLinkPhone, onOpenDevice)
}

@Composable
internal fun DevicesScreen(
    state: DevicesUiState,
    thisPhoneModel: String,
    onLinkPhone: () -> Unit,
    onOpenDevice: (String) -> Unit,
) {
    val colors = StayFocusedTheme.colors
    val devices = (state as? DevicesUiState.Loaded)?.devices.orEmpty()
    LazyColumn(
        Modifier.fillMaxSize().background(colors.background).testTag(DEVICES_LIST_TAG),
        contentPadding =
            androidx.compose.foundation.layout.PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = 6.dp,
                bottom = 24.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.devices_title),
                    style = StayFocusedTheme.type.displayL,
                    color = colors.text,
                )
                WhiteButton(
                    text = stringResource(R.string.devices_link_action),
                    onClick = onLinkPhone,
                    modifier = Modifier.width(96.dp).testTag(DEVICES_LINK_TAG),
                )
            }
        }
        item { ThisPhoneRow(thisPhoneModel, devices.size) }
        items(devices, key = { it.id }) { device -> DeviceCard(device) { onOpenDevice(device.id) } }
        if (state is DevicesUiState.Loaded && devices.isEmpty()) item { EmptyDevices(onLinkPhone) }
    }
}

@Composable
private fun ThisPhoneRow(
    model: String,
    linked: Int,
) {
    val c = StayFocusedTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .testTag(DEVICES_THIS_PHONE_TAG)
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, c.hairline08, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(c.text, RoundedCornerShape(4.dp)).clearAndSetSemantics { })
        Text(
            stringResource(R.string.devices_this_phone, model.uppercase(), linked),
            style = StayFocusedTheme.type.label.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
            color = c.secondary,
        )
    }
}

@Composable
internal fun DeviceCard(
    device: LinkedDevice,
    onClick: () -> Unit,
) {
    val c = StayFocusedTheme.colors
    val shape = StayFocusedTheme.shapes.card
    val badge = device.badgeCount()
    val alertLine = deviceAlertLine(device)
    Column(
        Modifier
            .testTag(deviceCardTag(device.id))
            .fillMaxWidth()
            .clip(shape)
            .background(
                androidx.compose.ui.graphics.Brush
                    .verticalGradient(listOf(c.panelGradientStart, c.panelGradientEnd)),
            ).border(1.dp, c.hairline06, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).background(c.track, RoundedCornerShape(16.dp)).clearAndSetSemantics { },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    device.name.take(1).uppercase(),
                    style = StayFocusedTheme.type.title.copy(fontSize = 18.sp, lineHeight = 18.sp),
                    color = c.text,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    device.name,
                    style = StayFocusedTheme.type.title.copy(fontSize = 19.sp),
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (device.model.isNotBlank()) {
                    Text(
                        device.model,
                        style = StayFocusedTheme.type.label.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                        color = c.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                LedDot(color = if (device.online) c.accent else c.tertiary, pulse = device.online)
                Text(
                    stringResource(if (device.online) R.string.devices_online else R.string.devices_offline),
                    style = StayFocusedTheme.type.label.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold),
                    color = if (device.online) c.accent else c.tertiary,
                )
            }
        }
        StatGrid(device)
        if (alertLine != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(c.alert, RoundedCornerShape(4.dp)).clearAndSetSemantics { })
                Text(
                    alertLine,
                    style = StayFocusedTheme.type.body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    color = c.alertText,
                    modifier = Modifier.weight(1f).testTag(DEVICES_ALERT_TAG),
                )
                if (badge > 0) {
                    Box(
                        Modifier
                            .testTag(DEVICES_BADGE_TAG)
                            .size(width = 24.dp, height = 24.dp)
                            .background(c.alert, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            badge.toString(),
                            style =
                                StayFocusedTheme.type.label.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                ),
                            color = c.onAccent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatGrid(device: LinkedDevice) {
    val c = StayFocusedTheme.colors
    val none = stringResource(R.string.devices_stat_none)
    val today = device.todayMins?.let { formatMinutes(it) } ?: none
    val battery = device.battery?.let { "$it%" } ?: none
    // A phone that is offline last reported a while ago, so what it was using is not shown as current.
    val using = if (device.online) device.currentApp ?: none else none
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.hairline06),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Stat(stringResource(R.string.devices_stat_today), today, 16.sp, Modifier.weight(1f))
        Stat(stringResource(R.string.devices_stat_battery), battery, 16.sp, Modifier.weight(1f))
        Stat(stringResource(R.string.devices_stat_using), using, 14.sp, Modifier.weight(1f))
    }
}

@Composable
private fun Stat(
    label: String,
    value: String,
    valueSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
) {
    val c = StayFocusedTheme.colors
    Column(modifier.background(c.panel).padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            label,
            style = StayFocusedTheme.type.label.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Medium),
            color = c.secondary,
        )
        Text(
            value,
            style = StayFocusedTheme.type.label.copy(fontSize = valueSize, fontWeight = FontWeight.Bold),
            color = c.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "Accessibility turned off · 1 request for more time", or null when there is nothing to flag. */
@Composable
internal fun deviceAlertLine(device: LinkedDevice): String? {
    val parts = mutableListOf<String>()
    device.alerts
        .filter { !it.dismissed }
        .map { alertText(it) }
        .distinct()
        .forEach { parts += it }
    val pending = device.requests.count { it.status == UnlockRequestStatus.PENDING }
    if (pending > 0) parts += pluralStringResource(R.plurals.devices_requests_line, pending, pending)
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Composable
private fun alertText(alert: TamperAlert): String =
    when (alert.kind) {
        TamperKind.PERMISSION_LOST -> {
            stringResource(
                if (alert.permission.equals("accessibility", ignoreCase = true)) {
                    R.string.devices_alert_accessibility
                } else {
                    R.string.devices_alert_permission
                },
            )
        }

        TamperKind.UNLINKED -> {
            stringResource(R.string.devices_alert_unlinked)
        }

        TamperKind.OFFLINE -> {
            stringResource(R.string.devices_alert_offline)
        }
    }

@Composable
private fun EmptyDevices(onLinkPhone: () -> Unit) {
    val c = StayFocusedTheme.colors
    val line = c.hairline14
    Column(
        Modifier
            .testTag(DEVICES_EMPTY_TAG)
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    color = line,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(26.dp.toPx()),
                    style =
                        Stroke(
                            stroke,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                        ),
                )
            }.padding(horizontal = 20.dp, vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.devices_empty_title),
            style = StayFocusedTheme.type.title.copy(fontSize = 18.sp),
            color = c.text,
        )
        Text(
            stringResource(R.string.devices_empty_body),
            style = StayFocusedTheme.type.body.copy(fontSize = 13.sp, lineHeight = 19.sp),
            color = c.secondary,
            textAlign = TextAlign.Center,
        )
        Box(Modifier.height(6.dp))
        WhiteButton(
            text = stringResource(R.string.devices_empty_cta),
            onClick = onLinkPhone,
            modifier = Modifier.testTag(DEVICES_EMPTY_LINK_TAG),
        )
    }
}

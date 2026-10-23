@file:Suppress("MagicNumber", "LongMethod")

package com.dculus.stayfocused.feature.devices

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import com.dculus.stayfocused.core.model.hasDerivedOfflineAlert
import com.dculus.stayfocused.core.ui.art.LinkArt
import com.dculus.stayfocused.core.ui.charts.HourTicks
import com.dculus.stayfocused.core.ui.components.GlowCard
import com.dculus.stayfocused.core.ui.components.LocalTabBarPadding
import com.dculus.stayfocused.core.ui.components.OnlinePill
import com.dculus.stayfocused.core.ui.components.V5Card
import com.dculus.stayfocused.core.ui.icons.V5Icons
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.dculus.stayfocused.core.ui.theme.V5Gradients
import com.dculus.stayfocused.core.ui.theme.V5Screen

internal const val DEVICES_LIST_TAG = "devices_list"
internal const val DEVICES_LINK_TAG = "devices_link"
internal const val DEVICES_EMPTY_TAG = "devices_empty"
internal const val DEVICES_THIS_PHONE_TAG = "devices_this_phone"
internal const val DEVICES_ALERT_TAG = "devices_alert"
internal const val DEVICES_LAST_SEEN_TAG = "devices_last_seen"

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
    V5Screen(darkContent = false) {
        val spacing = StayFocusedTheme.spacing
        val devices = (state as? DevicesUiState.Loaded)?.devices.orEmpty()
        LazyColumn(
            Modifier.fillMaxSize().testTag(DEVICES_LIST_TAG),
            contentPadding =
                PaddingValues(
                    start = spacing.screenTabs,
                    end = spacing.screenTabs,
                    // The prototype's 58 dp top padding includes its 46 dp fake status bar.
                    top = 12.dp,
                    bottom = 24.dp + LocalTabBarPadding.current,
                ),
            verticalArrangement = Arrangement.spacedBy(spacing.gap12),
        ) {
            item {
                Column(Modifier.padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.devices_title),
                        style = StayFocusedTheme.type.title,
                        color = StayFocusedTheme.colors.ink,
                    )
                    Text(
                        stringResource(R.string.devices_subtitle),
                        style = StayFocusedTheme.type.body.copy(fontSize = 15.sp),
                        color = StayFocusedTheme.colors.textSecondary,
                    )
                }
            }
            item { ThisPhoneRow(thisPhoneModel, devices.size) }
            items(devices, key = { it.id }) { device -> DeviceCard(device) { onOpenDevice(device.id) } }
            if (state is DevicesUiState.Loaded && devices.isEmpty()) item { EmptyDevices() }
            item { LinkTile(onLinkPhone) }
            item {
                Text(
                    stringResource(R.string.devices_link_helper),
                    style = StayFocusedTheme.type.body.copy(lineHeight = 22.sp),
                    color = StayFocusedTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
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
            .border(1.dp, c.hairline, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(c.ink, CircleShape).clearAndSetSemantics { })
        Text(
            stringResource(R.string.devices_this_phone, model.uppercase(), linked),
            style = StayFocusedTheme.type.tag,
            color = c.textSecondary,
        )
    }
}

@Composable
internal fun DeviceCard(
    device: LinkedDevice,
    onClick: () -> Unit,
) {
    val c = StayFocusedTheme.colors
    val attention = deviceAttentionLine(device)
    V5Card(
        Modifier.fillMaxWidth().testTag(deviceCardTag(device.id)),
        radius = 34.dp,
        onClick = onClick,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        device.name,
                        style = StayFocusedTheme.type.cardTitle,
                        color = c.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    deviceSubtitle(device)?.let {
                        Text(
                            it,
                            style = StayFocusedTheme.type.body.copy(fontSize = 13.sp),
                            color = c.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    OnlinePill(online = device.online)
                    if (!device.online) {
                        lastSeenText(device.lastSeen)?.let {
                            Text(
                                it,
                                style = StayFocusedTheme.type.tag,
                                color = c.textSecondary,
                                modifier = Modifier.testTag(DEVICES_LAST_SEEN_TAG),
                            )
                        }
                    }
                }
            }
            StatRow(device)
            device.hourlyMins?.let {
                HourTicks(it, stringResource(R.string.devices_hour_ticks_description))
            }
            if (attention != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).background(c.red, CircleShape).clearAndSetSemantics { })
                    Text(
                        attention,
                        style = StayFocusedTheme.type.label,
                        color = c.destructive,
                        modifier = Modifier.testTag(DEVICES_ALERT_TAG),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatRow(device: LinkedDevice) {
    val none = stringResource(R.string.devices_stat_none)
    val today = device.todayMins?.let { formatMinutes(it) } ?: none
    val battery = device.battery?.let { "$it%" } ?: none
    // A phone that is offline last reported a while ago, so what it was using is not shown as current.
    val using = if (device.online) device.currentApp ?: none else none
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Stat(stringResource(R.string.devices_stat_today), today, Modifier.weight(1f))
        Stat(stringResource(R.string.devices_stat_using), using, Modifier.weight(1f))
        Stat(stringResource(R.string.devices_stat_battery), battery, Modifier.weight(1f))
    }
}

@Composable
private fun Stat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val c = StayFocusedTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = StayFocusedTheme.type.body.copy(fontSize = 13.sp), color = c.textSecondary)
        Text(value, style = StayFocusedTheme.type.rule, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "Pixel 7a · Android 15"; the Android version is left out until the phone has reported it. */
@Composable
internal fun deviceSubtitle(device: LinkedDevice): String? {
    val version = device.osVersion?.takeIf { it.isNotBlank() }
    return when {
        device.model.isNotBlank() && version != null -> {
            stringResource(R.string.devices_model_android, device.model, version)
        }

        device.model.isNotBlank() -> {
            device.model
        }

        version != null -> {
            stringResource(R.string.devices_android, version)
        }

        else -> {
            null
        }
    }
}

/** "1 request waiting · Protection is off", or null when there is nothing to flag. */
@Composable
internal fun deviceAttentionLine(device: LinkedDevice): String? {
    val parts = mutableListOf<String>()
    val pending = device.requests.count { it.status == UnlockRequestStatus.PENDING }
    if (pending > 0) parts += pluralStringResource(R.plurals.devices_requests_line, pending, pending)
    val open = device.alerts.filter { !it.dismissed }
    if (open.any { it.kind == TamperKind.PERMISSION_LOST }) parts += stringResource(R.string.devices_protection_off)
    if (open.any { it.kind == TamperKind.UNLINKED }) parts += stringResource(R.string.devices_alert_unlinked)
    if (device.hasDerivedOfflineAlert || open.any { it.kind == TamperKind.OFFLINE }) {
        parts += stringResource(R.string.devices_alert_offline)
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Composable
private fun LinkTile(onClick: () -> Unit) {
    GlowCard(
        V5Gradients.pinkLink,
        Modifier.fillMaxWidth().height(236.dp).testTag(DEVICES_LINK_TAG),
        padding = 0.dp,
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize()) {
            Text(
                stringResource(R.string.devices_link_title),
                style = StayFocusedTheme.type.cardTitle.copy(fontSize = 22.sp),
                modifier = Modifier.padding(20.dp),
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(50.dp)
                    .background(StayFocusedTheme.colors.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    V5Icons.plus,
                    contentDescription = null,
                    tint = StayFocusedTheme.colors.ink,
                    modifier = Modifier.size(19.dp),
                )
            }
            LinkArt(Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
        }
    }
}

@Composable
private fun EmptyDevices() {
    V5Card(Modifier.fillMaxWidth().testTag(DEVICES_EMPTY_TAG), radius = 28.dp) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.devices_empty_title),
                style = StayFocusedTheme.type.cardTitle,
                color = StayFocusedTheme.colors.ink,
            )
            Text(
                stringResource(R.string.devices_empty_body),
                style = StayFocusedTheme.type.body,
                color = StayFocusedTheme.colors.textSecondary,
                textAlign = TextAlign.Start,
            )
        }
    }
}

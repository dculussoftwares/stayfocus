package com.dculus.stayfocused.feature.devices

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.dculus.stayfocused.core.ui.components.NavPlaceholderScreen
import com.dculus.stayfocused.core.ui.components.PlaceholderAction

private const val PLACEHOLDER_DEVICE_ID = "placeholder-device"
private const val PLACEHOLDER_TOKEN = "placeholder-token"

@Composable
internal fun DevicesRoute(
    onLinkPhone: () -> Unit,
    onOpenDevice: (deviceId: String) -> Unit,
) {
    val link = stringResource(R.string.devices_placeholder_link)
    val open = stringResource(R.string.devices_placeholder_open_device)
    val actions =
        remember(link, open, onLinkPhone, onOpenDevice) {
            listOf(
                PlaceholderAction(link, onLinkPhone),
                PlaceholderAction(open) { onOpenDevice(PLACEHOLDER_DEVICE_ID) },
            )
        }
    NavPlaceholderScreen(title = stringResource(R.string.devices_title), actions = actions)
}

@Composable
internal fun LinkAddRoute(onScan: () -> Unit) {
    val scan = stringResource(R.string.devices_placeholder_scan)
    val actions = remember(scan, onScan) { listOf(PlaceholderAction(scan, onScan)) }
    NavPlaceholderScreen(title = stringResource(R.string.link_add_title), actions = actions)
}

@Composable
internal fun LinkScanRoute(onScan: (token: String) -> Unit) {
    val scanned = stringResource(R.string.devices_placeholder_scanned)
    val actions = remember(scanned, onScan) { listOf(PlaceholderAction(scanned) { onScan(PLACEHOLDER_TOKEN) }) }
    NavPlaceholderScreen(title = stringResource(R.string.link_scan_title), actions = actions)
}

@Composable
internal fun LinkConfirmRoute() {
    NavPlaceholderScreen(title = stringResource(R.string.link_confirm_title))
}

@Composable
internal fun RemoteDeviceRoute() {
    NavPlaceholderScreen(title = stringResource(R.string.remote_device_title))
}

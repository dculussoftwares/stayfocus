package com.dculus.stayfocused.feature.devices

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.dculus.stayfocused.core.navigation.Devices
import com.dculus.stayfocused.core.navigation.LinkAdd
import com.dculus.stayfocused.core.navigation.LinkConfirm
import com.dculus.stayfocused.core.navigation.LinkScan
import com.dculus.stayfocused.core.navigation.RemoteDevice

fun NavController.navigateToDevices(navOptions: NavOptions? = null) = navigate(Devices, navOptions)

fun NavController.navigateToLinkAdd(navOptions: NavOptions? = null) = navigate(LinkAdd, navOptions)

fun NavController.navigateToLinkScan(navOptions: NavOptions? = null) = navigate(LinkScan, navOptions)

fun NavController.navigateToLinkConfirm(
    token: String,
    navOptions: NavOptions? = null,
) = navigate(LinkConfirm(token), navOptions)

fun NavController.navigateToRemoteDevice(
    deviceId: String,
    navOptions: NavOptions? = null,
) = navigate(RemoteDevice(deviceId), navOptions)

/** Devices tab: [onLinkPhone] starts linking, [onOpenDevice] opens a child phone. */
fun NavGraphBuilder.devicesScreen(
    onLinkPhone: () -> Unit,
    onOpenDevice: (deviceId: String) -> Unit,
) {
    composable<Devices> { DevicesRoute(onLinkPhone, onOpenDevice) }
}

fun NavGraphBuilder.linkAddScreen(onScan: () -> Unit) {
    composable<LinkAdd> { LinkAddRoute(onScan) }
}

fun NavGraphBuilder.linkScanScreen(onScan: (token: String) -> Unit) {
    composable<LinkScan> { LinkScanRoute(onScan) }
}

fun NavGraphBuilder.linkConfirmScreen() {
    composable<LinkConfirm> { LinkConfirmRoute() }
}

/** A linked child phone, shown with the tab bar. */
fun NavGraphBuilder.remoteDeviceScreen() {
    composable<RemoteDevice> { RemoteDeviceRoute() }
}

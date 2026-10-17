package com.dculus.stayfocused.feature.devices

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
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
    viewModelProvider: (@Composable () -> DevicesViewModel)? = null,
) {
    composable<Devices> {
        DevicesRoute(onLinkPhone, onOpenDevice, viewModelProvider?.invoke() ?: hiltViewModel())
    }
}

/** "Link a child's phone": [onScan] opens the camera; [onSignIn] when the parent has no account yet. */
fun NavGraphBuilder.linkAddScreen(
    onBack: () -> Unit,
    onScan: () -> Unit,
    onSignIn: () -> Unit,
    viewModelProvider: (@Composable () -> LinkAddViewModel)? = null,
) {
    composable<LinkAdd> {
        LinkAddRoute(onBack, onScan, onSignIn, viewModelProvider?.invoke() ?: hiltViewModel())
    }
}

/** The QR scanner. [onScan] gets the pairing token of a valid code; [onMessage] shows a toast for a bad one. */
fun NavGraphBuilder.linkScanScreen(
    onBack: () -> Unit,
    onScan: (token: String) -> Unit,
    onMessage: (String) -> Unit,
) {
    composable<LinkScan> { LinkScanRoute(onBack, onScan, onMessage) }
}

/**
 * Confirm link. [onLinked] gets the new phone's id and the "Phone linked" toast text once the child has allowed it;
 * the caller shows the toast and opens the remote device.
 */
fun NavGraphBuilder.linkConfirmScreen(
    onBack: () -> Unit,
    onLinked: (deviceId: String, message: String) -> Unit,
    viewModelProvider: (@Composable () -> LinkConfirmViewModel)? = null,
) {
    composable<LinkConfirm> {
        LinkConfirmRoute(onBack, onLinked, viewModelProvider?.invoke() ?: hiltViewModel())
    }
}

/** A linked child phone, shown with the tab bar. */
fun NavGraphBuilder.remoteDeviceScreen() {
    composable<RemoteDevice> { RemoteDeviceRoute() }
}

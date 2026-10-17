package com.dculus.stayfocused.feature.devices

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dculus.stayfocused.core.ui.components.NavPlaceholderScreen

@Composable
internal fun RemoteDeviceRoute() {
    NavPlaceholderScreen(title = stringResource(R.string.remote_device_title))
}

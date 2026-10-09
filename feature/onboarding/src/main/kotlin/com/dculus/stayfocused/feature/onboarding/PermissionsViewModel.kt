package com.dculus.stayfocused.feature.onboarding

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dculus.stayfocused.core.blocking.permissions.PermissionsRepository
import com.dculus.stayfocused.core.model.Permissions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The four rows of the system check, in display order. */
internal enum class PermissionRow(
    @StringRes val title: Int,
    @StringRes val why: Int,
    val tag: String,
) {
    Usage(R.string.perms_usage_title, R.string.perms_usage_why, "usage"),
    Accessibility(R.string.perms_accessibility_title, R.string.perms_accessibility_why, "accessibility"),
    Overlay(R.string.perms_overlay_title, R.string.perms_overlay_why, "overlay"),
    Notifications(R.string.perms_notifications_title, R.string.perms_notifications_why, "notifications"),
    ;

    fun isGranted(permissions: Permissions): Boolean =
        when (this) {
            Usage -> permissions.usage
            Accessibility -> permissions.accessibility
            Overlay -> permissions.overlay
            Notifications -> permissions.notifications
        }
}

internal fun Permissions.grantedCount(): Int = PermissionRow.entries.count { it.isGranted(this) }

/** What tapping Allow on Notifications should do. */
internal enum class NotificationStep { RequestRuntime, OpenSettings }

/** Live permission state for the system check. Rows update when the user returns from a Settings screen. */
@HiltViewModel
class PermissionsViewModel
    @Inject
    constructor(
        repository: PermissionsRepository,
    ) : ViewModel() {
        internal val permissions: StateFlow<Permissions> =
            repository
                .observe()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), repository.current())

        private var notificationRequests = 0

        /**
         * The runtime dialog is tried once; if it did not grant (the user said no, or Android no longer shows it),
         * or no runtime permission exists (below Android 13), the next tap opens the app's notification settings.
         */
        internal fun nextNotificationStep(runtimePermissionExists: Boolean): NotificationStep =
            if (runtimePermissionExists && notificationRequests++ == 0) {
                NotificationStep.RequestRuntime
            } else {
                NotificationStep.OpenSettings
            }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

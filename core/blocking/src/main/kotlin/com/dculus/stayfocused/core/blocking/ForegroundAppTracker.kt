package com.dculus.stayfocused.core.blocking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns raw window-state events into a stable [foreground] value.
 *
 * Ignored events: our own block screen, System UI (notification shade, recents overlay), keyboards,
 * windows that aren't activities (dialogs, popups) and consecutive duplicates (except the first event after
 * the block screen was shown).
 */
@Singleton
class ForegroundAppTracker
    @Inject
    constructor(
        private val env: ForegroundEnvironment,
    ) {
        private val _foreground = MutableStateFlow<ForegroundApp?>(null)

        /** The current foreground app, or `null` while the screen is off or nothing has been seen yet. */
        val foreground: StateFlow<ForegroundApp?> = _foreground.asStateFlow()

        @Volatile private var blockScreenUp = false

        fun onWindowStateChanged(
            packageName: String?,
            className: String?,
        ) {
            if (packageName.isNullOrEmpty() || className.isNullOrEmpty()) return
            val reason = ignoreReason(packageName, className)
            if (reason != null) {
                BlockingLog.log("Tracker", "ignored $packageName/$className ($reason)")
                return
            }
            blockScreenUp = false
            BlockingLog.log("Tracker", "foreground $packageName/$className")
            _foreground.value = ForegroundApp(packageName, env.nowMillis())
        }

        /**
         * The block screen was put in front of the foreground app. Its own window events are ignored, so the next
         * event for that same app (reopened from recents, say) is a real re-entry and must not be dropped as a
         * duplicate.
         */
        fun onBlockScreenShown() {
            blockScreenUp = true
        }

        private fun ignoreReason(
            packageName: String,
            className: String,
        ): String? =
            when {
                packageName == SYSTEM_UI_PACKAGE -> "system ui"
                packageName == env.ownPackage && className == BLOCK_SCREEN_CLASS -> "block screen"
                packageName in env.imePackages() -> "keyboard"
                env.isActivity(packageName, className) == false -> "not an activity"
                _foreground.value?.packageName == packageName && !blockScreenUp -> "duplicate"
                else -> null
            }

        /** Screen turned off: nobody is using an app, so time must not be counted. */
        fun onScreenOff() {
            BlockingLog.log("Tracker", "screen off / reset")
            blockScreenUp = false
            _foreground.value = null
        }

        companion object {
            const val SYSTEM_UI_PACKAGE = "com.android.systemui"

            /** Class name of the block screen activity (`screen.BlockActivity`). */
            const val BLOCK_SCREEN_CLASS = "com.dculus.stayfocused.core.blocking.screen.BlockActivity"
        }
    }

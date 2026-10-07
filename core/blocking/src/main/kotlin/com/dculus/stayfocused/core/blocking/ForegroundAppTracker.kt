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
 * windows that aren't activities (dialogs, popups) and consecutive duplicates.
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

        fun onWindowStateChanged(
            packageName: String?,
            className: String?,
        ) {
            if (packageName.isNullOrEmpty() || className.isNullOrEmpty()) return
            if (shouldIgnore(packageName, className)) return
            _foreground.value = ForegroundApp(packageName, env.nowMillis())
        }

        private fun shouldIgnore(
            packageName: String,
            className: String,
        ): Boolean =
            packageName == SYSTEM_UI_PACKAGE ||
                (packageName == env.ownPackage && className == BLOCK_SCREEN_CLASS) ||
                packageName in env.imePackages() ||
                env.isActivity(packageName, className) == false ||
                _foreground.value?.packageName == packageName

        /** Screen turned off: nobody is using an app, so time must not be counted. */
        fun onScreenOff() {
            _foreground.value = null
        }

        companion object {
            const val SYSTEM_UI_PACKAGE = "com.android.systemui"

            /** Class name of the block screen activity (added in a later M2 story). */
            const val BLOCK_SCREEN_CLASS = "com.dculus.stayfocused.core.blocking.screen.BlockActivity"
        }
    }

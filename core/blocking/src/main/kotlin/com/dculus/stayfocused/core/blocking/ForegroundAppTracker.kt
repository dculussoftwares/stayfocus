package com.dculus.stayfocused.core.blocking

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Turns raw window-state events into a stable [foreground] value.
 *
 * Ignored events: our own block screen, System UI (notification shade, recents overlay), keyboards,
 * windows that aren't activities (dialogs, popups) and consecutive duplicates.
 */
@Singleton
class ForegroundAppTracker @Inject constructor(
    private val env: ForegroundEnvironment,
) {
    private val _foreground = MutableStateFlow<ForegroundApp?>(null)

    /** The current foreground app, or `null` while the screen is off or nothing has been seen yet. */
    val foreground: StateFlow<ForegroundApp?> = _foreground.asStateFlow()

    fun onWindowStateChanged(packageName: String?, className: String?) {
        if (packageName.isNullOrEmpty() || className.isNullOrEmpty()) return
        if (packageName == SYSTEM_UI_PACKAGE) return
        if (packageName == env.ownPackage && className == BLOCK_SCREEN_CLASS) return
        if (packageName in env.imePackages()) return
        if (env.isActivity(packageName, className) == false) return
        if (_foreground.value?.packageName == packageName) return
        _foreground.value = ForegroundApp(packageName, env.nowMillis())
    }

    /** Screen turned off: nobody is using an app, so time must not be counted. */
    fun onScreenOff() {
        _foreground.value = null
    }

    companion object {
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"

        /** Class name of the block screen activity (added in a later M2 story). */
        const val BLOCK_SCREEN_CLASS = "com.dculus.stayfocused.core.blocking.BlockActivity"
    }
}

package com.dculus.stayfocused.core.blocking.screen

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import com.dculus.stayfocused.core.blocking.BlockingLog

/**
 * Where the block screen's windows come from and how the user is sent home. One implementation per engine mode:
 * [AccessibilitySurface] (the accessibility service) and [OverlaySurface] ("Display over other apps").
 */
internal interface BlockSurface {
    /** Context the windows are created with. */
    val windowContext: Context

    /** `WindowManager.LayoutParams.type` of the full-screen block window and the warning pill. */
    val windowType: Int

    /** Moves the blocked app out of the way. False when nothing happened (the caller keeps the block window up). */
    fun goHome(): Boolean
}

internal class AccessibilitySurface(
    private val service: AccessibilityService,
) : BlockSurface {
    override val windowContext: Context get() = service
    override val windowType: Int get() = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

    override fun goHome(): Boolean = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
}

/**
 * Standard engine (no Accessibility): a `TYPE_APPLICATION_OVERLAY` window, which needs "Display over other apps".
 * The same permission lets the app start [BlockActivity] and the home screen from the background.
 */
internal class OverlaySurface(
    private val context: Context,
) : BlockSurface {
    override val windowContext: Context get() = context
    override val windowType: Int get() = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

    fun isAvailable(): Boolean = Settings.canDrawOverlays(context)

    override fun goHome(): Boolean =
        try {
            context.startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        } catch (e: ActivityNotFoundException) {
            BlockingLog.log("Surface", "no home activity: ${e.message}")
            false
        } catch (e: SecurityException) {
            BlockingLog.log("Surface", "home start refused: ${e.message}")
            false
        }
}

/**
 * Adds an overlay window; returns why it could not be attached, or null. A disconnected service
 * ([WindowManager.BadTokenException]), a missing display and a revoked "Display over other apps"
 * ([SecurityException]) all mean "no window", never a crash.
 */
internal fun WindowManager.tryAddOverlay(
    view: View,
    params: WindowManager.LayoutParams,
): Exception? =
    try {
        addView(view, params)
        null
    } catch (e: WindowManager.BadTokenException) {
        e
    } catch (e: WindowManager.InvalidDisplayException) {
        e
    } catch (e: SecurityException) {
        e
    }

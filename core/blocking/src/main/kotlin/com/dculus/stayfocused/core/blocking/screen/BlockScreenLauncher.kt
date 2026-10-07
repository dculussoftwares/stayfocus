package com.dculus.stayfocused.core.blocking.screen

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.dculus.stayfocused.core.blocking.evaluator.Decision
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Optional
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Puts the block screen in front of a blocked app.
 *
 * 1. `performGlobalAction(GLOBAL_ACTION_HOME)` moves the blocked app out of the way.
 * 2. `startActivity(BlockActivity, NEW_TASK)` from the accessibility service.
 * 3. If [BlockActivity] isn't visible after [LAUNCH_CHECK_MS] (Android silently drops background activity
 *    starts it doesn't allow), a `TYPE_ACCESSIBILITY_OVERLAY` window from the service shows the same screen.
 *
 * Which path each API level uses is documented in `docs/blocking/block-screen-launch.md`.
 */
@Singleton
class BlockScreenLauncher
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val decisionSource: BlockDecisionSource,
        private val extras: Optional<BlockScreenExtras>,
    ) {
        private val handler = Handler(Looper.getMainLooper())
        private var service: AccessibilityService? = null
        private var overlay: AccessibilityOverlay? = null
        private var launchGeneration = 0L

        /** Called by the service when it connects. */
        fun attach(service: AccessibilityService) {
            this.service = service
        }

        /** Called by the service when it unbinds or is destroyed. */
        fun detach() {
            handler.removeCallbacksAndMessages(null)
            dismissOverlay()
            service = null
        }

        fun show(
            pkg: String,
            decision: Decision.Block,
        ) {
            handler.post { showOnMain(pkg, decision) }
        }

        /** Removes the fallback overlay, if showing. The engine calls this when the user leaves the blocked app. */
        fun dismissOverlay() {
            handler.post {
                launchGeneration++
                overlay?.remove()
                overlay = null
            }
        }

        private fun showOnMain(
            pkg: String,
            decision: Decision.Block,
        ) {
            val generation = ++launchGeneration
            val svc = service ?: return
            val request = BlockRequest(pkg, appLabel(pkg), decision)
            svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            if (!startBlockActivity(request)) {
                showOverlay(svc, request)
                return
            }
            handler.postDelayed({
                if (generation == launchGeneration && service === svc && !BlockScreenState.visible) {
                    showOverlay(svc, request)
                }
            }, LAUNCH_CHECK_MS)
        }

        private fun startBlockActivity(request: BlockRequest): Boolean =
            try {
                context.startActivity(request.toIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "BlockActivity launch failed, using overlay", e)
                false
            } catch (e: SecurityException) {
                Log.w(TAG, "BlockActivity launch refused, using overlay", e)
                false
            }

        private fun showOverlay(
            svc: AccessibilityService,
            request: BlockRequest,
        ) {
            overlay?.remove()
            overlay =
                AccessibilityOverlay(svc, request, decisionSource, extras.orElse(null)) { overlay = null }
                    .also { it.add() }
        }

        private fun appLabel(pkg: String): String? =
            try {
                context.packageManager
                    .getApplicationInfo(pkg, 0)
                    .loadLabel(context.packageManager)
                    .toString()
            } catch (_: PackageManager.NameNotFoundException) {
                // Not visible to us (package visibility) or uninstalled: the screen falls back to the package name.
                null
            }

        private companion object {
            const val TAG = "BlockScreenLauncher"
            const val LAUNCH_CHECK_MS = 350L
        }
    }

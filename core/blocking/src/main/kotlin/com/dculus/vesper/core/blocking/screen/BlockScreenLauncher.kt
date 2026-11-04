package com.dculus.vesper.core.blocking.screen

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.dculus.vesper.core.blocking.BlockingLog
import com.dculus.vesper.core.blocking.ForegroundAppTracker
import com.dculus.vesper.core.blocking.R
import com.dculus.vesper.core.blocking.evaluator.Decision
import com.dculus.vesper.core.blocking.evaluator.WarningKind
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.util.Optional
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Puts the block screen in front of a blocked app, through the [BlockSurface] of the running engine mode.
 *
 * 1. [BlockSurface.goHome] moves the blocked app out of the way (`GLOBAL_ACTION_HOME`, or the HOME intent).
 * 2. `startActivity(BlockActivity, NEW_TASK)`.
 * 3. If [BlockActivity] isn't visible after [LAUNCH_CHECK_MS] (Android silently drops background activity
 *    starts it doesn't allow), a full-screen overlay window of the surface's type shows the same screen.
 *
 * Which path each API level uses is documented in `docs/blocking/block-screen-launch.md`.
 */
@Singleton
@Suppress("TooManyFunctions") // one small function per step of the launch
class BlockScreenLauncher
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val decisionSource: BlockDecisionSource,
        private val clock: Clock,
        private val extras: Optional<BlockScreenExtras>,
        private val tracker: ForegroundAppTracker,
    ) : BlockPresenter {
        private val handler = Handler(Looper.getMainLooper())
        private var accessibility: AccessibilitySurface? = null
        private val standard = OverlaySurface(context)

        @Volatile private var overlay: BlockOverlayWindow? = null
        private var lastToken = 0L
        private var pendingCheck: Runnable? = null
        private var pill: UsageWarningPill? = null
        private var pillSurface: BlockSurface? = null
        private var timer: AppTimerChip? = null
        private var timerSurface: BlockSurface? = null

        /** True from [show] until the launch is settled (activity visible, overlay up, or cancelled). */
        @Volatile private var launching = false

        private var lastRequest: BlockRequest? = null
        private var lastLaunchAt = 0L

        init {
            // The home action can finish after the activity started and push it behind the launcher: cover that too.
            BlockScreenState.onPushedBack = { token -> handler.post { onPushedBack(token) } }
            // The activity made it to the screen (possibly late): the fallback overlay must not stay on top of it.
            BlockScreenState.onVisible = { handler.post { removeOverlay() } }
        }

        /** Within [PUSH_BACK_WINDOW_MS] of a launch a stop is the home action racing it, not the user leaving. */
        private fun onPushedBack(token: Long) {
            val request = lastRequest
            val svc = surface()
            if (request == null || svc == null || !isPushedBackLaunch(token)) return
            BlockingLog.log("Launcher", "block screen pushed behind after launch, overlay for ${request.pkg}")
            showOverlay(svc, request)
        }

        private fun isPushedBackLaunch(token: Long): Boolean =
            token == lastToken && BlockScreenState.activeToken == token && overlay == null &&
                SystemClock.elapsedRealtime() - lastLaunchAt <= PUSH_BACK_WINDOW_MS

        /** Called by the service when it connects. */
        fun attach(service: AccessibilityService) {
            accessibility = AccessibilitySurface(service)
        }

        /** The surface for the current engine mode: Accessibility when attached, else "Display over other apps". */
        private fun surface(): BlockSurface? = accessibility ?: standard.takeIf { it.isAvailable() }

        /** Called by the service when it unbinds or is destroyed. */
        fun detach() {
            handler.post {
                removePill()
                removeTimer()
            }
            invalidate()
            accessibility = null
        }

        override fun isShowing(): Boolean = launching || BlockScreenState.visible || overlay != null

        override fun show(
            pkg: String,
            decision: Decision.Block,
        ) {
            launching = true
            handler.post {
                removePill()
                removeTimer()
            }
            tracker.onBlockScreenShown()
            BlockingLog.log("Launcher", "show $pkg ${decision.reason}")
            handler.post { showOnMain(pkg, decision) }
        }

        override fun showWarning(
            pkg: String,
            kind: WarningKind,
            glow: Boolean,
        ) {
            handler.post {
                val svc = surface()
                if (svc == null) {
                    BlockingLog.log(
                        "Launcher",
                        "warning $kind for $pkg dropped: no surface (no Accessibility, no overlay permission)",
                    )
                    return@post
                }
                if (isShowing()) {
                    BlockingLog.log("Launcher", "warning $kind for $pkg dropped: block screen showing")
                    return@post
                }
                BlockingLog.log("Launcher", "warning $kind for $pkg shown")
                val app = appLabel(pkg) ?: pkg
                val urgent = kind != WarningKind.HALF
                val text =
                    context.getString(
                        when (kind) {
                            WarningKind.HALF -> R.string.usage_warning_half
                            WarningKind.ONE_MIN -> R.string.usage_warning_one_min
                            WarningKind.FINAL -> R.string.usage_warning_final
                        },
                        app,
                    )
                // The warning takes the stage: the timer tab tucks itself in.
                timer?.collapse()
                if (pillSurface !== svc) removePill()
                val shown =
                    pill ?: UsageWarningPill(svc, handler).also {
                        pill = it
                        pillSurface = svc
                    }
                val shownMs =
                    when (kind) {
                        WarningKind.HALF -> HALF_SHOWN_MS
                        WarningKind.ONE_MIN -> ONE_MIN_SHOWN_MS
                        WarningKind.FINAL -> FINAL_SHOWN_MS
                    }
                shown.show(text, urgent, shownMs, glow)
            }
        }

        override fun showAppTimer(
            pkg: String,
            remainingMs: Long,
            totalMs: Long,
            expanded: Boolean,
        ) {
            handler.post {
                val svc = surface()
                if (svc == null || isShowing()) {
                    BlockingLog.log("Launcher", "app timer for $pkg dropped: no surface or block screen showing")
                    return@post
                }
                if (timerSurface !== svc) removeTimer()
                val shown =
                    timer ?: AppTimerChip(svc, handler).also {
                        timer = it
                        timerSurface = svc
                    }
                shown.show(appLabel(pkg) ?: pkg, remainingMs, totalMs, expanded)
            }
        }

        override fun hideAppTimer() {
            handler.post { timer?.hide() }
        }

        private fun removeTimer() {
            timer?.remove()
            timer = null
            timerSurface = null
        }

        private fun removePill() {
            pill?.remove()
            pill = null
            pillSurface = null
        }

        /** Removes the fallback overlay, if showing. The engine calls this when the user leaves the blocked app. */
        override fun dismissOverlay() {
            handler.post { invalidate() }
        }

        /** Drops the pending launch check, makes a late-starting activity finish, and removes the overlay. */
        private fun invalidate() {
            launching = false
            BlockScreenState.activeToken = 0L
            cancelCheck()
            removeOverlay()
        }

        private fun removeOverlay() {
            overlay?.remove()
            overlay = null
        }

        private fun showOnMain(
            pkg: String,
            decision: Decision.Block,
        ) {
            val svc =
                surface() ?: run {
                    launching = false
                    BlockingLog.log("Launcher", "no block surface available, cannot show $pkg")
                    return
                }
            cancelCheck()
            val token = ++lastToken
            BlockScreenState.activeToken = token
            val request = BlockRequest(pkg, appLabel(pkg), decision, token)
            lastRequest = request
            lastLaunchAt = SystemClock.elapsedRealtime()
            val homed = svc.goHome()
            val started = startBlockActivity(request)
            BlockingLog.log("Launcher", "home=$homed activityStarted=$started token=$token")
            // Home did not happen: the blocked app is still in front, so cover it right now.
            if (!started || !homed) showOverlay(svc, request)
            if (!started) {
                launching = false
                return
            }
            val check =
                Runnable {
                    pendingCheck = null
                    launching = false
                    val visible = BlockScreenState.visible
                    BlockingLog.log("Launcher", "launch check: activityVisible=$visible token=$token")
                    if (!visible && BlockScreenState.activeToken == token) showOverlay(svc, request)
                }
            pendingCheck = check
            handler.postDelayed(check, LAUNCH_CHECK_MS)
        }

        private fun cancelCheck() {
            pendingCheck?.let(handler::removeCallbacks)
            pendingCheck = null
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
            svc: BlockSurface,
            request: BlockRequest,
        ) {
            BlockingLog.log("Launcher", "overlay shown for ${request.pkg}")
            overlay?.remove()
            // Stored before add(): a failed attachment reports through onRemoved() while add() is still running,
            // and that must clear this very reference (otherwise isShowing() would see an overlay that isn't there).
            lateinit var created: BlockOverlayWindow
            created =
                BlockOverlayWindow(
                    svc,
                    request,
                    decisionSource,
                    clock,
                    extras.orElse(null),
                    onRemoved = { if (overlay === created) overlay = null },
                    onFinished = { BlockScreenState.activeToken = 0L },
                )
            overlay = created
            created.add()
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
            const val PUSH_BACK_WINDOW_MS = 2_000L
            const val HALF_SHOWN_MS = 4_000L
            const val ONE_MIN_SHOWN_MS = 6_000L
            const val FINAL_SHOWN_MS = 3_000L
        }
    }

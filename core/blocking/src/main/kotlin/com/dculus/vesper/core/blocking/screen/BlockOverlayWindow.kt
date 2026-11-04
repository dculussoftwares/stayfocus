package com.dculus.vesper.core.blocking.screen

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.dculus.vesper.core.ui.theme.VesperTheme
import java.time.Clock

/**
 * Fallback when Android drops the background start of [BlockActivity] (and the only path without a window of its
 * own): the same screen as a full-screen window of the [surface]'s type, `TYPE_ACCESSIBILITY_OVERLAY` or
 * `TYPE_APPLICATION_OVERLAY`. Back and the button go home and remove the window.
 */
internal class BlockOverlayWindow(
    private val surface: BlockSurface,
    private val request: BlockRequest,
    private val decisionSource: BlockDecisionSource,
    private val clock: Clock,
    private val extras: BlockScreenExtras?,
    private val onRemoved: () -> Unit,
    /** The user (or an Allow decision) ended this block, as opposed to the activity taking over the screen. */
    private val onFinished: () -> Unit,
) : LifecycleOwner,
    SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val windowManager = surface.windowContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: BackAwareFrame? = null
    private var compose: ComposeView? = null

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun add() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        val root = BackAwareFrame(surface.windowContext, ::goHome)
        val composeView = ComposeView(surface.windowContext)
        root.addView(composeView)
        root.setViewTreeLifecycleOwner(this)
        root.setViewTreeSavedStateRegistryOwner(this)
        composeView.setContent {
            VesperTheme {
                BlockHost(request, decisionSource, clock, extras, onGoHome = ::goHome, onAllow = ::finish)
            }
        }
        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                surface.windowType,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.OPAQUE,
            )
        val error = windowManager.tryAddOverlay(root, params)
        if (error != null) return failed(composeView, error)
        view = root
        compose = composeView
    }

    private fun failed(
        composeView: ComposeView,
        e: Exception,
    ) {
        Log.w(TAG, "Overlay could not be attached", e)
        composeView.disposeComposition()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        onRemoved()
    }

    fun remove() {
        val v = view ?: return
        view = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        compose?.disposeComposition()
        compose = null
        runCatching { windowManager.removeView(v) }
        onRemoved()
    }

    private fun finish() {
        remove()
        onFinished()
    }

    private fun goHome() {
        // Keep the overlay if Home did not happen: removing it would expose the blocked app.
        if (surface.goHome()) finish()
    }
}

/** Window root that turns the back key into [onBack] (a window without an activity has no back dispatcher). */
private class BackAwareFrame(
    context: Context,
    private val onBack: () -> Unit,
) : FrameLayout(context) {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_UP) onBack()
        return true
    }
}

private const val TAG = "BlockOverlayWindow"

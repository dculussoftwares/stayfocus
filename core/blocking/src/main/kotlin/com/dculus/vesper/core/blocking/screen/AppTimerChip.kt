package com.dculus.vesper.core.blocking.screen

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import com.dculus.vesper.core.blocking.BlockingLog
import com.dculus.vesper.core.blocking.R
import com.dculus.vesper.core.ui.theme.V5Colors
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot

/**
 * The app timer: a small tab docked on the left or right edge of the screen while a limited app is in front.
 *
 * It slides out of the edge showing the time left ("12:34 left · Instagram") while its ring sweeps from full down to
 * the share of the budget that is left. After a few seconds it tucks itself back into the edge, leaving only the
 * ring peeking out, which keeps draining as the time runs. A tap slides it out again (and it tucks itself in again
 * after a while); a tap on the open tab tucks it in at once.
 *
 * Drag it to move it: it follows the finger as a small ring pill, anywhere on the screen, and on release snaps to the
 * nearer side (or the side it was flung towards) at that height. The chosen side and height are kept for the next
 * time it shows.
 *
 * Every move is a slide of the tab plus a fade of its text, retargeted from wherever it is, so taps in quick
 * succession never jump. With animations off in system settings it simply snaps between states. One overlay window
 * of the [surface]'s type, never focusable, and only as wide as the visible part of the tab, so the app underneath
 * keeps every touch outside it.
 */
@Suppress("TooManyFunctions") // small, single-purpose animation and gesture steps
internal class AppTimerChip(
    private val surface: BlockSurface,
    private val handler: Handler,
) {
    private val context = surface.windowContext
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var root: Parts? = null
    private val running = mutableListOf<Animator>()
    private var slide: Animator? = null
    private var textFade: Animator? = null
    private var snap: Animator? = null
    private val collapseRunnable = Runnable { collapse() }

    /** Where the user last left the tab; kept across shows. Null height: the default spot. */
    private var dockRight = false
    private var dockY: Int? = null

    private class Parts(
        val container: FrameLayout,
        val params: WindowManager.LayoutParams,
        val card: LinearLayout,
        val texts: View,
        val time: TextView,
        val appLabel: String,
        val ring: TimerRing,
        val endsAt: Long,
        val totalMs: Long,
    ) {
        /** Tab width once measured; 0 until then. */
        var width = 0
        var expanded = false
        var dragging = false

        /** True while the opening sweep runs; the live tick leaves the ring alone until it ends. */
        var sweeping = true
    }

    private val tick =
        object : Runnable {
            override fun run() {
                val parts = root ?: return
                val left = parts.endsAt - SystemClock.elapsedRealtime()
                val text = formatTimeLeft(left)
                parts.time.text = context.getString(R.string.app_timer_left, text)
                parts.card.contentDescription = context.getString(R.string.app_timer_a11y, text, parts.appLabel)
                if (!parts.sweeping) parts.ring.fraction = timeLeftFraction(left, parts.totalMs)
                // Re-align to the next whole second so the digits change in step with a real clock.
                handler.postDelayed(this, left.mod(SECOND_MS).let { if (it == 0L) SECOND_MS else it })
            }
        }

    @SuppressLint("ClickableViewAccessibility") // DragListener sends taps through performClick()
    fun show(
        appLabel: String,
        remainingMs: Long,
        totalMs: Long,
        expanded: Boolean,
    ) {
        remove()
        val ring = TimerRing(context)
        val time = label(bold = true)
        val app = label(bold = false).apply { text = context.getString(R.string.app_timer_app, appLabel) }
        val texts =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                // The words read left to right on either side; only the tab itself mirrors.
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                addView(time, LinearLayout.LayoutParams(WRAP, WRAP))
                addView(app, LinearLayout.LayoutParams(WRAP, WRAP))
            }
        val card = cardView(texts, ring)
        val container =
            FrameLayout(context).apply {
                clipChildren = false
                // Sides are explicit (left / right), also in right-to-left languages: the slide maths assume it.
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                addView(card, FrameLayout.LayoutParams(WRAP, WRAP))
            }
        val params = windowParams()
        val error = windowManager.tryAddOverlay(container, params)
        if (error != null) {
            BlockingLog.log("Timer", "could not add app timer: ${error.message}")
            return
        }
        val parts =
            Parts(
                container,
                params,
                card,
                texts,
                time,
                appLabel,
                ring,
                SystemClock.elapsedRealtime() + remainingMs,
                totalMs,
            )
        root = parts
        card.alpha = 0f
        card.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            if (parts.expanded) collapse() else expand()
        }
        card.setOnTouchListener(DragListener(parts))
        tick.run()
        // Measure the open tab once, pin its width (the window shrinks around the tucked tab later), then enter.
        card.post {
            if (root !== parts) return@post
            parts.width = card.width
            applySide(parts)
            card.translationX = hiddenX(parts)
            setWindowWidth(parts, tuckedWindowWidth())
            card.alpha = 1f
            enter(parts, timeLeftFraction(remainingMs, totalMs), expanded)
        }
    }

    /** Slides the tab out of the edge with the time showing, then tucks it in again after [HOLD_MS]. */
    fun expand() {
        val parts = root ?: return
        if (parts.width == 0 || parts.dragging) return
        handler.removeCallbacks(collapseRunnable)
        parts.expanded = true
        // Widen the window first so the tab has room to slide into; the tab stays put on screen while it does.
        setWindowWidth(parts, parts.width + shadow())
        slideTo(parts, 0f, SLIDE_MS)
        fadeTexts(parts, 1f, TEXT_IN_MS, TEXT_IN_DELAY_MS)
        handler.postDelayed(collapseRunnable, HOLD_MS)
    }

    /** Tucks the tab into the edge, leaving only the ring showing. */
    fun collapse() {
        val parts = root ?: return
        handler.removeCallbacks(collapseRunnable)
        if (!parts.expanded || parts.width == 0) return
        parts.expanded = false
        fadeTexts(parts, 0f, TEXT_OUT_MS, 0L)
        slideTo(parts, tuckedX(parts), SLIDE_MS) {
            // Only now shrink the window: during the slide the tab still needs the room.
            if (root === parts && !parts.expanded) setWindowWidth(parts, tuckedWindowWidth())
        }
    }

    /** Slides the tab fully into the edge and removes it (the app left the front). */
    fun hide() {
        val parts = root ?: return
        handler.removeCallbacks(collapseRunnable)
        if (parts.width == 0 || parts.dragging) {
            remove()
            return
        }
        parts.expanded = false
        fadeTexts(parts, 0f, TEXT_OUT_MS, 0L)
        slideTo(parts, hiddenX(parts), HIDE_MS) { if (root === parts) remove() }
    }

    /** Removes the tab at once (the block screen is coming up, or the engine stopped). */
    fun remove() {
        handler.removeCallbacks(collapseRunnable)
        handler.removeCallbacks(tick)
        // Clear root first: cancelling an animation ends it, and its end listener must not act on this tab again.
        val parts = root
        root = null
        running.toList().forEach { it.cancel() }
        running.clear()
        slide = null
        textFade = null
        snap = null
        if (parts != null) runCatching { windowManager.removeView(parts.container) }
    }

    private fun enter(
        parts: Parts,
        fraction: Float,
        expanded: Boolean,
    ) {
        parts.texts.alpha = 0f
        start(
            ValueAnimator.ofFloat(1f, fraction).apply {
                startDelay = SWEEP_DELAY_MS
                duration = SWEEP_MS
                interpolator = EASE_IN_OUT
                addUpdateListener { parts.ring.fraction = it.animatedValue as Float }
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            parts.sweeping = false
                        }
                    },
                )
            },
        )
        if (expanded) expand() else slideTo(parts, tuckedX(parts), SLIDE_MS)
    }

    // --- Dragging -----------------------------------------------------------------------------------------------

    /** Tells a tap (open / tuck in) from a drag (move the tab), and moves the window with the finger. */
    @SuppressLint("ClickableViewAccessibility") // a tap still goes through performClick()
    private inner class DragListener(
        private val parts: Parts,
    ) : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var velocity: VelocityTracker? = null

        override fun onTouch(
            v: View,
            event: MotionEvent,
        ): Boolean {
            velocity?.addMovement(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    velocity?.recycle()
                    velocity = VelocityTracker.obtain().also { it.addMovement(event) }
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!parts.dragging && hypot(event.rawX - downX, event.rawY - downY) > touchSlop) {
                        startDrag(parts)
                    }
                    if (parts.dragging) followFinger(parts, event.rawX, event.rawY)
                }

                MotionEvent.ACTION_UP -> {
                    if (parts.dragging) {
                        val vx =
                            velocity?.let {
                                it.computeCurrentVelocity(VELOCITY_UNITS_MS)
                                it.xVelocity
                            } ?: 0f
                        endDrag(parts, vx)
                    } else {
                        v.performClick()
                    }
                    recycle()
                }

                MotionEvent.ACTION_CANCEL -> {
                    if (parts.dragging) endDrag(parts, 0f)
                    recycle()
                }
            }
            return true
        }

        private fun recycle() {
            velocity?.recycle()
            velocity = null
        }
    }

    /** Picks the tab up: it shrinks to the ring pill, rounds all its corners and lifts. */
    private fun startDrag(parts: Parts) {
        if (parts.width == 0) return
        parts.dragging = true
        handler.removeCallbacks(collapseRunnable)
        slide?.cancel()
        textFade?.cancel()
        snap?.cancel()
        parts.expanded = false
        parts.texts.alpha = 0f
        parts.card.translationX = tuckedX(parts)
        parts.card.background = cardBackground(floating = true)
        setWindowWidth(parts, tuckedWindowWidth())
        start(
            ObjectAnimator.ofFloat(parts.card, View.TRANSLATION_Z, dp(LIFT_DP).toFloat()).apply { duration = LIFT_MS },
        )
        parts.card.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    /** Centres the pill under the finger, kept inside the screen. */
    private fun followFinger(
        parts: Parts,
        rawX: Float,
        rawY: Float,
    ) {
        val params = parts.params
        params.x = (rawX - params.width / 2f).toInt().coerceIn(0, screenWidth() - params.width)
        params.y = clampY(parts, (rawY - parts.container.height / 2f).toInt())
        runCatching { windowManager.updateViewLayout(parts.container, params) }
    }

    /** Lets go: snaps to the side it was flung towards, or else the nearer one, and stays tucked in there. */
    private fun endDrag(
        parts: Parts,
        velocityX: Float,
    ) {
        parts.dragging = false
        val params = parts.params
        val flingMin = ViewConfiguration.get(context).scaledMinimumFlingVelocity * FLING_FACTOR
        dockRight =
            when {
                velocityX > flingMin -> true
                velocityX < -flingMin -> false
                else -> params.x + params.width / 2 > screenWidth() / 2
            }
        dockY = params.y
        applySide(parts)
        parts.card.translationX = tuckedX(parts)
        start(ObjectAnimator.ofFloat(parts.card, View.TRANSLATION_Z, 0f).apply { duration = LIFT_MS })
        val from = params.x
        val to = windowX(params.width)
        snap?.cancel()
        val animator =
            ValueAnimator.ofInt(from, to).apply {
                duration = SNAP_MS
                interpolator = EASE_DRAWER
                addUpdateListener {
                    if (root !== parts) return@addUpdateListener
                    params.x = it.animatedValue as Int
                    runCatching { windowManager.updateViewLayout(parts.container, params) }
                }
            }
        snap = animator
        start(animator)
    }

    // --- Layout per side ----------------------------------------------------------------------------------------

    /**
     * Mirrors the tab for the side it is docked on: the ring end (what peeks out when tucked) faces the middle of
     * the screen, the square end sits against the edge, and the shadow gets room on the inner side.
     */
    @SuppressLint("RtlHardcoded") // the tab docks to a physical edge, whatever the reading direction
    private fun applySide(parts: Parts) {
        val s = shadow()
        arrange(parts.card, parts.texts, parts.ring)
        parts.card.background = cardBackground(floating = false)
        parts.card.layoutParams =
            FrameLayout.LayoutParams(parts.width, WRAP).apply {
                gravity = (if (dockRight) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL
            }
        if (dockRight) parts.container.setPadding(s, s, 0, s) else parts.container.setPadding(0, s, s, s)
    }

    /** The tab's translation when tucked in: only the ring end peeks out of the edge. */
    private fun tuckedX(parts: Parts): Float {
        val hidden = (parts.width - tuckedWidth()).toFloat()
        return if (dockRight) hidden else -hidden
    }

    private fun hiddenX(parts: Parts): Float = if (dockRight) parts.width.toFloat() else -parts.width.toFloat()

    private fun setWindowWidth(
        parts: Parts,
        width: Int,
    ) {
        val params = parts.params
        val x = windowX(width)
        if (params.width == width && params.x == x) return
        params.width = width
        params.x = x
        dockY?.let { params.y = clampY(parts, it) }
        runCatching { windowManager.updateViewLayout(parts.container, params) }
    }

    private fun windowX(width: Int): Int = if (dockRight) screenWidth() - width else 0

    private fun clampY(
        parts: Parts,
        y: Int,
    ): Int {
        val top = statusBarHeight()
        val bottom = screenHeight() - parts.container.height - dp(BOTTOM_KEEP_CLEAR_DP)
        return if (bottom > top) y.coerceIn(top, bottom) else top
    }

    /** What stays visible when tucked in: the ring with a little tab around it. */
    private fun tuckedWidth(): Int = dp(PEEK_DP) + dp(RING_DP) + dp(RING_SIDE_PADDING_DP)

    private fun tuckedWindowWidth(): Int = tuckedWidth() + shadow()

    private fun shadow(): Int = dp(SHADOW_ROOM_DP)

    // --- Animation helpers --------------------------------------------------------------------------------------

    /** Moves the tab to [x] from wherever it is now, replacing any slide in flight. */
    private fun slideTo(
        parts: Parts,
        x: Float,
        durationMs: Long,
        onDone: (() -> Unit)? = null,
    ) {
        slide?.cancel()
        val animator =
            ObjectAnimator.ofFloat(parts.card, View.TRANSLATION_X, x).apply {
                duration = durationMs
                interpolator = EASE_DRAWER
            }
        if (onDone != null) {
            animator.addListener(
                object : AnimatorListenerAdapter() {
                    private var cancelled = false

                    override fun onAnimationCancel(animation: Animator) {
                        cancelled = true
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        if (!cancelled) onDone()
                    }
                },
            )
        }
        slide = animator
        start(animator)
    }

    private fun fadeTexts(
        parts: Parts,
        to: Float,
        durationMs: Long,
        delayMs: Long,
    ) {
        textFade?.cancel()
        val animator =
            ObjectAnimator.ofFloat(parts.texts, View.ALPHA, to).apply {
                duration = durationMs
                startDelay = delayMs
                interpolator = EASE_OUT
            }
        textFade = animator
        start(animator)
    }

    private fun start(animator: Animator) {
        running += animator
        animator.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    running -= animation
                }
            },
        )
        animator.start()
    }

    // --- Views --------------------------------------------------------------------------------------------------

    private fun cardView(
        texts: View,
        ring: TimerRing,
    ): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cardBackground(floating = false)
            elevation = dp(ELEVATION_DP).toFloat()
            isClickable = true
            arrange(this, texts, ring)
        }

    /**
     * Lays the tab out for its side: on the left `[time · app  ◯]`, on the right `[◯  time · app]`, with the wider
     * padding against the screen edge. Done by hand (child order and left/right padding) rather than with a
     * right-to-left layout direction, which Android ignores in an app that doesn't declare RTL support. The tab is
     * equally wide either way.
     */
    private fun arrange(
        card: LinearLayout,
        texts: View,
        ring: View,
    ) {
        val edge = dp(EDGE_SIDE_PADDING_DP)
        val ringSide = dp(RING_SIDE_PADDING_DP)
        val v = dp(V_PADDING_DP)
        card.removeAllViews()
        val textParams = LinearLayout.LayoutParams(WRAP, WRAP)
        val ringParams = LinearLayout.LayoutParams(dp(RING_DP), dp(RING_DP))
        if (dockRight) {
            card.setPadding(ringSide, v, edge, v)
            textParams.leftMargin = dp(GAP_DP)
            card.addView(ring, ringParams)
            card.addView(texts, textParams)
        } else {
            card.setPadding(edge, v, ringSide, v)
            textParams.rightMargin = dp(GAP_DP)
            card.addView(texts, textParams)
            card.addView(ring, ringParams)
        }
    }

    /** Square where the tab meets the screen edge and round where it sticks out; round all over while dragged. */
    @Suppress("MagicNumber") // corner radii: top-left, top-right, bottom-right, bottom-left (x, y each)
    private fun cardBackground(floating: Boolean): GradientDrawable {
        val r = dp(CARD_RADIUS_DP).toFloat()
        val left = if (floating || dockRight) r else 0f
        val right = if (floating || !dockRight) r else 0f
        return GradientDrawable().apply {
            cornerRadii = floatArrayOf(left, left, right, right, right, right, left, left)
            setColor(INK)
            setStroke(dp(1), BORDER_COLOR)
        }
    }

    private fun label(bold: Boolean): TextView =
        TextView(context).apply {
            setTextColor(if (bold) Color.WHITE else DIM_WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP)
            typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
            // Tabular digits: the countdown must not jiggle sideways as it ticks.
            fontFeatureSettings = "tnum"
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            if (!bold) maxWidth = dp(APP_MAX_WIDTH_DP)
        }

    @SuppressLint("RtlHardcoded") // x is measured from the physical left edge
    private fun windowParams() =
        WindowManager
            .LayoutParams(
                WRAP,
                WRAP,
                surface.windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                x = 0
                y = dockY ?: (statusBarHeight() + (screenHeight() * TOP_SHARE).toInt())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

    private fun screenWidth(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds
                .width()
        } else {
            context.resources.displayMetrics.widthPixels
        }

    private fun screenHeight(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds
                .height()
        } else {
            context.resources.displayMetrics.heightPixels
        }

    private fun statusBarHeight(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.windowInsets
                .getInsets(WindowInsets.Type.statusBars())
                .top
        } else {
            dp(LEGACY_STATUS_BAR_DP)
        }

    private fun dp(value: Int): Int =
        TypedValue
            .applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics)
            .toInt()

    /** A lime arc over a faint track, drawn clockwise from 12 o'clock; [fraction] 1 is a full ring. */
    private class TimerRing(
        context: Context,
    ) : View(context) {
        var fraction = 1f
            set(value) {
                if (field == value) return
                field = value
                invalidate()
            }

        private val stroke = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3f, resources.displayMetrics)
        private val track =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                color = Color.argb(TRACK_ALPHA, 0xFF, 0xFF, 0xFF)
            }
        private val arc =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
                color = LIME
            }
        private val bounds = RectF()

        override fun onSizeChanged(
            w: Int,
            h: Int,
            oldw: Int,
            oldh: Int,
        ) {
            val inset = stroke / 2
            bounds.set(inset, inset, w - inset, h - inset)
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawOval(bounds, track)
            canvas.drawArc(bounds, START_ANGLE, FULL_TURN * fraction, false, arc)
        }

        private companion object {
            const val TRACK_ALPHA = 0x40
            const val START_ANGLE = -90f
            const val FULL_TURN = 360f
        }
    }

    private companion object {
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

        const val TEXT_SP = 14f
        const val EDGE_SIDE_PADDING_DP = 14
        const val RING_SIDE_PADDING_DP = 10
        const val V_PADDING_DP = 10
        const val CARD_RADIUS_DP = 20
        const val ELEVATION_DP = 6
        const val LIFT_DP = 6
        const val RING_DP = 20
        const val GAP_DP = 10
        const val PEEK_DP = 10
        const val APP_MAX_WIDTH_DP = 150
        const val SHADOW_ROOM_DP = 10
        const val LEGACY_STATUS_BAR_DP = 24

        /** Kept clear above the bottom of the screen (navigation bar, gesture area). */
        const val BOTTOM_KEEP_CLEAR_DP = 56

        /** Default height of the tab, below the status bar: clear of most apps' top bars. */
        const val TOP_SHARE = 0.22f

        const val VELOCITY_UNITS_MS = 1_000
        const val FLING_FACTOR = 4

        val tokens = V5Colors()
        val LIME = tokens.lime.toArgb()

        // Same ink as the usage warning chip, so the two read as one family.
        val INK = Color.rgb(0x00, 0x0B, 0x1A)
        val DIM_WHITE = Color.argb(0xB3, 0xFF, 0xFF, 0xFF)
        val BORDER_COLOR = Color.argb(0x33, 0xFF, 0xFF, 0xFF)

        // iOS-like drawer curve for the slides in and out of the edge and the snap to a side; strong ease-out for
        // fades; strong ease-in-out for the ring morphing in place.
        val EASE_DRAWER = PathInterpolator(0.32f, 0.72f, 0f, 1f)
        val EASE_OUT = PathInterpolator(0.23f, 1f, 0.32f, 1f)
        val EASE_IN_OUT = PathInterpolator(0.77f, 0f, 0.175f, 1f)

        const val SLIDE_MS = 300L
        const val SNAP_MS = 320L
        const val HIDE_MS = 200L
        const val LIFT_MS = 120L
        const val TEXT_IN_MS = 180L
        const val TEXT_IN_DELAY_MS = 60L
        const val TEXT_OUT_MS = 120L
        const val SWEEP_DELAY_MS = 150L
        const val SWEEP_MS = 700L

        /** How long the tab stays open before it tucks itself in, on start and after a tap. */
        const val HOLD_MS = 3_500L
    }
}

private const val SECOND_MS = 1_000L
private const val SECONDS_PER_MIN = 60L
private const val SECONDS_PER_HOUR = 3_600L

/** "12:34", or "1:02:03" from an hour up; rounded up so it never reads 0:00 while time is left. */
internal fun formatTimeLeft(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) + SECOND_MS - 1) / SECOND_MS
    val h = total / SECONDS_PER_HOUR
    val m = total % SECONDS_PER_HOUR / SECONDS_PER_MIN
    val s = total % SECONDS_PER_MIN
    return if (h > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", m, s)
    }
}

/** Share of [totalMs] still left, 0..1. */
internal fun timeLeftFraction(
    leftMs: Long,
    totalMs: Long,
): Float = if (totalMs > 0) (leftMs.toFloat() / totalMs).coerceIn(0f, 1f) else 0f

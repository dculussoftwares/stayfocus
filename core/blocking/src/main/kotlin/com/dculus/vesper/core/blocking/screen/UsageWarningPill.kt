package com.dculus.vesper.core.blocking.screen

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.input.InputManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.dculus.vesper.core.blocking.BlockingLog

/**
 * The usage warning: the screen's edges and corners glow in the app's colours and a card pops up in the middle of
 * the screen with the message and a ring that drains while it is up. One full-screen overlay window of the
 * [surface]'s type that takes no touches and no focus, so the app underneath keeps working. Removes itself after the
 * duration given to [show]. The urgent (one minute) warning is orange, the glow breathes and the card shakes; the
 * calm (half) one is violet and pulses once. With animations off in system settings it simply appears and
 * disappears.
 */
@Suppress("TooManyFunctions") // small, single-purpose animation steps
internal class UsageWarningPill(
    private val surface: BlockSurface,
    private val handler: Handler,
) {
    private val context = surface.windowContext
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: Parts? = null
    private val running = mutableListOf<Animator>()
    private val dismissRunnable = Runnable { animateOut() }

    private class Parts(
        val container: FrameLayout,
        val glow: View,
        val halo: View,
        val card: View,
    )

    fun show(
        text: String,
        urgent: Boolean,
        durationMs: Long,
    ) {
        remove()
        val palette = if (urgent) WarningPalette.URGENT else WarningPalette.CALM
        val container = FrameLayout(context).apply { clipChildren = false }
        val glow = EdgeGlowView(context, palette)
        val halo = View(context).apply { background = rounded(palette.halo) }
        val ring = CountdownRing(context)
        val card = cardView(text, ring)
        val centred = { w: Int, h: Int -> FrameLayout.LayoutParams(w, h).apply { gravity = Gravity.CENTER } }
        container.addView(glow, FrameLayout.LayoutParams(MATCH, MATCH))
        container.addView(halo, centred(0, 0))
        container.addView(card, centred(WRAP, WRAP))
        val error = windowManager.tryAddOverlay(container, windowParams())
        if (error != null) {
            BlockingLog.log("Pill", "could not add usage warning: ${error.message}")
            return
        }
        val parts = Parts(container, glow, halo, card)
        root = parts
        glow.alpha = 0f
        halo.alpha = 0f
        card.alpha = 0f
        // Size the halo to the card and start the entrance once the card has been measured.
        card.post {
            if (root !== parts) return@post
            halo.layoutParams = centred(card.width, card.height)
            animateIn(parts, ring, urgent, durationMs)
        }
        card.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        playSound()
        handler.postDelayed(dismissRunnable, durationMs)
    }

    /** Removes the warning at once (the block screen is coming up, or a newer warning replaces it). */
    fun remove() {
        handler.removeCallbacks(dismissRunnable)
        // Clear root first: cancelling the entrance ends it, and its end listener must not start new animations.
        val parts = root
        root = null
        running.toList().forEach { it.cancel() }
        running.clear()
        if (parts != null) runCatching { windowManager.removeView(parts.container) }
    }

    @Suppress("DEPRECATION") // FLAG_LAYOUT_NO_LIMITS: the only way to reach under the system bars on API 26–29
    private fun windowParams() =
        WindowManager
            .LayoutParams(
                MATCH,
                MATCH,
                surface.windowType,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
                // From Android 12 touches only pass through an app overlay at most this opaque; an accessibility
                // overlay is trusted and needs no cap.
                if (surface.windowType == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ) {
                    val max = context.getSystemService(InputManager::class.java).maximumObscuringOpacityForTouch
                    alpha = minOf(max, OVERLAY_ALPHA_CAP)
                }
            }

    /** A compact pill: a small countdown ring and one line of white text on v5 ink. */
    private fun cardView(
        text: String,
        ring: CountdownRing,
    ): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ICON_PADDING_DP), dp(V_PADDING_DP), dp(H_PADDING_DP), dp(V_PADDING_DP))
            background = cardBackground()
            elevation = dp(ELEVATION_DP).toFloat()
            addView(ring, LinearLayout.LayoutParams(dp(RING_DP), dp(RING_DP)))
            addView(
                TextView(context).apply {
                    this.text = text
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP)
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                },
                LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(GAP_DP) },
            )
        }

    /** Solid v5 ink with a faint glass border, so the chip reads clearly over the coloured glow. */
    private fun cardBackground(): GradientDrawable = rounded(INK).apply { setStroke(dp(1), BORDER_COLOR) }

    private fun animateIn(
        parts: Parts,
        ring: CountdownRing,
        urgent: Boolean,
        durationMs: Long,
    ) {
        val card = parts.card
        val rise = dp(RISE_DP).toFloat()
        val enter =
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(parts.glow, View.ALPHA, 0f, 1f).apply { duration = GLOW_IN_MS },
                    ObjectAnimator
                        .ofPropertyValuesHolder(
                            card,
                            PropertyValuesHolder.ofFloat(View.SCALE_X, ENTER_SCALE, 1f),
                            PropertyValuesHolder.ofFloat(View.SCALE_Y, ENTER_SCALE, 1f),
                            PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, rise, 0f),
                        ).apply {
                            duration = ENTER_MS
                            interpolator = OvershootInterpolator(OVERSHOOT)
                        },
                    ObjectAnimator.ofFloat(card, View.ALPHA, 0f, 1f).apply { duration = FADE_MS },
                )
            }
        enter.addListener(
            object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    // A cancel (exit or removal) also ends the entrance; only a finished entrance draws attention.
                    if (cancelled || root !== parts) return
                    if (urgent) attentionUrgent(parts) else attentionCalm(card)
                }
            },
        )
        start(enter)
        start(
            ValueAnimator.ofFloat(1f, 0f).apply {
                duration = durationMs
                interpolator = LinearInterpolator()
                addUpdateListener { ring.fraction = it.animatedValue as Float }
            },
        )
    }

    /** One soft pulse: enough to catch the eye without nagging. */
    private fun attentionCalm(card: View) {
        start(pulse(card, CALM_PULSE).apply { startDelay = PULSE_DELAY_MS })
    }

    /** A shake with a second haptic tick, then the glow and the halo keep breathing until the warning leaves. */
    private fun attentionUrgent(parts: Parts) {
        val card = parts.card
        val shift = dp(SHAKE_DP).toFloat()
        val shake =
            ObjectAnimator
                .ofFloat(card, View.TRANSLATION_X, 0f, -shift, shift, -shift * 0.7f, shift * 0.7f, -shift * 0.3f, 0f)
                .apply { duration = SHAKE_MS }
        shake.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    card.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            },
        )
        start(shake)
        start(pulse(card, URGENT_PULSE).apply { startDelay = SHAKE_MS })
        if (!ValueAnimator.areAnimatorsEnabled()) return
        start(
            ObjectAnimator.ofFloat(parts.glow, View.ALPHA, 1f, BREATHE_LOW).apply {
                duration = BREATHE_MS
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
            },
        )
        start(
            ObjectAnimator
                .ofPropertyValuesHolder(
                    parts.halo,
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, HALO_SCALE),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, HALO_SCALE),
                    PropertyValuesHolder.ofFloat(View.ALPHA, HALO_ALPHA, 0f),
                ).apply {
                    duration = HALO_MS
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = DecelerateInterpolator()
                },
        )
    }

    private fun pulse(
        view: View,
        to: Float,
    ): Animator =
        ObjectAnimator
            .ofPropertyValuesHolder(
                view,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, to, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, to, 1f),
            ).apply { duration = PULSE_MS }

    private fun animateOut() {
        val parts = root ?: return
        running.toList().forEach { it.cancel() }
        running.clear()
        parts.halo.alpha = 0f
        val exit =
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(parts.glow, View.ALPHA, 0f),
                    ObjectAnimator.ofFloat(parts.card, View.ALPHA, 0f),
                    ObjectAnimator.ofFloat(parts.card, View.SCALE_X, EXIT_SCALE),
                    ObjectAnimator.ofFloat(parts.card, View.SCALE_Y, EXIT_SCALE),
                )
                duration = EXIT_MS
                interpolator = AccelerateInterpolator()
            }
        exit.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (root === parts) remove()
                }
            },
        )
        start(exit)
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

    /** The default notification sound, only when the phone's ringer is on (not silent or vibrate). */
    private fun playSound() {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
        runCatching {
            RingtoneManager.getRingtone(context, uri)?.apply {
                audioAttributes =
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                play()
            }
        }
    }

    private fun rounded(color: Int) =
        GradientDrawable().apply {
            cornerRadius = dp(CARD_RADIUS_DP).toFloat()
            setColor(color)
        }

    private fun dp(value: Int): Int =
        TypedValue
            .applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics)
            .toInt()

    /** A white ring that drains clockwise from full ([fraction] 1) to empty (0), over a faint track. */
    private class CountdownRing(
        context: Context,
    ) : View(context) {
        var fraction = 1f
            set(value) {
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
                color = Color.WHITE
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
            const val TRACK_ALPHA = 0x55
            const val START_ANGLE = -90f
            const val FULL_TURN = 360f
        }
    }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

        const val TEXT_SP = 15f
        const val H_PADDING_DP = 18
        const val ICON_PADDING_DP = 12
        const val V_PADDING_DP = 11
        const val CARD_RADIUS_DP = 24
        const val ELEVATION_DP = 8
        const val RING_DP = 20
        const val GAP_DP = 10
        const val SHAKE_DP = 7
        const val RISE_DP = 16

        // v5 `ink`.
        val INK = Color.rgb(0x00, 0x0B, 0x1A)
        val BORDER_COLOR = Color.argb(0x33, 0xFF, 0xFF, 0xFF)

        const val GLOW_IN_MS = 450L
        const val ENTER_MS = 520L
        const val FADE_MS = 180L
        const val EXIT_MS = 300L
        const val SHAKE_MS = 480L
        const val PULSE_MS = 420L
        const val PULSE_DELAY_MS = 150L
        const val HALO_MS = 1_100L
        const val BREATHE_MS = 900L

        const val ENTER_SCALE = 0.8f
        const val EXIT_SCALE = 0.9f
        const val OVERSHOOT = 1.4f
        const val CALM_PULSE = 1.04f
        const val URGENT_PULSE = 1.06f
        const val HALO_SCALE = 1.25f
        const val HALO_ALPHA = 0.5f
        const val BREATHE_LOW = 0.5f
        const val OVERLAY_ALPHA_CAP = 0.8f
    }
}

package com.dculus.stayfocused.core.blocking.screen

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Handler
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import android.widget.TextView

/**
 * A slim pill at the top of the screen, as a `TYPE_ACCESSIBILITY_OVERLAY` window that takes no touches and no
 * focus, so the app underneath keeps working. Removes itself after the duration given to [show].
 */
internal class UsageWarningPill(
    private val service: AccessibilityService,
    private val handler: Handler,
) {
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: TextView? = null
    private val removeRunnable = Runnable { remove() }

    fun show(
        text: String,
        urgent: Boolean,
        durationMs: Long,
    ) {
        remove()
        val pill =
            TextView(service).apply {
                this.text = text
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP)
                gravity = Gravity.CENTER
                setPadding(dp(H_PADDING_DP), dp(V_PADDING_DP), dp(H_PADDING_DP), dp(V_PADDING_DP))
                background =
                    GradientDrawable().apply {
                        cornerRadius = dp(RADIUS_DP).toFloat()
                        setColor(if (urgent) URGENT_COLOR else CALM_COLOR)
                    }
            }
        val params =
            WindowManager
                .LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    y = statusBarHeight() + dp(TOP_GAP_DP)
                }
        try {
            windowManager.addView(pill, params)
        } catch (_: WindowManager.BadTokenException) {
            return // the service disconnected under us
        }
        view = pill
        if (urgent) pill.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        playSound()
        handler.postDelayed(removeRunnable, durationMs)
    }

    /** The default notification sound, only when the phone's ringer is on (not silent or vibrate). */
    private fun playSound() {
        val audio = service.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
        runCatching {
            RingtoneManager.getRingtone(service, uri)?.apply {
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

    fun remove() {
        handler.removeCallbacks(removeRunnable)
        val v = view ?: return
        view = null
        runCatching { windowManager.removeView(v) }
    }

    private fun dp(value: Int): Int =
        TypedValue
            .applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), service.resources.displayMetrics)
            .toInt()

    private fun statusBarHeight(): Int {
        val id = service.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) service.resources.getDimensionPixelSize(id) else 0
    }

    private companion object {
        const val TEXT_SP = 14f
        const val H_PADDING_DP = 16
        const val V_PADDING_DP = 8
        const val RADIUS_DP = 20
        const val TOP_GAP_DP = 8
        val CALM_COLOR = Color.argb(0xE6, 0x20, 0x20, 0x20)
        val URGENT_COLOR = Color.argb(0xEB, 0xB4, 0x5F, 0x06)
    }
}

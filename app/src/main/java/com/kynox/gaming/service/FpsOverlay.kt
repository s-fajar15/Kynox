package com.kynox.gaming.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.kynox.gaming.core.utils.Logger
import com.kynox.gaming.core.utils.gpuFreqMhz
import com.kynox.gaming.domain.model.LiveMetrics

private const val TAG = "FpsOverlay"
private const val PREFS = "kynox_overlay"
private const val KEY_X = "x"
private const val KEY_Y = "y"
private const val KEY_DETAIL = "detail"

/**
 * Floating pill drawn over the game while a session is recording:
 *
 *   61 FPS
 *   42°C · 587 MHz · 4.8 W
 *
 * FPS and the metric line are always visible -- nothing to tap just to see
 * them. The FPS number is coloured against the recent peak (so it means the
 * same thing on a 30, 60 or 120 FPS game). A tap instead reveals the one
 * thing that genuinely benefits from being out of the way by default: the
 * timer and the stop control, so a stray tap while repositioning the
 * overlay can't end the recording. A drag moves it; the position and
 * whether controls are expanded are both remembered.
 *
 * Built from plain Views because a Compose overlay would need its own
 * lifecycle plumbing for no visual gain. Every window operation is posted to
 * the main thread, so it is safe to call from the service's IO coroutines.
 */
class FpsOverlay(context: Context, private val onStop: () -> Unit) {

    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val startedAt = SystemClock.elapsedRealtime()

    private var container: LinearLayout? = null
    private var fpsView: TextView? = null
    private var metricsView: TextView? = null
    private var timerView: TextView? = null
    private var controlsRow: LinearLayout? = null

    @Volatile private var latest = LiveMetrics()
    private var peakFps = 0f
    @Volatile private var scale = 1f
    @Volatile private var opacityPercent = OverlayStyle.DEFAULT_OPACITY_PERCENT

    fun canShow(): Boolean = Settings.canDrawOverlays(appContext)

    fun show() {
        handler.post {
            if (container == null && canShow()) attach()
        }
    }

    fun update(metrics: LiveMetrics) {
        latest = metrics
        handler.post { render() }
    }

    fun hide() {
        handler.post { detach() }
    }

    /** Size (percent of the default) and panel opacity (percent); rebuilds the overlay if it is already showing. */
    fun setStyle(scalePercent: Int, opacity: Int) {
        val newScale = scalePercent / 100f
        if (newScale == scale && opacity == opacityPercent) return
        scale = newScale
        opacityPercent = opacity
        handler.post {
            if (container != null) {
                detach()
                if (canShow()) attach()
            }
        }
    }

    private fun render() {
        val fps = latest.fps
        val number = fpsView ?: return
        number.text = if (fps == null) "-- FPS" else "${String.format("%.0f", fps)} FPS"
        number.setTextColor(colorFor(fps))

        val elapsedSeconds = ((SystemClock.elapsedRealtime() - startedAt) / 1000).toInt()
        timerView?.text = String.format("%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60)

        metricsView?.text = detailText()
    }

    private fun colorFor(fps: Float?): Int {
        if (fps == null) return OverlayStyle.MUTED
        peakFps = maxOf(peakFps * 0.98f, fps)
        val ratio = fps / maxOf(peakFps, 1f)
        return when {
            ratio >= 0.9f -> OverlayStyle.GOOD
            ratio >= 0.65f -> OverlayStyle.WARNING
            else -> OverlayStyle.DANGER
        }
    }

    private fun detailText(): String {
        val parts = mutableListOf<String>()
        latest.cpuTempCelsius?.let { parts.add(String.format("CPU %.0f\u00B0C", it)) }
        latest.gpuFreqRaw?.let { parts.add("GPU ${gpuFreqMhz(it)} MHz") }
        latest.powerWatts?.let { parts.add(String.format("%.1f W", it)) }
        return if (parts.isEmpty()) "--" else parts.joinToString(" \u00B7 ")
    }

    private fun attach() {
        val density = appContext.resources.displayMetrics.density
        val scaleNow = scale
        fun dp(value: Int): Int = (value * density * scaleNow).toInt().coerceAtLeast(1)
        val shadow = 0xCC000000.toInt()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt(KEY_X, dp(24))
            y = prefs.getInt(KEY_Y, dp(48))
        }

        // "61 FPS" -- bold sans, not monospace, no unit/accent decoration. Always visible.
        val number = TextView(appContext).apply {
            text = "-- FPS"
            setTextColor(OverlayStyle.MUTED)
            textSize = 15f * scaleNow
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(dp(2).toFloat(), 0f, 0f, shadow)
        }
        // "42°C · 587 MHz · 4.8 W" -- small, muted, always visible underneath.
        val metrics = TextView(appContext).apply {
            text = "--"
            setTextColor(OverlayStyle.MUTED)
            textSize = 10f * scaleNow
            typeface = Typeface.DEFAULT
            setShadowLayer(dp(2).toFloat(), 0f, 0f, shadow)
            setPadding(0, dp(2), 0, 0)
        }
        // Timer + stop: collapsed by default (per the reference design), revealed by a tap.
        val timer = TextView(appContext).apply {
            text = "00:00"
            setTextColor(OverlayStyle.MUTED)
            textSize = 10f * scaleNow
            typeface = Typeface.DEFAULT
        }
        val stop = FrameLayout(appContext).apply {
            background = OverlayStyle.stopBackground(dp(2).toFloat())
            val square = View(appContext).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(1).toFloat()
                    setColor(Color.WHITE)
                }
            }
            addView(square, FrameLayout.LayoutParams(dp(8), dp(8), Gravity.CENTER))
            setOnClickListener { onStop() }
        }
        val controls = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, 0)
            addView(timer)
            addView(stop, LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginStart = dp(10) })
            visibility = if (prefs.getBoolean(KEY_DETAIL, false)) View.VISIBLE else View.GONE
        }

        val box = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = OverlayStyle.panelBackground(dp(OverlayStyle.CORNER_RADIUS_DP).toFloat(), dp(1), opacityPercent)
            addView(number, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(metrics, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(controls, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        val tapSlop = dp(6)
        box.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var touchX = 0f
            private var touchY = 0f
            private var moved = false

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x
                        startY = params.y
                        touchX = event.rawX
                        touchY = event.rawY
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - touchX
                        val dy = event.rawY - touchY
                        if (!moved && (Math.abs(dx) > tapSlop || Math.abs(dy) > tapSlop)) moved = true
                        if (moved) {
                            params.x = startX + dx.toInt()
                            params.y = startY + dy.toInt()
                            try {
                                windowManager.updateViewLayout(view, params)
                            } catch (t: Throwable) {
                                Logger.w(TAG, "Could not move overlay: ${t.message}")
                            }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (moved) {
                            prefs.edit().putInt(KEY_X, params.x).putInt(KEY_Y, params.y).apply()
                        } else {
                            toggleDetail()
                        }
                        return true
                    }
                }
                return false
            }
        })

        try {
            windowManager.addView(box, params)
            container = box
            fpsView = number
            metricsView = metrics
            timerView = timer
            controlsRow = controls
            render()
        } catch (t: Throwable) {
            Logger.e(TAG, "Could not show FPS overlay", t)
        }
    }

    private fun toggleDetail() {
        val controls = controlsRow ?: return
        val show = controls.visibility != View.VISIBLE
        controls.visibility = if (show) View.VISIBLE else View.GONE
        prefs.edit().putBoolean(KEY_DETAIL, show).apply()
        render()
    }

    private fun detach() {
        val view = container ?: return
        try {
            windowManager.removeView(view)
        } catch (t: Throwable) {
            Logger.w(TAG, "Could not remove overlay: ${t.message}")
        }
        container = null
        fpsView = null
        metricsView = null
        timerView = null
        controlsRow = null
    }
}

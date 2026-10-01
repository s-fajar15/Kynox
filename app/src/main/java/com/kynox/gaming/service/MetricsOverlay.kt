package com.kynox.gaming.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
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
import com.kynox.gaming.domain.model.OverlaySettings
import com.kynox.gaming.domain.model.QuickOverlayMetrics

private const val TAG = "MetricsOverlay"
private const val PREFS = "kynox_quick_overlay"
private const val KEY_X = "x"
private const val KEY_Y = "y"

/**
 * Compact floating row showing whichever live metrics the user picked in
 * Settings (FPS, CPU usage, GPU usage/frequency, battery temperature) --
 * with no session recording behind it, unlike [FpsOverlay]. Draggable, with
 * its position remembered; a small (x) button ends it.
 */
class MetricsOverlay(context: Context, private val onStop: () -> Unit) {

    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private var container: LinearLayout? = null
    private var textView: TextView? = null

    @Volatile private var latest = QuickOverlayMetrics(null, null, null, null, null)
    @Volatile private var settings = OverlaySettings()
    @Volatile private var scale = 1f
    @Volatile private var opacityPercent = OverlayStyle.DEFAULT_OPACITY_PERCENT

    fun canShow(): Boolean = Settings.canDrawOverlays(appContext)

    fun show() {
        handler.post {
            if (container == null && canShow()) attach()
        }
    }

    fun update(metrics: QuickOverlayMetrics, overlaySettings: OverlaySettings) {
        latest = metrics
        settings = overlaySettings
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
        val view = textView ?: return
        val parts = mutableListOf<String>()
        if (settings.showFps) parts.add("FPS " + (latest.fps?.let { String.format("%.0f", it) } ?: "--"))
        if (settings.showCpu) parts.add("CPU " + (latest.cpuUsagePercent?.let { String.format("%.0f%%", it) } ?: "--"))
        if (settings.showGpu) {
            parts.add(
                "GPU " + (
                    latest.gpuUsagePercent?.let { String.format("%.0f%%", it) }
                        ?: latest.gpuFreqRaw?.let { "${gpuFreqMhz(it)}MHz" }
                        ?: "--"
                    )
            )
        }
        if (settings.showBatteryTemp) parts.add("BAT " + (latest.batteryTempCelsius?.let { String.format("%.0f\u00B0C", it) } ?: "--"))
        view.text = if (parts.isEmpty()) "--" else parts.joinToString("  \u00B7  ")
    }

    private fun attach() {
        val density = appContext.resources.displayMetrics.density
        val scaleNow = scale
        fun dp(value: Int): Int = (value * density * scaleNow).toInt().coerceAtLeast(1)

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
            y = prefs.getInt(KEY_Y, dp(96))
        }

        val text = TextView(appContext).apply {
            text = "--"
            setTextColor(OverlayStyle.TEXT)
            textSize = 12f * scaleNow
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setShadowLayer(dp(3).toFloat(), 0f, 0f, 0x99000000.toInt())
        }
        val stop = FrameLayout(appContext).apply {
            background = OverlayStyle.stopBackground(dp(2).toFloat())
            val square = View(appContext).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(1).toFloat()
                    setColor(Color.WHITE)
                }
            }
            addView(square, FrameLayout.LayoutParams(dp(7), dp(7), Gravity.CENTER))
            setOnClickListener { onStop() }
        }

        val row = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(9), dp(8))
            background = OverlayStyle.panelBackground(dp(OverlayStyle.CORNER_RADIUS_DP).toFloat(), dp(1), opacityPercent)
            addView(text)
            addView(stop, LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginStart = dp(12) })
        }

        row.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var touchX = 0f
            private var touchY = 0f

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x
                        startY = params.y
                        touchX = event.rawX
                        touchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - touchX
                        val dy = event.rawY - touchY
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        try {
                            windowManager.updateViewLayout(view, params)
                        } catch (t: Throwable) {
                            Logger.w(TAG, "Could not move overlay: ${t.message}")
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        prefs.edit().putInt(KEY_X, params.x).putInt(KEY_Y, params.y).apply()
                        return true
                    }
                }
                return false
            }
        })

        try {
            windowManager.addView(row, params)
            container = row
            textView = text
            render()
        } catch (t: Throwable) {
            Logger.e(TAG, "Could not show metrics overlay", t)
        }
    }

    private fun detach() {
        val view = container ?: return
        try {
            windowManager.removeView(view)
        } catch (t: Throwable) {
            Logger.w(TAG, "Could not remove overlay: ${t.message}")
        }
        container = null
        textView = null
    }
}

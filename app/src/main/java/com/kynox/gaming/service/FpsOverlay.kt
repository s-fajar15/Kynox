package com.kynox.gaming.service

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
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
private const val KEY_DETAIL = "detail"

/**
 * Floating long, thin HUD drawn over the game while a session is recording:
 *
 *   | 61   ~~~ FPS graph ~~~   CPU    GPU      PWR
 *   | FPS                      42°C   587 MHz  4.8 W
 *
 * FPS, its short history graph and the metric columns are always visible. The
 * FPS number and graph are coloured against the recent peak. A tap reveals the
 * timer, the - / + size buttons and the stop control, so a stray tap while
 * repositioning can't end the recording. A drag moves it; position and whether
 * controls are expanded are remembered.
 *
 * Built from plain Views because a Compose overlay would need its own
 * lifecycle plumbing for no visual gain. Every window operation is posted to
 * the main thread, so it is safe to call from the service's IO coroutines.
 */
class FpsOverlay(
    context: Context,
    /** Dipanggil dengan +/- [OVERLAY_RESIZE_STEP_PERCENT] saat tombol perbesar/perkecil diketuk. */
    private val onResize: (Int) -> Unit = {},
    private val onStop: () -> Unit
) {

    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val startedAt = SystemClock.elapsedRealtime()

    private var container: LinearLayout? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var defaultX = 0
    private var defaultY = 0
    private var configCallbacks: ComponentCallbacks? = null
    private var fpsView: TextView? = null
    private var cpuView: TextView? = null
    private var gpuView: TextView? = null
    private var pwrView: TextView? = null
    private var graphView: SparklineView? = null
    private val history = FpsHistory()
    private var timerView: TextView? = null
    private var controlsRow: LinearLayout? = null
    private var stripeView: View? = null

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
        handler.post {
            history.add(metrics.fps)
            render()
        }
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
        number.text = if (fps == null) "--" else String.format("%.0f", fps)
        if (fps != null) peakFps = maxOf(peakFps * 0.98f, fps)
        val statusColor = fpsStatusColor(fps, peakFps)
        number.setTextColor(statusColor)
        (stripeView?.background as? GradientDrawable)?.setColor(statusColor)
        graphView?.setData(history.snapshot(), statusColor)

        val elapsedSeconds = ((SystemClock.elapsedRealtime() - startedAt) / 1000).toInt()
        timerView?.text = String.format("%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60)

        bindColumn(cpuView, "CPU", latest.cpuTempCelsius?.let { String.format("%.0f\u00B0C", it) })
        bindColumn(gpuView, "GPU", latest.gpuFreqRaw?.let { "${gpuFreqMhz(it)} MHz" })
        bindColumn(pwrView, "PWR", latest.powerWatts?.let { String.format("%.1f W", it) })
    }

    /** Kolom yang nilainya tidak tersedia disembunyikan, bukan diisi tanda hubung. */
    private fun bindColumn(view: TextView?, label: String, value: String?) {
        view ?: return
        if (value == null) {
            view.visibility = View.GONE
        } else {
            view.visibility = View.VISIBLE
            view.text = columnText(label, value)
        }
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
            x = prefs.getInt(OverlayPosition.keyX(OverlayPosition.isLandscape(appContext)), dp(24))
            y = prefs.getInt(OverlayPosition.keyY(OverlayPosition.isLandscape(appContext)), dp(48))
        }

        // Strip tipis: garis kondisi | FPS | grafik | kolom CPU, GPU, PWR.
        val condensedBold = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)
        val number = TextView(appContext).apply {
            text = "--"
            setTextColor(OverlayStyle.MUTED)
            textSize = 22f * scaleNow
            typeface = condensedBold
            includeFontPadding = false
            gravity = Gravity.START
            setShadowLayer(dp(2).toFloat(), 0f, 0f, shadow)
        }
        val unit = TextView(appContext).apply {
            text = "FPS"
            setTextColor(OverlayStyle.ACCENT)
            textSize = 8f * scaleNow
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            letterSpacing = 0.25f
            includeFontPadding = false
            gravity = Gravity.START
        }
        val left = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            minimumWidth = dp(32)
            addView(number, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(unit, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val stripe = View(appContext).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(1).toFloat()
                setColor(OverlayStyle.MUTED)
            }
        }
        val graph = SparklineView(appContext)
        val divider = View(appContext).apply { setBackgroundColor(OverlayStyle.DIVIDER) }
        fun column(): TextView = TextView(appContext).apply {
            textSize = 12f * scaleNow
            typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
            includeFontPadding = false
            setLineSpacing(0f, 1.05f)
            setTextColor(OverlayStyle.TEXT)
            visibility = View.GONE
        }
        val cpu = column()
        val gpu = column()
        val pwr = column()
        // Timer + ukuran + stop: tersembunyi secara default, muncul saat overlay diketuk.
        val timer = TextView(appContext).apply {
            text = "00:00"
            setTextColor(OverlayStyle.MUTED)
            textSize = 11f * scaleNow
            typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        }
        val stop = FrameLayout(appContext).apply {
            background = OverlayStyle.stopBackground(dp(3).toFloat())
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
            addView(timer, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(sizeButton("\u2212", -OVERLAY_RESIZE_STEP_PERCENT, ::dp), LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginStart = dp(8) })
            addView(sizeButton("+", OVERLAY_RESIZE_STEP_PERCENT, ::dp), LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginStart = dp(6) })
            addView(stop, LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginStart = dp(10) })
            visibility = if (prefs.getBoolean(KEY_DETAIL, false)) View.VISIBLE else View.GONE
        }

        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        val mainRow = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(stripe, LinearLayout.LayoutParams(dp(3), dp(28)).apply { marginEnd = dp(8) })
            addView(left)
            addView(graph, LinearLayout.LayoutParams(dp(104), dp(28)).apply { marginStart = dp(10); marginEnd = dp(10) })
            addView(divider, LinearLayout.LayoutParams(dp(1), dp(24)).apply { marginEnd = dp(2) })
            addView(cpu, LinearLayout.LayoutParams(wrap, wrap).apply { marginStart = dp(10) })
            addView(gpu, LinearLayout.LayoutParams(wrap, wrap).apply { marginStart = dp(12) })
            addView(pwr, LinearLayout.LayoutParams(wrap, wrap).apply { marginStart = dp(12) })
        }
        val box = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(7), dp(14), dp(7))
            background = OverlayStyle.hudBackground(dp(7).toFloat(), dp(1), opacityPercent)
            addView(mainRow, ViewGroup.LayoutParams(wrap, wrap))
            addView(controls, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, wrap))
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
                            val screen = OverlayPosition.screenSize(windowManager, appContext)
                params.x = OverlayPosition.clamp(startX + dx.toInt(), view.width, screen.first)
                            params.y = OverlayPosition.clamp(startY + dy.toInt(), view.height, screen.second)
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
                            val land = OverlayPosition.isLandscape(appContext)
                prefs.edit().putInt(OverlayPosition.keyX(land), params.x).putInt(OverlayPosition.keyY(land), params.y).apply()
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
            windowParams = params
            defaultX = dp(24)
            defaultY = dp(48)
            registerConfigCallbacks()
            box.post { reposition() }
            fpsView = number
            cpuView = cpu
            gpuView = gpu
            pwrView = pwr
            graphView = graph
            timerView = timer
            controlsRow = controls
            stripeView = stripe
            render()
        } catch (t: Throwable) {
            Logger.e(TAG, "Could not show FPS overlay", t)
        }
    }

    private fun sizeButton(label: String, delta: Int, dp: (Int) -> Int): TextView = TextView(appContext).apply {
        text = label
        gravity = Gravity.CENTER
        setTextColor(OverlayStyle.TEXT)
        textSize = 13f * scale
        typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
        background = OverlayStyle.buttonBackground(dp(3).toFloat())
        contentDescription = if (delta > 0) "Perbesar overlay" else "Perkecil overlay"
        setOnClickListener { onResize(delta) }
    }

    private fun toggleDetail() {
        val controls = controlsRow ?: return
        val show = controls.visibility != View.VISIBLE
        controls.visibility = if (show) View.VISIBLE else View.GONE
        prefs.edit().putBoolean(KEY_DETAIL, show).apply()
        render()
    }

    /** Memuat posisi tersimpan untuk orientasi sekarang dan menjepitnya ke ukuran layar. */
    private fun reposition() {
        val view = container ?: return
        val params = windowParams ?: return
        val land = OverlayPosition.isLandscape(appContext)
        val screen = OverlayPosition.screenSize(windowManager, appContext)
        params.x = OverlayPosition.clamp(prefs.getInt(OverlayPosition.keyX(land), defaultX), view.width, screen.first)
        params.y = OverlayPosition.clamp(prefs.getInt(OverlayPosition.keyY(land), defaultY), view.height, screen.second)
        try {
            windowManager.updateViewLayout(view, params)
        } catch (t: Throwable) {
            Logger.w(TAG, "Could not reposition overlay: ${t.message}")
        }
    }

    private fun registerConfigCallbacks() {
        if (configCallbacks != null) return
        val callbacks = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                // Ukuran layar baru baru tersedia sesaat setelah rotasi.
                handler.postDelayed({ reposition() }, 250)
            }

            override fun onLowMemory() = Unit
        }
        appContext.registerComponentCallbacks(callbacks)
        configCallbacks = callbacks
    }

    private fun detach() {
        val view = container ?: return
        try {
            windowManager.removeView(view)
        } catch (t: Throwable) {
            Logger.w(TAG, "Could not remove overlay: ${t.message}")
        }
        configCallbacks?.let { appContext.unregisterComponentCallbacks(it) }
        configCallbacks = null
        windowParams = null
        container = null
        fpsView = null
        cpuView = null
        gpuView = null
        pwrView = null
        graphView = null
        timerView = null
        controlsRow = null
        stripeView = null
    }
}

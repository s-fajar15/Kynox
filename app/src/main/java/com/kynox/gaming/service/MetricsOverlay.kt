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

/**
 * Long, thin floating strip showing whichever live metrics the user picked in
 * Settings (FPS with a short graph, CPU usage, GPU usage/frequency, battery
 * temperature) -- with no session recording behind it, unlike [FpsOverlay].
 * Draggable, with its position remembered; small - / + buttons resize it and
 * an (x) button ends it.
 */
class MetricsOverlay(
    context: Context,
    /** Dipanggil dengan +/- [OVERLAY_RESIZE_STEP_PERCENT] saat tombol perbesar/perkecil diketuk. */
    private val onResize: (Int) -> Unit = {},
    private val onStop: () -> Unit
) {

    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private var container: LinearLayout? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var defaultX = 0
    private var defaultY = 0
    private var configCallbacks: ComponentCallbacks? = null
    private var tilesHost: LinearLayout? = null
    private var tileViews: List<TextView> = emptyList()
    private var graphView: SparklineView? = null
    private val history = FpsHistory()
    private var peakFps = 0f
    private var tileSignature: String = ""

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
        handler.post {
            history.add(if (overlaySettings.showFps) metrics.fps else null)
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

    private data class Tile(val label: String, val value: String)

    private fun currentTiles(): List<Tile> {
        val tiles = mutableListOf<Tile>()
        if (settings.showFps) tiles.add(Tile("FPS", latest.fps?.let { String.format("%.0f", it) } ?: "--"))
        if (settings.showCpu) tiles.add(Tile("CPU", latest.cpuUsagePercent?.let { String.format("%.0f%%", it) } ?: "--"))
        if (settings.showGpu) {
            tiles.add(
                Tile(
                    "GPU",
                    latest.gpuUsagePercent?.let { String.format("%.0f%%", it) }
                        ?: latest.gpuFreqRaw?.let { "${gpuFreqMhz(it)}MHz" }
                        ?: "--"
                )
            )
        }
        if (settings.showBatteryTemp) tiles.add(Tile("BAT", latest.batteryTempCelsius?.let { String.format("%.0f\u00B0C", it) } ?: "--"))
        return tiles
    }

    private fun render() {
        val host = tilesHost ?: return
        val tiles = currentTiles()
        val signature = tiles.joinToString("|") { it.label }
        if (signature != tileSignature) rebuildTiles(host, tiles, signature)
        val fps = latest.fps
        if (fps != null) peakFps = maxOf(peakFps * 0.98f, fps)
        val fpsColor = fpsStatusColor(fps, peakFps)
        tiles.forEachIndexed { index, tile ->
            val color = if (tile.label == "FPS") fpsColor else OverlayStyle.TEXT
            tileViews.getOrNull(index)?.text = columnText(tile.label, tile.value, color)
        }
        graphView?.setData(history.snapshot(), fpsColor)
    }

    private fun rebuildTiles(host: LinearLayout, tiles: List<Tile>, signature: String) {
        val density = appContext.resources.displayMetrics.density
        val scaleNow = scale
        fun dp(value: Int): Int = (value * density * scaleNow).toInt().coerceAtLeast(1)
        host.removeAllViews()
        val views = mutableListOf<TextView>()
        var graph: SparklineView? = null
        // Satu kolom per metrik (label kecil di atas, nilai di bawah); grafik FPS menempel setelah kolom FPS.
        tiles.forEachIndexed { index, tile ->
            val column = TextView(appContext).apply {
                textSize = 12.5f * scaleNow
                typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
                includeFontPadding = false
                setLineSpacing(0f, 1.05f)
                setTextColor(OverlayStyle.TEXT)
                minWidth = dp(30)
            }
            host.addView(column, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                if (index > 0) marginStart = dp(12)
            })
            views.add(column)
            if (tile.label == "FPS") {
                val spark = SparklineView(appContext)
                host.addView(spark, LinearLayout.LayoutParams(dp(96), dp(26)).apply { marginStart = dp(8) })
                graph = spark
            }
        }
        tileViews = views
        graphView = graph
        tileSignature = signature
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
            x = prefs.getInt(OverlayPosition.keyX(OverlayPosition.isLandscape(appContext)), dp(24))
            y = prefs.getInt(OverlayPosition.keyY(OverlayPosition.isLandscape(appContext)), dp(96))
        }

        val host = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val stop = FrameLayout(appContext).apply {
            background = OverlayStyle.stopBackground(dp(3).toFloat())
            val square = View(appContext).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(1).toFloat()
                    setColor(Color.WHITE)
                }
            }
            addView(square, FrameLayout.LayoutParams(dp(7), dp(7), Gravity.CENTER))
            setOnClickListener { onStop() }
        }
        val accentBar = View(appContext).apply { setBackgroundColor(OverlayStyle.ACCENT) }
        val divider = View(appContext).apply { setBackgroundColor(OverlayStyle.DIVIDER) }

        val row = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(7), dp(12), dp(7))
            background = OverlayStyle.hudBackground(dp(7).toFloat(), dp(1), opacityPercent)
            addView(accentBar, LinearLayout.LayoutParams(dp(3), dp(26)).apply { marginEnd = dp(8) })
            addView(host)
            addView(divider, LinearLayout.LayoutParams(dp(1), dp(24)).apply { marginStart = dp(12); marginEnd = dp(10) })
            addView(sizeButton("\u2212", -OVERLAY_RESIZE_STEP_PERCENT, ::dp), LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(5) })
            addView(sizeButton("+", OVERLAY_RESIZE_STEP_PERCENT, ::dp), LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(7) })
            addView(stop, LinearLayout.LayoutParams(dp(20), dp(20)))
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
                        val screen = OverlayPosition.screenSize(windowManager, appContext)
                params.x = OverlayPosition.clamp(startX + dx.toInt(), view.width, screen.first)
                        params.y = OverlayPosition.clamp(startY + dy.toInt(), view.height, screen.second)
                        try {
                            windowManager.updateViewLayout(view, params)
                        } catch (t: Throwable) {
                            Logger.w(TAG, "Could not move overlay: ${t.message}")
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val land = OverlayPosition.isLandscape(appContext)
                prefs.edit().putInt(OverlayPosition.keyX(land), params.x).putInt(OverlayPosition.keyY(land), params.y).apply()
                        return true
                    }
                }
                return false
            }
        })

        try {
            windowManager.addView(row, params)
            container = row
            windowParams = params
            defaultX = dp(24)
            defaultY = dp(96)
            registerConfigCallbacks()
            row.post { reposition() }
            tilesHost = host
            tileSignature = ""
            render()
        } catch (t: Throwable) {
            Logger.e(TAG, "Could not show metrics overlay", t)
        }
    }

    private fun sizeButton(label: String, delta: Int, dp: (Int) -> Int): TextView = TextView(appContext).apply {
        text = label
        gravity = Gravity.CENTER
        setTextColor(OverlayStyle.TEXT)
        textSize = 12f * scale
        typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
        background = OverlayStyle.buttonBackground(dp(3).toFloat())
        contentDescription = if (delta > 0) "Perbesar overlay" else "Perkecil overlay"
        setOnClickListener { onResize(delta) }
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
        tilesHost = null
        tileViews = emptyList()
        graphView = null
        tileSignature = ""
    }
}

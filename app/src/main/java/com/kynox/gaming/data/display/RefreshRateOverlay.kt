package com.kynox.gaming.data.display

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Jendela overlay 1px hampir transparan yang meminta display mode tertentu.
 * Dipakai sebagai jalur tanpa root: sistem mengikuti preferredDisplayModeId
 * dari jendela yang sedang terlihat. Perlu izin "tampil di atas aplikasi lain".
 */
class RefreshRateOverlay(private val context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private var view: View? = null

    fun canShow(): Boolean = try { Settings.canDrawOverlays(context) } catch (_: Throwable) { false }

    fun show(modeId: Int, hz: Float) {
        if (!canShow()) return
        main.post {
            try {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                @Suppress("DEPRECATION")
                val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else WindowManager.LayoutParams.TYPE_PHONE
                val params = WindowManager.LayoutParams(
                    1, 1,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    preferredDisplayModeId = modeId
                    preferredRefreshRate = hz
                }
                val existing = view
                if (existing != null) {
                    wm.updateViewLayout(existing, params)
                } else {
                    val v = View(context).apply { setBackgroundColor(0x01000000) }
                    wm.addView(v, params)
                    view = v
                }
            } catch (_: Throwable) {
                view = null
            }
        }
    }

    fun hide() {
        main.post {
            val v = view ?: return@post
            try {
                (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeViewImmediate(v)
            } catch (_: Throwable) {
            }
            view = null
        }
    }
}

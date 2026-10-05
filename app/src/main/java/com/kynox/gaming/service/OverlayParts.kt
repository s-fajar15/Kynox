package com.kynox.gaming.service

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.WindowManager
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.view.View
import kotlin.math.ceil

/** Jumlah titik riwayat FPS pada grafik overlay (satu titik per pembaruan, sekitar satu menit). */
internal const val OVERLAY_GRAPH_POINTS = 60

/** Riwayat FPS pendek untuk grafik overlay. Nilai null (tidak terbaca) disimpan sebagai celah. */
internal class FpsHistory(private val capacity: Int = OVERLAY_GRAPH_POINTS) {
    private val values = ArrayDeque<Float?>()

    fun add(value: Float?) {
        values.addLast(value)
        while (values.size > capacity) values.removeFirst()
    }

    fun snapshot(): List<Float?> = values.toList()
}

/** Warna FPS terhadap puncak terbaru, supaya artinya sama di game 30, 60, atau 120 FPS. */
internal fun fpsStatusColor(fps: Float?, peak: Float): Int {
    if (fps == null) return OverlayStyle.MUTED
    val ratio = fps / maxOf(peak, 1f)
    return when {
        ratio >= 0.9f -> OverlayStyle.GOOD
        ratio >= 0.65f -> OverlayStyle.WARNING
        else -> OverlayStyle.DANGER
    }
}

/** Dua baris: label kecil redup di atas, nilai terang di bawah. */
internal fun columnText(label: String, value: String, valueColor: Int = OverlayStyle.TEXT): CharSequence {
    val sb = SpannableStringBuilder()
    sb.append(label)
    sb.setSpan(ForegroundColorSpan(OverlayStyle.MUTED), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    sb.setSpan(RelativeSizeSpan(0.72f), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    sb.append('\n')
    val start = sb.length
    sb.append(value)
    sb.setSpan(ForegroundColorSpan(valueColor), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    return sb
}

/**
 * Grafik garis kecil untuk riwayat FPS: garis berwarna kondisi, isian gradien
 * tipis, dan satu garis bantu di tengah. Skala sumbu Y kelipatan 30 FPS
 * (minimal 60) supaya bentuknya tidak melompat-lompat tiap pembaruan.
 */
internal class SparklineView(context: Context) : View(context) {
    private var data: List<Float?> = emptyList()
    private var color: Int = OverlayStyle.MUTED
    private val density = context.resources.displayMetrics.density

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val grid = Paint().apply { color = 0x26FFFFFF }
    private val linePath = Path()
    private val fillPath = Path()

    fun setData(values: List<Float?>, lineColor: Int) {
        data = values
        color = lineColor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        grid.strokeWidth = 1f
        canvas.drawLine(0f, h / 2f, w, h / 2f, grid)
        canvas.drawLine(0f, h - 0.5f, w, h - 0.5f, grid)
        if (data.size < 2) return

        val peak = data.filterNotNull().maxOrNull() ?: return
        val maxY = maxOf(60f, ceil(peak / 30f) * 30f)
        val pad = 2f * density
        val usableH = h - pad * 2f
        val step = w / (OVERLAY_GRAPH_POINTS - 1)
        val offset = OVERLAY_GRAPH_POINTS - data.size

        line.color = color
        line.strokeWidth = 1.5f * density
        val transparent = color and 0x00FFFFFF
        fill.shader = LinearGradient(0f, 0f, 0f, h, transparent or 0x66000000, transparent, Shader.TileMode.CLAMP)

        linePath.reset()
        fillPath.reset()
        var segmentStartX = 0f
        var lastX = 0f
        var open = false
        data.forEachIndexed { i, value ->
            if (value == null) {
                if (open) closeSegment(segmentStartX, lastX, h)
                open = false
                return@forEachIndexed
            }
            val x = (offset + i) * step
            val y = pad + usableH * (1f - (value / maxY).coerceIn(0f, 1f))
            if (!open) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
                segmentStartX = x
                open = true
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            lastX = x
        }
        if (open) closeSegment(segmentStartX, lastX, h)
        canvas.drawPath(fillPath, fill)
        canvas.drawPath(linePath, line)
    }

    private fun closeSegment(startX: Float, endX: Float, h: Float) {
        fillPath.lineTo(endX, h)
        fillPath.lineTo(startX, h)
        fillPath.close()
    }
}

/**
 * Posisi overlay disimpan terpisah untuk potret dan landscape, dan selalu
 * dijepit ke ukuran layar saat ini. Tanpa ini, koordinat yang disimpan di
 * potret (mis. y = 1500) jatuh di luar layar saat game berputar ke landscape,
 * dan overlay terlihat "tidak muncul".
 */
internal object OverlayPosition {
    private val OVERLAY_PREFS = listOf("kynox_overlay", "kynox_quick_overlay")

    fun keyX(landscape: Boolean): String = if (landscape) "x_land" else "x"
    fun keyY(landscape: Boolean): String = if (landscape) "y_land" else "y"

    /** Menjepit [value] supaya view berukuran [viewSize] tetap sepenuhnya di dalam layar berukuran [screenSize]. */
    fun clamp(value: Int, viewSize: Int, screenSize: Int): Int = value.coerceIn(0, maxOf(0, screenSize - viewSize))

    fun isLandscape(context: Context): Boolean =
        context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    /** Lebar dan tinggi layar pada orientasi sekarang. */
    fun screenSize(windowManager: WindowManager, context: Context): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= 30) {
            val bounds = windowManager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val dm = context.resources.displayMetrics
            dm.widthPixels to dm.heightPixels
        }

    /** Menghapus semua posisi tersimpan; overlay kembali ke posisi awal saat ditampilkan berikutnya. */
    fun reset(context: Context) {
        OVERLAY_PREFS.forEach { name ->
            context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
                .remove(keyX(false)).remove(keyY(false)).remove(keyX(true)).remove(keyY(true))
                .apply()
        }
    }
}

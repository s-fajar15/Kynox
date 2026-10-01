package com.kynox.gaming.data.gaming

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.media.MediaScannerConnection
import com.kynox.gaming.core.root.RootExecutor
import com.kynox.gaming.core.utils.Logger
import com.kynox.gaming.core.utils.gpuFreqMhz
import com.kynox.gaming.domain.model.SessionReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

private const val TAG = "SessionExporter"
private const val PUBLIC_DIR = "/storage/emulated/0/Documents/Kynox"

private const val PAGE_WIDTH = 595
private const val PAGE_HEIGHT = 842
private const val PAGE_MARGIN = 24

/**
 * Session report exports that are not images: a CSV of every sample (for a
 * spreadsheet) and a PDF made from the same rendered report the image
 * export uses, cut into A4 pages. Like [com.kynox.gaming.ui.components.exportBitmapToGallery]
 * the finished file is copied into shared storage through root, so no
 * storage permission is needed.
 */
object SessionExporter {

    suspend fun exportCsv(context: Context, rootExecutor: RootExecutor, report: SessionReport): Boolean =
        withContext(Dispatchers.IO) {
            val cacheFile = File(context.cacheDir, fileName(report, "csv"))
            try {
                cacheFile.writeText(buildCsv(report))
                copyToPublic(context, rootExecutor, cacheFile)
            } catch (t: Throwable) {
                Logger.e(TAG, "CSV export failed", t)
                false
            } finally {
                cacheFile.delete()
            }
        }

    suspend fun exportPdf(context: Context, rootExecutor: RootExecutor, bitmap: Bitmap, fileNamePrefix: String): Boolean =
        withContext(Dispatchers.IO) {
            val cacheFile = File(context.cacheDir, "${fileNamePrefix}_${System.currentTimeMillis()}.pdf")
            try {
                writePdf(bitmap, cacheFile)
                copyToPublic(context, rootExecutor, cacheFile)
            } catch (t: Throwable) {
                Logger.e(TAG, "PDF export failed", t)
                false
            } finally {
                cacheFile.delete()
            }
        }

    private fun fileName(report: SessionReport, extension: String): String {
        val safeLabel = report.gameLabel.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifBlank { "game" }
        return "kynox_${safeLabel}_${report.startedAtMs}.$extension"
    }

    private fun buildCsv(report: SessionReport): String {
        fun num(value: Float?, digits: Int = 2): String =
            value?.let { String.format(Locale.US, "%.${digits}f", it) } ?: ""

        val builder = StringBuilder()
        builder.append("elapsed_s,fps,cpu_freq_mhz,gpu_freq_mhz,cpu_temp_c,battery_temp_c,power_w,battery_percent,charge_counter_mah\n")
        report.samples.forEach { s ->
            builder.append(String.format(Locale.US, "%.1f", s.elapsedMs / 1000f)).append(',')
            builder.append(num(s.fps, 1)).append(',')
            builder.append(s.avgCpuFreqKhz?.let { (it / 1000).toString() } ?: "").append(',')
            builder.append(s.gpuFreqKhz?.let { gpuFreqMhz(it).toString() } ?: "").append(',')
            builder.append(num(s.cpuTempCelsius, 1)).append(',')
            builder.append(num(s.batteryTempCelsius, 1)).append(',')
            builder.append(num(s.powerWatts, 2)).append(',')
            builder.append(s.batteryPercent?.toString() ?: "").append(',')
            builder.append(s.chargeCounterMicroAh?.let { String.format(Locale.US, "%.1f", it / 1000f) } ?: "")
            builder.append('\n')
        }
        return builder.toString()
    }

    private fun writePdf(bitmap: Bitmap, target: File) {
        val document = PdfDocument()
        try {
            val contentWidth = PAGE_WIDTH - PAGE_MARGIN * 2
            val contentHeight = PAGE_HEIGHT - PAGE_MARGIN * 2
            val scale = contentWidth.toFloat() / bitmap.width
            val sliceHeightPx = (contentHeight / scale).toInt().coerceAtLeast(1)

            var top = 0
            var pageNumber = 1
            while (top < bitmap.height) {
                val bottom = minOf(top + sliceHeightPx, bitmap.height)
                val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                val canvas = page.canvas
                canvas.save()
                canvas.translate(PAGE_MARGIN.toFloat(), PAGE_MARGIN.toFloat())
                canvas.scale(scale, scale)
                canvas.drawBitmap(
                    bitmap,
                    Rect(0, top, bitmap.width, bottom),
                    RectF(0f, 0f, bitmap.width.toFloat(), (bottom - top).toFloat()),
                    null
                )
                canvas.restore()
                document.finishPage(page)
                top = bottom
                pageNumber++
            }
            FileOutputStream(target).use { document.writeTo(it) }
        } finally {
            document.close()
        }
    }

    private suspend fun copyToPublic(context: Context, rootExecutor: RootExecutor, source: File): Boolean {
        val publicPath = "$PUBLIC_DIR/${source.name}"
        val result = rootExecutor.execute(
            "mkdir -p '$PUBLIC_DIR' && cp '${source.absolutePath}' '$publicPath' && chmod 644 '$publicPath'",
            timeoutMs = 8000L
        )
        if (result.isSuccess) {
            MediaScannerConnection.scanFile(context, arrayOf(publicPath), null, null)
        } else {
            Logger.e(TAG, "Root copy failed: ${result.stderr}")
        }
        return result.isSuccess
    }
}

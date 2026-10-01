package com.kynox.gaming.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import com.kynox.gaming.core.root.RootExecutor
import com.kynox.gaming.core.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val TAG = "ImageExport"
private const val PUBLIC_DIR = "/storage/emulated/0/Pictures/Kynox"

/**
 * Writes [bitmap] to the device's Pictures/Kynox folder so it shows up in
 * the gallery like any photo. Kynox already needs root for every other
 * feature, so the export reuses that instead of asking for the separate
 * storage permission a non-rooted app would need on Android 9 and below --
 * one thing to grant, not two.
 */
suspend fun exportBitmapToGallery(
    context: Context,
    rootExecutor: RootExecutor,
    bitmap: Bitmap,
    fileNamePrefix: String
): Boolean = withContext(Dispatchers.IO) {
    val fileName = "${fileNamePrefix}_${System.currentTimeMillis()}.png"
    val cacheFile = File(context.cacheDir, fileName)
    try {
        FileOutputStream(cacheFile).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }

        val publicPath = "$PUBLIC_DIR/$fileName"
        val result = rootExecutor.execute(
            "mkdir -p '$PUBLIC_DIR' && cp '${cacheFile.absolutePath}' '$publicPath' && chmod 644 '$publicPath'",
            timeoutMs = 8000L
        )
        if (result.isSuccess) {
            MediaScannerConnection.scanFile(context, arrayOf(publicPath), null, null)
        } else {
            Logger.e(TAG, "Root copy failed: ${result.stderr}")
        }
        result.isSuccess
    } catch (t: Throwable) {
        Logger.e(TAG, "Failed to export image", t)
        false
    } finally {
        cacheFile.delete()
    }
}

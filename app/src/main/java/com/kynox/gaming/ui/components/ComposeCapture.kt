package com.kynox.gaming.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Renders [content] off-screen at a fixed width and its own natural
 * height, then reads the whole thing back as one bitmap.
 *
 * A screen longer than one page (a scrolling report, say) only ever has
 * its visible slice drawn to the real display, so a plain screenshot of
 * the screen can't capture it in one image. Instead this composes a
 * throwaway copy of the same content as an invisible child of the
 * activity's own root view -- which is what lets it inherit the
 * lifecycle, view-model store and saved-state owners Compose needs
 * without wiring them up by hand -- lets it lay out to its full height,
 * and reads its pixels directly instead of going through the screen.
 */
suspend fun captureComposableToBitmap(
    anchorView: View,
    widthPx: Int,
    backgroundColor: Int,
    content: @Composable () -> Unit
): Bitmap? {
    val activity = anchorView.context.findActivity() ?: return null
    val root = activity.window?.decorView?.findViewById<ViewGroup>(android.R.id.content) ?: return null

    return suspendCancellableCoroutine { continuation ->
        val composeView = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            visibility = View.INVISIBLE
        }

        fun finish(result: Bitmap?) {
            root.removeView(composeView)
            if (continuation.isActive) continuation.resume(result)
        }

        composeView.setContent(content)
        root.addView(composeView, ViewGroup.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Measure/layout explicitly so a long Compose report is never clipped
        // to the activity viewport. The exported bitmap represents the full
        // report from top to bottom, not only the currently visible slice.
        composeView.post {
            val widthSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            composeView.measure(widthSpec, heightSpec)
            composeView.layout(0, 0, composeView.measuredWidth, composeView.measuredHeight)
        }

        composeView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val widthSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
                val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                composeView.measure(widthSpec, heightSpec)
                composeView.layout(0, 0, composeView.measuredWidth, composeView.measuredHeight)
                val width = composeView.measuredWidth
                val height = composeView.measuredHeight
                if (width <= 0 || height <= 0) return
                composeView.viewTreeObserver.removeOnGlobalLayoutListener(this)

                // Wait one more frame for charts/text to settle, then draw the
                // complete measured height. Nothing is cropped to the viewport.
                composeView.post {
                    val bitmap = try {
                        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bmp ->
                            val canvas = Canvas(bmp)
                            canvas.drawColor(backgroundColor)
                            composeView.draw(canvas)
                        }
                    } catch (t: Throwable) {
                        null
                    }
                    finish(bitmap)
                }
            }
        })

        continuation.invokeOnCancellation { root.post { root.removeView(composeView) } }
    }
}

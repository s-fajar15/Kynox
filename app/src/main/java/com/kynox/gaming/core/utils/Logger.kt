package com.kynox.gaming.core.utils

import android.util.Log

object Logger {
    private const val TAG = "Kynox"
    var sink: ((level: String, tag: String, message: String) -> Unit)? = null

    fun d(tag: String, message: String) {
        Log.d("$TAG:$tag", message)
        sink?.invoke("DEBUG", tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i("$TAG:$tag", message)
        sink?.invoke("INFO", tag, message)
    }

    fun w(tag: String, message: String) {
        Log.w("$TAG:$tag", message)
        sink?.invoke("WARN", tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e("$TAG:$tag", message, throwable)
        sink?.invoke("ERROR", tag, message + (throwable?.let { " :: ${it.message}" } ?: ""))
    }
}

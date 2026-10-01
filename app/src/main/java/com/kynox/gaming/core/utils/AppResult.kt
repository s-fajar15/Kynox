package com.kynox.gaming.core.utils

sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Failure(val message: String, val throwable: Throwable? = null) : AppResult<Nothing>()

    inline fun onSuccess(block: (T) -> Unit): AppResult<T> {
        if (this is Success) block(data)
        return this
    }

    inline fun onFailure(block: (String) -> Unit): AppResult<T> {
        if (this is Failure) block(message)
        return this
    }
}

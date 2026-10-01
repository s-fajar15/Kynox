package com.kynox.gaming.domain.model

data class LogEntry(
    val timestamp: Long,
    val action: String,
    val target: String,
    val previousValue: String?,
    val newValue: String?,
    val result: String,
    val error: String?
)

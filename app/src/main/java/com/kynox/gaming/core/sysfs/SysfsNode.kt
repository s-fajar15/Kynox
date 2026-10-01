package com.kynox.gaming.core.sysfs

data class SysfsNode(
    val path: String,
    val exists: Boolean,
    val readable: Boolean,
    val writable: Boolean,
    val value: String? = null
)

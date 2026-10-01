package com.kynox.gaming.domain.model

enum class TouchNodeKind { SAMPLING_RATE, SENSITIVITY, GAME_MODE, OTHER }

/** One touch-panel control the kernel exposes through sysfs/procfs. Names and meanings differ per vendor. */
data class TouchNode(
    val path: String,
    val name: String,
    val value: String,
    val writable: Boolean,
    val kind: TouchNodeKind
)

data class TouchWriteResult(
    val ok: Boolean,
    /** What the node reads back after the write, when it could be read. */
    val readBack: String?,
    val message: String?
)

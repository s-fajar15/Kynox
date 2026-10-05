package com.kynox.gaming.core.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/**
 * Apakah layar Kynox sedang terlihat. Polling milik layar (proses, CPU, GPU,
 * termal, baterai, dashboard) menunggu di sini selama aplikasi di latar
 * belakang, sehingga tidak ada pembacaan root yang sia-sia saat pengguna
 * sedang di aplikasi lain. Pemantauan latar belakang yang disengaja
 * (MonitorService, GameDetectionService) tidak memakai ini.
 */
object AppVisibility {
    private val visible = MutableStateFlow(true)

    fun set(isVisible: Boolean) {
        visible.value = isVisible
    }

    /** Kembali segera bila aplikasi terlihat; selain itu menunggu sampai terlihat lagi. */
    suspend fun awaitForeground() {
        if (!visible.value) visible.first { it }
    }
}

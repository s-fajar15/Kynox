package com.kynox.gaming.ui.components

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Membuka layar pengaturan tanpa membuat aplikasi crash. Beberapa ROM tidak punya layar tertentu
 * (misalnya akses Jangan Ganggu atau izin ubah pengaturan); dalam kasus itu dibuka Pengaturan utama.
 */
fun Context.startActivitySafely(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Throwable) {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Throwable) {
        }
    }
}

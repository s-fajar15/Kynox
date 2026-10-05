package com.kynox.gaming.ui.logs

import com.kynox.gaming.domain.model.LogEntry

enum class LogFilter { ALL, SUCCESS, FAILED }

/** Menyaring log menurut hasil dan kata kunci (aksi, target, nilai lama/baru, galat; tidak peka huruf besar). */
fun filterLogs(logs: List<LogEntry>, filter: LogFilter, query: String): List<LogEntry> {
    val q = query.trim()
    return logs.filter { entry ->
        val matchesFilter = when (filter) {
            LogFilter.ALL -> true
            LogFilter.SUCCESS -> entry.result == "SUCCESS"
            LogFilter.FAILED -> entry.result != "SUCCESS"
        }
        matchesFilter && (q.isEmpty() ||
            entry.action.contains(q, ignoreCase = true) ||
            logActionLabel(entry.action).contains(q, ignoreCase = true) ||
            entry.target.contains(q, ignoreCase = true) ||
            (entry.previousValue?.contains(q, ignoreCase = true) == true) ||
            (entry.newValue?.contains(q, ignoreCase = true) == true) ||
            (entry.error?.contains(q, ignoreCase = true) == true))
    }
}

private val actionLabels = mapOf(
    "APPLY_PROFILE" to "Terapkan profil",
    "RESET_PROFILE" to "Kembalikan profil asli",
    "APPLY_REFRESH_RATE" to "Atur refresh rate",
    "AUTOMATION_APPLY" to "Automation diterapkan",
    "AUTOMATION_RESTORE" to "Automation dikembalikan",
    "AUTO_GAME_MODE_ON" to "Mode Game otomatis aktif",
    "AUTO_GAME_MODE_OFF" to "Mode Game otomatis selesai",
    "AUTO_GAME_MODE_SWITCH" to "Mode Game ganti profil",
    "CLEAN_CACHE" to "Bersihkan cache",
    "CLEAN_RAM" to "Bersihkan RAM",
    "DISABLE_THERMAL_PARAM" to "Nonaktifkan parameter termal",
    "RESTORE_THERMAL" to "Pulihkan termal",
    "RESTORE_THERMAL_PARAM" to "Pulihkan parameter termal",
    "RESTORE_THERMAL_POLICY" to "Pulihkan kebijakan termal",
    "RELAX_THERMAL_TRIP" to "Longgarkan titik trip termal",
    "SET_THERMAL_POLICY" to "Atur kebijakan termal",
    "STOP_THERMAL_SERVICE" to "Hentikan layanan termal",
    "PAUSE_CHARGING" to "Jeda pengisian",
    "RESUME_CHARGING" to "Lanjutkan pengisian",
    "RESTORE_CHARGE_CURRENT" to "Pulihkan arus pengisian",
    "START_FAST_CHARGE_DAEMON" to "Mulai pengisian cepat",
    "STOP_FAST_CHARGE_DAEMON" to "Hentikan pengisian cepat",
    "ROLLBACK_MIN_FREQ" to "Batalkan frekuensi minimum",
    "ROLLBACK_PROFILE_PARAM" to "Batalkan parameter profil",
    "SET_CORE_ONLINE" to "Atur status inti CPU",
    "SET_GOVERNOR" to "Atur governor CPU",
    "SET_MAX_FREQ" to "Atur frekuensi maksimum CPU",
    "SET_MIN_FREQ" to "Atur frekuensi minimum CPU",
    "SET_GPU_GOVERNOR" to "Atur governor GPU",
    "SET_GPU_MAX_FREQ" to "Atur frekuensi maksimum GPU",
    "RESTORE_BACKUP" to "Pulihkan cadangan"
)

/** Nama aksi log yang ramah dibaca. Aksi yang belum dikenal ditampilkan apa adanya. */
fun logActionLabel(action: String): String = actionLabels[action] ?: action

fun logResultLabel(result: String): String = if (result == "SUCCESS") "Berhasil" else "Gagal"

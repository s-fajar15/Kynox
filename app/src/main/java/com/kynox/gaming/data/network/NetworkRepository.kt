package com.kynox.gaming.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class NetworkSnapshot(val timestamp: Long, val rxBytes: Long, val txBytes: Long, val transport: String, val downBps: Long, val upBps: Long)

class NetworkRepository(private val context: Context) {
    private val file get() = File(context.filesDir, "network_history.json")
    private var lastRx = TrafficStats.getTotalRxBytes().coerceAtLeast(0)
    private var lastTx = TrafficStats.getTotalTxBytes().coerceAtLeast(0)
    private var lastTime = System.currentTimeMillis()

    fun currentTransport(): String = try {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val network = cm?.activeNetwork
        val caps = if (cm != null && network != null) cm.getNetworkCapabilities(network) else null
        when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi‑Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Seluler"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Online"
        }
    } catch (_: SecurityException) {
        "Tidak diketahui"
    }

    suspend fun sample(): NetworkSnapshot = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val rx = TrafficStats.getTotalRxBytes().coerceAtLeast(0)
        val tx = TrafficStats.getTotalTxBytes().coerceAtLeast(0)
        val dt = ((now - lastTime).coerceAtLeast(1)) / 1000.0
        val sample = NetworkSnapshot(now, rx, tx, currentTransport(), ((rx - lastRx).coerceAtLeast(0) / dt).toLong(), ((tx - lastTx).coerceAtLeast(0) / dt).toLong())
        lastRx = rx; lastTx = tx; lastTime = now
        append(sample)
        sample
    }

    suspend fun history(): List<NetworkSnapshot> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        runCatching {
            val a = JSONArray(file.readText())
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                NetworkSnapshot(o.getLong("t"), o.getLong("rx"), o.getLong("tx"), o.optString("transport"), o.optLong("down"), o.optLong("up"))
            }
        }.getOrDefault(emptyList())
    }

    private fun append(s: NetworkSnapshot) {
        runCatching {
            val old = if (file.exists()) JSONArray(file.readText()) else JSONArray()
            old.put(JSONObject().apply { put("t", s.timestamp); put("rx", s.rxBytes); put("tx", s.txBytes); put("transport", s.transport); put("down", s.downBps); put("up", s.upBps) })
            while (old.length() > 240) old.remove(0)
            file.writeText(old.toString())
        }
    }
}

package com.kynox.gaming.core.root

import com.kynox.gaming.core.utils.Logger
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Protokol teks untuk shell root yang tetap hidup. Dipisah dari proses aslinya agar bisa diuji.
 *
 * Tiap perintah dibungkus `sh -c '<perintah>'` (tanda kutip dilepas aman), stdin-nya /dev/null
 * supaya perintah yang membaca stdin tidak memakan protokol, lalu diakhiri baris penanda
 * berisi kode keluar.
 */
internal object RootShellProtocol {

    /** Membungkus [command] dalam satu tanda kutip tunggal dengan aman. */
    fun quote(command: String): String = "'" + command.replace("'", "'\\''") + "'"

    fun wrap(command: String, marker: String): String =
        "sh -c ${quote(command)} </dev/null 2>&1; printf '\\n%s%d\\n' '$marker' \$?\n"

    /** Hasil penguraian: isi keluaran dan kode keluar, atau null bila baris penanda belum lengkap. */
    data class Parsed(val output: String, val exitCode: Int)

    fun parse(lines: List<String>, marker: String): Parsed? {
        val index = lines.indexOfFirst { it.startsWith(marker) }
        if (index < 0) return null
        val code = lines[index].removePrefix(marker).trim().toIntOrNull() ?: -1
        return Parsed(lines.subList(0, index).joinToString("\n").trim(), code)
    }
}

/**
 * Satu proses `su` yang dibuka sekali dan dipakai berulang. Inilah yang menghentikan toast
 * "Kynox mendapatkan hak superuser" dari Magisk: toast muncul tiap kali `su` BARU dipanggil,
 * bukan tiap perintah.
 *
 * Bukan thread-safe; pemanggil ([RootExecutorImpl]) menjaganya dengan Mutex.
 */
internal class RootShell {

    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private val ioPool: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "kynox-root-shell").apply { isDaemon = true }
    }
    private var counter = 0L

    class Output(val exitCode: Int, val output: String, val timedOut: Boolean, val shellFailed: Boolean)

    val isOpen: Boolean get() = process?.isAlive == true

    /** Membuka `su`. Mengembalikan false bila gagal (tidak ada su, akses ditolak, dsb). */
    fun open(): Boolean {
        close()
        return try {
            val p = ProcessBuilder("su").redirectErrorStream(true).start()
            process = p
            writer = BufferedWriter(OutputStreamWriter(p.outputStream))
            reader = BufferedReader(InputStreamReader(p.inputStream))
            true
        } catch (t: Throwable) {
            Logger.e(TAG, "Cannot start su shell", t)
            close()
            false
        }
    }

    fun run(command: String, timeoutMs: Long): Output {
        val w = writer
        val r = reader
        val p = process
        if (w == null || r == null || p == null || !p.isAlive) return Output(-1, "", timedOut = false, shellFailed = true)

        val marker = "__KYNOX_END_${System.nanoTime()}_${counter++}__"
        val task = Callable<RootShellProtocol.Parsed?> {
            w.write(RootShellProtocol.wrap(command, marker))
            w.flush()
            val lines = ArrayList<String>()
            while (true) {
                val line = r.readLine() ?: return@Callable null // shell berhenti
                lines.add(line)
                if (line.startsWith(marker)) break
            }
            RootShellProtocol.parse(lines, marker)
        }
        val future = ioPool.submit(task)
        return try {
            val parsed = future.get(timeoutMs, TimeUnit.MILLISECONDS)
            if (parsed == null) {
                close()
                Output(-1, "", timedOut = false, shellFailed = true)
            } else {
                Output(parsed.exitCode, parsed.output, timedOut = false, shellFailed = false)
            }
        } catch (e: TimeoutException) {
            // Perintah macet: tutup shell (membuka blokir pembacaan); perintah berikutnya membuka shell baru.
            future.cancel(true)
            close()
            Output(-1, "", timedOut = true, shellFailed = false)
        } catch (t: Throwable) {
            close()
            Output(-1, "", timedOut = false, shellFailed = true)
        }
    }

    fun close() {
        try { writer?.close() } catch (_: Throwable) {}
        try { process?.destroyForcibly() } catch (_: Throwable) {}
        writer = null
        reader = null
        process = null
    }

    private companion object {
        const val TAG = "RootShell"
    }
}

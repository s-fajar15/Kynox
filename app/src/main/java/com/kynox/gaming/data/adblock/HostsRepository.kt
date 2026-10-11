package com.kynox.gaming.data.adblock

import android.content.Context
import com.kynox.gaming.core.root.RootExecutor
import com.kynox.gaming.core.root.RootProvider
import com.kynox.gaming.data.logs.LogRepository
import com.kynox.gaming.domain.model.LogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val MODULE_DIR = "/data/adb/modules/kynox_hosts"
private const val MODULE_HOSTS = "$MODULE_DIR/system/etc/hosts"
private const val SYSTEM_HOSTS = "/system/etc/hosts"
private const val MAX_BYTES_PER_SOURCE = 25L * 1024 * 1024
private const val MAX_TOTAL_DOMAINS = 600_000

data class HostsSource(val url: String, val enabled: Boolean)

data class HostsState(
    val rootAvailable: Boolean,
    /** True bila root berasal dari Magisk, KernelSU, atau APatch (ketiganya memuat modul dari /data/adb/modules). */
    val supported: Boolean,
    val provider: RootProvider,
    val installed: Boolean,
    /** Domain di file hosts milik modul (hasil pembaruan terakhir); null bila belum dibaca. */
    val moduleBlocked: Int?,
    /** True bila /system/etc/hosts benar-benar sudah memuat file Kynox (modul termount). */
    val mounted: Boolean,
    val markedForRemoval: Boolean
) {
    val needsReboot: Boolean get() = installed && !mounted && !markedForRemoval
}

fun managerName(p: RootProvider): String = when (p) {
    RootProvider.MAGISK -> "Magisk"
    RootProvider.KERNELSU -> "KernelSU"
    RootProvider.APATCH -> "APatch"
    else -> "manajer root"
}

data class HostsOutcome(val success: Boolean, val message: String)

/**
 * Pemblokir iklan berbasis file hosts lewat modul milik Kynox untuk Magisk, KernelSU, atau APatch.
 *
 * Cara kerja: Kynox membuat modul Magisk kecil (/data/adb/modules/kynox_hosts) yang berisi
 * system/etc/hosts. Manajer modul memasang file itu di atas /system/etc/hosts saat boot (systemless;
 * partisi sistem tidak disentuh). Setelah modul termount, memperbarui isi file di tempat (tanpa
 * mengganti file) langsung berlaku tanpa reboot. Format modulnya sama di ketiganya (module.prop + system/etc/hosts, penanda `remove`).
 *
 * Yang diubah: file milik modul ini saja. Label SELinux hanya diberikan ke file hosts buatan
 * Kynox itu sendiri (kebijakan SELinux tidak diubah).
 */
class HostsRepository(
    private val context: Context,
    private val rootExecutor: RootExecutor,
    private val logRepository: LogRepository
) {
    private val dir: File get() = File(context.filesDir, "adblock").apply { mkdirs() }
    private val originalFile get() = File(dir, "hosts_original")
    private val buildFile get() = File(dir, "hosts_build")
    private val sourcesFile get() = File(dir, "sources.txt")
    private val whitelistFile get() = File(dir, "whitelist.txt")
    private val blacklistFile get() = File(dir, "blacklist.txt")

    // ---- Konfigurasi pengguna -------------------------------------------------

    fun defaultSources() = listOf(
        HostsSource("https://adaway.org/hosts.txt", true),
        HostsSource("https://raw.githubusercontent.com/r-a-y/mobile-hosts/master/AdguardDNS.txt", true)
    )

    suspend fun sources(): List<HostsSource> = withContext(Dispatchers.IO) {
        if (!sourcesFile.exists()) return@withContext defaultSources()
        sourcesFile.readLines().mapNotNull { l ->
            val enabled = !l.startsWith("disabled|")
            val url = l.removePrefix("disabled|").trim()
            if (isValidSourceUrl(url)) HostsSource(url, enabled) else null
        }
    }

    suspend fun saveSources(list: List<HostsSource>) = withContext(Dispatchers.IO) {
        sourcesFile.writeText(list.filter { isValidSourceUrl(it.url) }.joinToString("\n") { (if (it.enabled) "" else "disabled|") + it.url })
    }

    suspend fun whitelistText(): String = withContext(Dispatchers.IO) { if (whitelistFile.exists()) whitelistFile.readText() else "" }
    suspend fun blacklistText(): String = withContext(Dispatchers.IO) { if (blacklistFile.exists()) blacklistFile.readText() else "" }
    suspend fun saveLists(whitelist: String, blacklist: String) = withContext(Dispatchers.IO) {
        whitelistFile.writeText(whitelist.take(200_000)); blacklistFile.writeText(blacklist.take(200_000))
    }

    /** Hanya https, tanpa spasi atau karakter yang bisa merusak, dan nama host valid. */
    fun isValidSourceUrl(url: String): Boolean {
        if (url.length > 300 || !url.startsWith("https://")) return false
        if (url.any { it.isWhitespace() || it in "'\"`$;|&<>\\" }) return false
        val host = url.removePrefix("https://").substringBefore('/').substringBefore(':')
        return HostsParser.isValidHostname(host)
    }

    // ---- Status ----------------------------------------------------------------

    suspend fun state(): HostsState = withContext(Dispatchers.IO) {
        val status = rootExecutor.status()
        if (!status.isAvailable) return@withContext HostsState(false, false, RootProvider.NONE, false, null, false, false)
        val supported = status.provider == RootProvider.MAGISK || status.provider == RootProvider.KERNELSU || status.provider == RootProvider.APATCH
        val r = rootExecutor.execute(
            "[ -d $MODULE_DIR ] && echo I=1 || echo I=0 ; " +
                "[ -e $MODULE_DIR/remove ] && echo R=1 || echo R=0 ; " +
                "echo C=\$(grep -c '^0.0.0.0 ' $MODULE_HOSTS 2>/dev/null) ; " +
                "grep -q '$HOSTS_MARKER' $SYSTEM_HOSTS 2>/dev/null && echo M=1 || echo M=0"
        )
        var installed = false; var removal = false; var count: Int? = null; var mounted = false
        r.stdout.lines().forEach { l ->
            when {
                l.startsWith("I=") -> installed = l.endsWith("1")
                l.startsWith("R=") -> removal = l.endsWith("1")
                l.startsWith("C=") -> count = l.removePrefix("C=").trim().toIntOrNull()
                l.startsWith("M=") -> mounted = l.endsWith("1")
            }
        }
        HostsState(true, supported, status.provider, installed, if (installed) count else null, mounted, removal)
    }

    private suspend fun log(action: String, detail: String?, ok: Boolean, error: String? = null) {
        runCatching { logRepository.append(LogEntry(System.currentTimeMillis(), action, "hosts", null, detail, if (ok) "SUCCESS" else "FAILED", error)) }
    }

    // ---- Operasi ---------------------------------------------------------------

    /** Menulis [source] ke file hosts modul DI TEMPAT (inode sama, agar mount yang sudah ada ikut berubah). */
    private suspend fun writeModuleHosts(source: File): Boolean {
        val p = source.absolutePath
        rootExecutor.execute(
            "cat '$p' > $MODULE_HOSTS && chmod 644 $MODULE_HOSTS && chown 0:0 $MODULE_HOSTS && chcon u:object_r:system_file:s0 $MODULE_HOSTS",
            timeoutMs = 20_000L
        )
        val check = rootExecutor.execute("cmp -s '$p' $MODULE_HOSTS && echo SAME", timeoutMs = 20_000L)
        return check.stdout.contains("SAME")
    }

    suspend fun install(): HostsOutcome = withContext(Dispatchers.IO) {
        val st = state()
        if (!st.rootAvailable) return@withContext HostsOutcome(false, "Butuh akses root.")
        if (!st.supported) return@withContext HostsOutcome(false, "Butuh root Magisk, KernelSU, atau APatch.")
        if (st.installed && !st.markedForRemoval) return@withContext HostsOutcome(true, "Modul sudah terpasang.")

        // Simpan hosts asli sistem (sekali), tanpa bagian Kynox bila ada.
        if (!originalFile.exists()) {
            val orig = rootExecutor.execute("cat $SYSTEM_HOSTS 2>/dev/null").stdout
            originalFile.writeText(HostsParser.cleanBase(orig))
        }
        val prop = "id=kynox_hosts\\nname=Kynox Hosts\\nversion=v1\\nversionCode=1\\nauthor=Kynox\\ndescription=Hosts pemblokir iklan yang dikelola aplikasi Kynox"
        rootExecutor.execute("rm -f $MODULE_DIR/remove $MODULE_DIR/disable ; mkdir -p $MODULE_DIR/system/etc && printf '$prop\\n' > $MODULE_DIR/module.prop && chmod 755 $MODULE_DIR $MODULE_DIR/system $MODULE_DIR/system/etc && chmod 644 $MODULE_DIR/module.prop")
        val ok = writeModuleHosts(originalFile)
        log("HOSTS_INSTALL", MODULE_DIR, ok, if (ok) null else "file hosts modul tidak terverifikasi")
        if (ok) HostsOutcome(true, "Modul terpasang. Reboot HP sekali agar ${managerName(st.provider)} memasangnya di /system/etc/hosts, lalu jalankan Perbarui daftar.")
        else HostsOutcome(false, "Gagal menulis file hosts modul.")
    }

    suspend fun update(onProgress: (String) -> Unit): HostsOutcome = withContext(Dispatchers.IO) {
        val st = state()
        if (!st.installed || st.markedForRemoval) return@withContext HostsOutcome(false, "Pasang modul dulu.")
        val enabled = sources().filter { it.enabled }
        if (enabled.isEmpty()) return@withContext HostsOutcome(false, "Tidak ada sumber yang aktif.")

        val blocked = LinkedHashSet<String>()
        val failures = mutableListOf<String>()
        enabled.forEach { s ->
            onProgress("Mengunduh ${s.url.removePrefix("https://").substringBefore('/')}…")
            val (text, why) = download(s.url)
            if (text == null) failures += s.url.removePrefix("https://").substringBefore('/') + " (" + (why ?: "tidak diketahui") + ")"
            else HostsParser.parseInto(text, blocked, MAX_TOTAL_DOMAINS)
        }
        if (blocked.isEmpty()) {
            return@withContext HostsOutcome(false, "Tidak ada domain yang berhasil dibaca (" + (if (failures.isEmpty()) "daftar kosong" else "gagal: " + failures.joinToString()) + "). File hosts tidak diubah.")
        }
        onProgress("Menyusun file hosts…")
        val base = if (originalFile.exists()) originalFile.readText() else ""
        val build = HostsParser.build(
            base, blocked,
            HostsParser.parseDomainList(whitelistText()), HostsParser.parseDomainList(blacklistText())
        )
        buildFile.writeText(build.text)
        onProgress("Menulis ke modul…")
        val ok = writeModuleHosts(buildFile)
        buildFile.delete()
        val after = state()
        log("HOSTS_UPDATE", "diblokir=${build.blockedCount}, sumber gagal=${failures.size}", ok, if (ok) null else "tidak terverifikasi")
        if (!ok) return@withContext HostsOutcome(false, "Gagal menulis file hosts modul. File lama dipertahankan sebisanya.")
        val tail = when {
            !after.mounted -> " Modul belum termount: reboot sekali agar berlaku."
            else -> " Sudah berlaku tanpa reboot."
        }
        val warn = if (failures.isNotEmpty()) " Sumber gagal diunduh: ${failures.joinToString()}." else ""
        HostsOutcome(true, "${build.blockedCount} domain diblokir.$tail$warn")
    }

    /** Mengembalikan isi hosts asli di tempat (langsung berlaku), modul tetap terpasang. */
    suspend fun disable(): HostsOutcome = withContext(Dispatchers.IO) {
        val st = state()
        if (!st.installed) return@withContext HostsOutcome(false, "Modul belum terpasang.")
        if (!originalFile.exists()) originalFile.writeText(HostsParser.cleanBase(""))
        val ok = writeModuleHosts(originalFile)
        log("HOSTS_DISABLE", null, ok, if (ok) null else "tidak terverifikasi")
        if (ok) HostsOutcome(true, "Pemblokiran dimatikan: isi hosts dikembalikan ke aslinya. Modul masih terpasang.")
        else HostsOutcome(false, "Gagal mengembalikan isi hosts.")
    }

    /** Mengosongkan blokir lalu menandai modul untuk dihapus pada boot berikutnya. */
    suspend fun remove(): HostsOutcome = withContext(Dispatchers.IO) {
        val st = state()
        if (!st.installed) return@withContext HostsOutcome(false, "Modul belum terpasang.")
        if (!originalFile.exists()) originalFile.writeText(HostsParser.cleanBase(""))
        writeModuleHosts(originalFile)
        rootExecutor.execute("touch $MODULE_DIR/remove")
        val flagged = state().markedForRemoval
        log("HOSTS_REMOVE", null, flagged, if (flagged) null else "penanda hapus tidak terverifikasi")
        if (flagged) HostsOutcome(true, "Modul ditandai untuk dihapus. Reboot sekali agar ${managerName(st.provider)} menghapusnya. Pemblokiran sudah dimatikan.")
        else HostsOutcome(false, "Gagal menandai modul untuk dihapus.")
    }

    // ---- Unduhan -----------------------------------------------------------------

    /** Mengembalikan (isi, null) bila berhasil atau (null, alasan singkat) bila gagal. */
    private fun download(url: String): Pair<String?, String?> {
        if (!isValidSourceUrl(url)) return null to "alamat tidak valid"
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000; readTimeout = 30_000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Kynox")
            }
            val code = conn.responseCode
            if (code != 200) return null to "HTTP $code"
            if (conn.url.protocol != "https") return null to "dialihkan ke alamat non-HTTPS"
            val out = ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buf = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_BYTES_PER_SOURCE) return null to "file terlalu besar"
                    out.write(buf, 0, n)
                }
            }
            out.toString(Charsets.UTF_8.name()) to null
        } catch (t: Throwable) {
            null to (t.javaClass.simpleName + (t.message?.let { ": " + it.take(80) } ?: ""))
        } finally {
            conn?.disconnect()
        }
    }
}

package com.kynox.gaming.data.adblock

/** Penanda yang ditulis Kynox di file hosts buatannya (dipakai untuk mendeteksi apakah sudah termount). */
const val HOSTS_MARKER = "# kynox-hosts v1"

data class HostsBuild(val text: String, val blockedCount: Int)

/** Logika murni untuk membaca dan menyusun file hosts, agar bisa diuji tanpa Android. */
object HostsParser {
    private val LOCAL_NAMES = setOf(
        "localhost", "localhost.localdomain", "local", "broadcasthost", "ip6-localhost", "ip6-loopback",
        "ip6-localnet", "ip6-mcastprefix", "ip6-allnodes", "ip6-allrouters", "ip6-allhosts", "0.0.0.0"
    )
    private val BLOCK_IPS = setOf("0.0.0.0", "127.0.0.1", "::", "::1")

    private val LABEL = Regex("^[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?$")

    /**
     * Nama host yang aman ditulis ke file hosts dan perintah shell: huruf/angka/tanda hubung,
     * minimal dua label, tiap label 1-63 karakter, total maksimal 253. Alamat IP ditolak.
     */
    fun isValidHostname(host: String): Boolean {
        if (host.isEmpty() || host.length > 253) return false
        val labels = host.split('.')
        if (labels.size < 2) return false
        if (labels.all { l -> l.isNotEmpty() && l.all { it.isDigit() } }) return false
        return labels.all { LABEL.matches(it) }
    }

    /**
     * Satu baris format hosts ("0.0.0.0 domain" atau "127.0.0.1 domain"; komentar # diabaikan)
     * menjadi nama domain huruf kecil, atau null bila bukan entri pemblokiran yang valid.
     * Hanya entri dengan alamat nol/loopback yang diterima, supaya daftar tidak bisa mengalihkan
     * domain ke alamat lain.
     */
    fun parseDomain(rawLine: String): String? {
        val line = rawLine.substringBefore('#').trim()
        if (line.isEmpty()) return null
        val parts = line.split(Regex("\\s+"))
        if (parts.size < 2) return null
        if (parts[0] !in BLOCK_IPS) return null
        val domain = parts[1].lowercase().trimEnd('.')
        if (domain in LOCAL_NAMES) return null
        return if (isValidHostname(domain)) domain else null
    }

    /** Daftar domain polos (satu per baris) untuk daftar putih/hitam buatan pengguna. */
    fun parseDomainList(text: String): Set<String> =
        text.lineSequence()
            .map { it.substringBefore('#').trim().lowercase().trimEnd('.') }
            .filter { it.isNotEmpty() && isValidHostname(it) }
            .toCollection(LinkedHashSet())

    /** Menambahkan domain dari teks hosts ke [sink]; berhenti di [limit]. Mengembalikan jumlah baris valid yang dibaca. */
    fun parseInto(text: String, sink: MutableSet<String>, limit: Int): Int {
        var n = 0
        for (line in text.lineSequence()) {
            if (sink.size >= limit) break
            val d = parseDomain(line) ?: continue
            if (sink.add(d)) n++
        }
        return n
    }

    /** Isi hosts asli tanpa bagian buatan Kynox sebelumnya, dengan baris localhost dijamin ada. */
    fun cleanBase(original: String): String {
        val kept = original.lineSequence().takeWhile { !it.startsWith(HOSTS_MARKER) }.toList().joinToString("\n").trimEnd()
        val hasLocal = kept.lineSequence().any { l -> l.substringBefore('#').trim().split(Regex("\\s+")).drop(1).contains("localhost") }
        return if (hasLocal) kept + "\n" else "127.0.0.1 localhost\n::1 localhost\n" + (if (kept.isBlank()) "" else kept + "\n")
    }

    /** Menyusun file hosts: dasar + (diblokir - daftar putih) + daftar hitam. */
    fun build(base: String, blocked: Set<String>, whitelist: Set<String>, blacklist: Set<String>): HostsBuild {
        val finalSet = LinkedHashSet<String>()
        blocked.forEach { if (it !in whitelist) finalSet.add(it) }
        blacklist.forEach { if (it !in whitelist) finalSet.add(it) }
        val sb = StringBuilder(cleanBase(base))
        sb.append(HOSTS_MARKER).append(" | blocked=").append(finalSet.size).append('\n')
        finalSet.forEach { sb.append("0.0.0.0 ").append(it).append('\n') }
        return HostsBuild(sb.toString(), finalSet.size)
    }
}

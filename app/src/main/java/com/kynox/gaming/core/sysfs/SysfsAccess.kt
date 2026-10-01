package com.kynox.gaming.core.sysfs

import com.kynox.gaming.core.root.RootExecutor
import java.io.File

/**
 * Low level probing used by the capability engine. Every check here is a
 * real filesystem/root probe -- nothing is assumed or hardcoded as
 * "supported" without first being verified on the running device.
 */
object SysfsAccess {

    fun existsDirect(path: String): Boolean = File(path).exists()

    fun readDirect(path: String): String? = try {
        val f = File(path)
        if (f.exists() && f.canRead()) f.readText().trim() else null
    } catch (t: Throwable) {
        null
    }

    fun listChildren(dirPath: String): List<String> = try {
        File(dirPath).listFiles()?.map { it.name }?.sorted() ?: emptyList()
    } catch (t: Throwable) {
        emptyList()
    }

    suspend fun probeNode(path: String, rootAvailable: Boolean, rootExecutor: RootExecutor): SysfsNode {
        val file = File(path)
        if (file.exists()) {
            val directValue = readDirect(path)
            if (directValue != null) {
                val writable = if (rootAvailable) true else file.canWrite()
                return SysfsNode(path, exists = true, readable = true, writable = writable, value = directValue)
            }
        }
        if (!rootAvailable) {
            return SysfsNode(path, exists = file.exists(), readable = false, writable = false)
        }
        val result = rootExecutor.readFile(path)
        return if (result.isSuccess && result.stdout.isNotBlank()) {
            SysfsNode(path, exists = true, readable = true, writable = true, value = result.stdout.trim())
        } else {
            SysfsNode(path, exists = false, readable = false, writable = false)
        }
    }

    /**
     * Some kernels (this project has seen it on MIUI) restrict read access to
     * certain sysfs directories/nodes to root, so a plain java.io.File check
     * reports "doesn't exist" even though it does. These variants retry
     * through root before giving up, and are used anywhere a wrong "not
     * supported" would hide a feature that is actually available.
     */
    suspend fun existsWithRoot(path: String, rootAvailable: Boolean, rootExecutor: RootExecutor): Boolean {
        if (File(path).exists()) return true
        if (!rootAvailable) return false
        val result = rootExecutor.execute("[ -e '$path' ] && echo yes")
        return result.isSuccess && result.stdout.contains("yes")
    }

    suspend fun readWithRoot(path: String, rootAvailable: Boolean, rootExecutor: RootExecutor): String? {
        readDirect(path)?.let { return it }
        if (!rootAvailable) return null
        val result = rootExecutor.readFile(path)
        return if (result.isSuccess && result.stdout.isNotBlank()) result.stdout.trim() else null
    }

    suspend fun listChildrenWithRoot(dirPath: String, rootAvailable: Boolean, rootExecutor: RootExecutor): List<String> {
        val direct = listChildren(dirPath)
        if (direct.isNotEmpty()) return direct
        if (!rootAvailable) return direct
        val result = rootExecutor.execute("ls -1 '$dirPath' 2>/dev/null")
        if (!result.isSuccess || result.stdout.isBlank()) return direct
        return result.stdout.lines().map { it.trim() }.filter { it.isNotEmpty() }.sorted()
    }

    /**
     * Reads several sysfs files in a single root invocation instead of one
     * `su -c` process per file. Spawning `su` repeatedly every poll cycle
     * (once per file, several files per screen) is real, measurable overhead
     * -- this is the fix for that when several related values are read
     * together (e.g. every GPU devfreq node at once).
     */
    suspend fun readMultipleWithRoot(paths: List<String>, rootAvailable: Boolean, rootExecutor: RootExecutor): Map<String, String?> {
        if (paths.isEmpty()) return emptyMap()
        val direct = paths.associateWith { readDirect(it) }
        if (direct.values.all { it != null } || !rootAvailable) return direct

        val marker = "@@KYNOX_SYSFS@@"
        val missing = paths.filter { direct[it] == null }
        val command = missing.joinToString(" ; ") { path -> "echo '$marker$path' ; cat '$path' 2>/dev/null" }
        val result = rootExecutor.execute(command, timeoutMs = 5000L)
        val rootValues = mutableMapOf<String, String?>()
        if (result.isSuccess) {
            result.stdout.split(marker).drop(1).forEach { part ->
                val newline = part.indexOf('\n')
                val path = if (newline == -1) part.trim() else part.substring(0, newline).trim()
                val value = if (newline == -1) null else part.substring(newline + 1).trim().ifBlank { null }
                if (path.isNotEmpty()) rootValues[path] = value
            }
        }
        return paths.associateWith { direct[it] ?: rootValues[it] }
    }
}

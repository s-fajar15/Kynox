package com.kynox.gaming.core.sysfs

import com.kynox.gaming.core.root.RootCommandResult
import com.kynox.gaming.core.root.RootExecutor
import com.kynox.gaming.core.root.RootProvider
import com.kynox.gaming.core.root.RootStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Shell tiruan: mengikuti aturan nyata bahwa kode keluar perintah gabungan = kode keluar perintah TERAKHIR. */
private class FakeShellExecutor(private val files: Map<String, String>) : RootExecutor {
    val commands = mutableListOf<String>()
    override suspend fun status() = RootStatus(true, RootProvider.UNKNOWN, null, "fake")
    override suspend fun refreshStatus() = status()
    override suspend fun readFile(path: String) = execute("cat '$path'")
    override suspend fun writeFile(path: String, value: String, timeoutMs: Long) = execute("echo")
    override suspend fun execute(command: String, timeoutMs: Long): RootCommandResult {
        commands += command
        val out = StringBuilder()
        var exit = 0
        command.split(" ; ").forEach { part ->
            val p = part.trim()
            when {
                p.startsWith("echo '") -> { out.append(p.removePrefix("echo '").substringBefore("'")).append('\n'); exit = 0 }
                p.startsWith("cat '") -> {
                    val path = p.removePrefix("cat '").substringBefore("'")
                    val v = files[path]
                    if (v != null) { out.append(v).append('\n'); exit = 0 } else exit = 1
                }
                p == "true" -> exit = 0
            }
        }
        return RootCommandResult(command, exit, out.toString(), "", false, 1)
    }
}

class SysfsAccessTest {
    @Test fun lastMissingPathDoesNotDiscardOtherValues() = runBlocking {
        val exec = FakeShellExecutor(mapOf("/nonexistent/kynox/a" to "1", "/nonexistent/kynox/b" to "0"))
        val values = SysfsAccess.readMultipleWithRoot(
            listOf("/nonexistent/kynox/a", "/nonexistent/kynox/b", "/nonexistent/kynox/missing"), true, exec
        )
        assertEquals("1", values["/nonexistent/kynox/a"])
        assertEquals("0", values["/nonexistent/kynox/b"])
        assertNull(values["/nonexistent/kynox/missing"])
    }

    @Test fun manyPathsAreSplitIntoSmallBatches() = runBlocking {
        val paths = (1..40).map { "/nonexistent/kynox/n$it" }
        val exec = FakeShellExecutor(paths.associateWith { "v" })
        val values = SysfsAccess.readMultipleWithRoot(paths, true, exec)
        assertEquals(40, values.values.count { it == "v" })
        assertEquals(3, exec.commands.size)
    }
}

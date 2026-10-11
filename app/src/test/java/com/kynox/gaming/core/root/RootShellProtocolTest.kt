package com.kynox.gaming.core.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RootShellProtocolTest {

    @Test fun quoteEscapesSingleQuotes() {
        assertEquals("'echo hi'", RootShellProtocol.quote("echo hi"))
        assertEquals("'echo '\\''x'\\'''", RootShellProtocol.quote("echo 'x'"))
    }

    @Test fun unbalancedQuoteStaysInsideTheWrapper() {
        val wrapped = RootShellProtocol.wrap("echo \"oops", "M")
        // Tanda kutip ganda yang tak berpasangan berada di dalam kutip tunggal, jadi penanda tetap terbaca shell.
        assertTrue(wrapped.startsWith("sh -c 'echo \"oops' </dev/null 2>&1;"))
        assertTrue(wrapped.endsWith("\n"))
        assertTrue(wrapped.contains("'M'"))
    }

    @Test fun wrapReadsNothingFromStdin() {
        assertTrue(RootShellProtocol.wrap("cat", "M").contains("</dev/null"))
    }

    @Test fun parseSplitsOutputAndExitCode() {
        val parsed = RootShellProtocol.parse(listOf("line1", "line2", "", "MARK0"), "MARK")
        assertEquals("line1\nline2", parsed!!.output)
        assertEquals(0, parsed.exitCode)
    }

    @Test fun parseKeepsNonZeroExitCode() {
        assertEquals(127, RootShellProtocol.parse(listOf("not found", "", "MARK127"), "MARK")!!.exitCode)
    }

    @Test fun parseWithoutMarkerIsNull() {
        assertNull(RootShellProtocol.parse(listOf("a", "b"), "MARK"))
    }

    @Test fun parseEmptyOutput() {
        assertEquals("", RootShellProtocol.parse(listOf("", "MARK0"), "MARK")!!.output)
    }
}

package com.kynox.gaming.data

import com.kynox.gaming.data.adblock.HOSTS_MARKER
import com.kynox.gaming.data.adblock.HostsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostsParserTest {
    @Test fun parsesBlockLinesOnly() {
        assertEquals("ads.example.com", HostsParser.parseDomain("0.0.0.0 ads.example.com"))
        assertEquals("ads.example.com", HostsParser.parseDomain("127.0.0.1\tADS.Example.com  # komentar"))
        assertNull(HostsParser.parseDomain("# 0.0.0.0 ads.example.com"))
        assertNull(HostsParser.parseDomain(""))
        assertNull(HostsParser.parseDomain("0.0.0.0 localhost"))
        assertNull(HostsParser.parseDomain("0.0.0.0 0.0.0.0"))
        assertNull(HostsParser.parseDomain("127.0.0.1 localhost.localdomain"))
        assertNull(HostsParser.parseDomain("0.0.0.0"))
    }

    @Test fun rejectsRedirectsToOtherAddressesAndUnsafeNames() {
        assertNull(HostsParser.parseDomain("93.184.216.34 ads.example.com"))
        assertNull(HostsParser.parseDomain("0.0.0.0 bad_name.example.com"))
        assertNull(HostsParser.parseDomain("0.0.0.0 a.com; reboot"))
        assertNull(HostsParser.parseDomain("0.0.0.0 evil.com\$(id)"))
    }

    @Test fun parseIntoDeduplicatesAndRespectsLimit() {
        val sink = LinkedHashSet<String>()
        val n = HostsParser.parseInto("0.0.0.0 a.com\n0.0.0.0 a.com\n0.0.0.0 b.com\n0.0.0.0 c.com\n", sink, 2)
        assertEquals(setOf("a.com", "b.com"), sink)
        assertEquals(2, n)
    }

    @Test fun domainListIgnoresCommentsAndInvalid() {
        val set = HostsParser.parseDomainList("# catatan\nExample.com\n\nbad name\nsub.test.org # x\n")
        assertEquals(setOf("example.com", "sub.test.org"), set)
    }

    @Test fun buildAppliesWhitelistAndBlacklistAndMarker() {
        val b = HostsParser.build(
            "127.0.0.1 localhost\n::1 ip6-localhost\n",
            blocked = linkedSetOf("a.com", "b.com", "c.com"),
            whitelist = setOf("b.com", "z.com"),
            blacklist = setOf("d.com", "z.com")
        )
        assertEquals(3, b.blockedCount)
        val lines = b.text.lines()
        assertTrue(lines.contains("0.0.0.0 a.com"))
        assertTrue(lines.contains("0.0.0.0 c.com"))
        assertTrue(lines.contains("0.0.0.0 d.com"))
        assertFalse(lines.contains("0.0.0.0 b.com"))
        assertFalse(lines.contains("0.0.0.0 z.com"))
        assertTrue(b.text.contains(HOSTS_MARKER))
        assertTrue(lines.contains("127.0.0.1 localhost"))
    }

    @Test fun cleanBaseStripsPreviousKynoxSectionAndKeepsLocalhost() {
        val prev = "127.0.0.1 localhost\n::1 ip6-localhost\n$HOSTS_MARKER | blocked=2\n0.0.0.0 a.com\n0.0.0.0 b.com\n"
        val base = HostsParser.cleanBase(prev)
        assertFalse(base.contains("a.com"))
        assertTrue(base.contains("127.0.0.1 localhost"))
        // Tanpa localhost sama sekali: ditambahkan.
        val empty = HostsParser.cleanBase("")
        assertTrue(empty.contains("127.0.0.1 localhost"))
        assertTrue(empty.contains("::1 localhost"))
    }

    @Test fun rebuildDoesNotAccumulateOldEntries() {
        val first = HostsParser.build("127.0.0.1 localhost\n", linkedSetOf("a.com"), emptySet(), emptySet())
        val second = HostsParser.build(first.text, linkedSetOf("b.com"), emptySet(), emptySet())
        assertFalse(second.text.contains("a.com"))
        assertTrue(second.text.contains("b.com"))
        assertEquals(1, second.blockedCount)
    }

    @Test fun hostnameValidationRejectsUnsafeAndNonHostnames() {
        assertTrue(HostsParser.isValidHostname("ads.example.com"))
        assertTrue(HostsParser.isValidHostname("a-b.example.co.id"))
        assertFalse(HostsParser.isValidHostname(""))
        assertFalse(HostsParser.isValidHostname("localhost"))
        assertFalse(HostsParser.isValidHostname("1.1.1.1"))
        assertFalse(HostsParser.isValidHostname("ads.example.com; reboot"))
        assertFalse(HostsParser.isValidHostname("-bad.example.com"))
        assertFalse(HostsParser.isValidHostname("a..b.com"))
        assertFalse(HostsParser.isValidHostname("a".repeat(64) + ".com"))
        assertFalse(HostsParser.isValidHostname("ads.example.com\$(id)"))
    }
}

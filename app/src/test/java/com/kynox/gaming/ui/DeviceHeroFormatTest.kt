package com.kynox.gaming.ui

import com.kynox.gaming.ui.components.kernelShort
import com.kynox.gaming.ui.components.ramLabel
import com.kynox.gaming.ui.components.storageLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceHeroFormatTest {
    private val gib = 1024L * 1024L * 1024L

    @Test fun ramRoundsUpToSaleSize() {
        assertEquals("6 GB RAM", ramLabel((5.3 * gib).toLong()))
        assertEquals("4 GB RAM", ramLabel((3.7 * gib).toLong()))
        assertEquals("8 GB RAM", ramLabel((7.4 * gib).toLong()))
        assertEquals("8 GB RAM", ramLabel(8 * gib))
        assertNull(ramLabel(0L))
    }

    @Test fun storageRoundsUpToSaleSize() {
        assertEquals("128 GB", storageLabel(112 * gib))
        assertEquals("256 GB", storageLabel(230 * gib))
        assertEquals("1 TB", storageLabel(931 * gib))
        assertNull(storageLabel(0L))
    }

    @Test fun kernelShortExtractsVersion() {
        assertEquals("4.14.356-gabc", kernelShort("Linux version 4.14.356-gabc (user@host) (clang 17) #1 SMP PREEMPT"))
        assertEquals("--", kernelShort(null))
        assertEquals("--", kernelShort("  "))
        assertEquals("tidak dikenal", kernelShort("tidak dikenal"))
    }
}

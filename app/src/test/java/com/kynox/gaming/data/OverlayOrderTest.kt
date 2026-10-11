package com.kynox.gaming.data

import com.kynox.gaming.data.settings.PortableSettings
import com.kynox.gaming.domain.model.DEFAULT_OVERLAY_ORDER
import com.kynox.gaming.domain.model.OverlayMetric
import com.kynox.gaming.domain.model.defaultOverlayOrder
import com.kynox.gaming.domain.model.moveInOrder
import com.kynox.gaming.domain.model.parseOverlayOrder
import com.kynox.gaming.domain.model.serializeOverlayOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayOrderTest {

    @Test fun defaultStringMatchesDefaultList() {
        assertEquals(DEFAULT_OVERLAY_ORDER, serializeOverlayOrder(defaultOverlayOrder))
    }

    @Test fun nullOrEmptyGivesDefault() {
        assertEquals(defaultOverlayOrder, parseOverlayOrder(null))
        assertEquals(defaultOverlayOrder, parseOverlayOrder(""))
    }

    @Test fun unknownAndDuplicateDroppedMissingAppended() {
        val parsed = parseOverlayOrder("RAM,BOGUS,RAM,FPS")
        assertEquals(listOf(OverlayMetric.RAM, OverlayMetric.FPS), parsed.take(2))
        assertEquals(OverlayMetric.values().size, parsed.size)
        assertEquals(parsed.size, parsed.toSet().size)
    }

    @Test fun roundTrip() {
        val order = listOf(OverlayMetric.POWER, OverlayMetric.FPS) + defaultOverlayOrder.filter { it != OverlayMetric.POWER && it != OverlayMetric.FPS }
        assertEquals(order, parseOverlayOrder(serializeOverlayOrder(order)))
    }

    @Test fun moveUpAndDown() {
        val moved = moveInOrder(defaultOverlayOrder, OverlayMetric.GPU, -1)
        assertEquals(listOf(OverlayMetric.FPS, OverlayMetric.GPU, OverlayMetric.CPU), moved.take(3))
        assertEquals(defaultOverlayOrder, moveInOrder(moved, OverlayMetric.GPU, 1))
    }

    @Test fun moveAtEdgesIsNoOp() {
        assertEquals(defaultOverlayOrder, moveInOrder(defaultOverlayOrder, OverlayMetric.FPS, -1))
        assertEquals(defaultOverlayOrder, moveInOrder(defaultOverlayOrder, OverlayMetric.BATTERY_TEMP, 1))
    }

    @Test fun sanitizeNormalizesOrderAndKeepsNewBooleans() {
        val out = PortableSettings.sanitize(mapOf("overlay_order" to "RAM,XX", "overlay_show_ram" to true, "overlay_show_power" to "ya"))
        assertEquals(DEFAULT_OVERLAY_ORDER.let { serializeOverlayOrder(parseOverlayOrder("RAM")) }, out["overlay_order"])
        assertEquals(true, out["overlay_show_ram"])
        assertEquals(false, out.containsKey("overlay_show_power"))
    }
}

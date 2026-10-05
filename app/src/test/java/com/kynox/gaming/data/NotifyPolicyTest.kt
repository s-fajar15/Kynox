package com.kynox.gaming.data

import com.kynox.gaming.data.notify.NotifyThrottle
import com.kynox.gaming.data.notify.ThermalWarner
import com.kynox.gaming.data.notify.thermalWarnThreshold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotifyPolicyTest {
    private var now = 1_000_000L
    private val throttle = NotifyThrottle { now }
    private val minute = 60_000L

    @Test fun throttleBlocksUntilCooldownPasses() {
        assertTrue(throttle.tryAcquire("k", 5 * minute))
        now += 4 * minute
        assertFalse(throttle.tryAcquire("k", 5 * minute))
        now += 1 * minute
        assertTrue(throttle.tryAcquire("k", 5 * minute))
    }

    @Test fun keysAreIndependent() {
        assertTrue(throttle.tryAcquire("a", minute))
        assertTrue(throttle.tryAcquire("b", minute))
    }

    @Test fun thresholdFollowsSettingThenTripThenFallback() {
        assertEquals(50f, thermalWarnThreshold(50, 60f), 0f)
        assertEquals(55f, thermalWarnThreshold(0, 60f), 0f)
        assertEquals(47f, thermalWarnThreshold(0, null), 0f)
    }

    @Test fun warnerFiresOnceUntilZoneCoolsDown() {
        val warner = ThermalWarner(throttle)
        assertFalse(warner.shouldWarn("cpu", 40f, 47f, minute))
        assertTrue(warner.shouldWarn("cpu", 48f, 47f, minute))
        now += 10 * minute
        assertFalse("still hot, already warned", warner.shouldWarn("cpu", 49f, 47f, minute))
        assertFalse(warner.shouldWarn("cpu", 40f, 47f, minute))
        now += 10 * minute
        assertTrue("cooled below threshold-5 then hot again", warner.shouldWarn("cpu", 48f, 47f, minute))
    }

    @Test fun cooldownIsSharedAcrossSourcesAndDoesNotConsumeTheWarning() {
        val warner = ThermalWarner(throttle)
        assertTrue(warner.shouldWarn("cpu", 50f, 47f, 5 * minute))
        now += minute
        assertFalse("battery blocked by shared cooldown", warner.shouldWarn("battery", 50f, 47f, 5 * minute))
        now += 5 * minute
        assertTrue("battery still allowed once cooldown ends", warner.shouldWarn("battery", 50f, 47f, 5 * minute))
    }
}

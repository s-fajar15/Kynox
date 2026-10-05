package com.kynox.gaming.service

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayPositionTest {

    @Test fun positionInsideScreenIsKept() {
        assertEquals(120, OverlayPosition.clamp(120, 300, 2000))
    }

    @Test fun portraitCoordinateOffTheLandscapeScreenIsPulledBackIn() {
        // y = 1500 disimpan di potret; layar landscape hanya setinggi 900 dan overlay setinggi 120.
        assertEquals(780, OverlayPosition.clamp(1500, 120, 900))
    }

    @Test fun negativeValuesBecomeZero() {
        assertEquals(0, OverlayPosition.clamp(-40, 300, 2000))
    }

    @Test fun viewLargerThanScreenStaysAtOrigin() {
        assertEquals(0, OverlayPosition.clamp(50, 3000, 2000))
    }

    @Test fun orientationsUseSeparateKeys() {
        assertEquals("x", OverlayPosition.keyX(false))
        assertEquals("x_land", OverlayPosition.keyX(true))
        assertEquals("y", OverlayPosition.keyY(false))
        assertEquals("y_land", OverlayPosition.keyY(true))
    }
}

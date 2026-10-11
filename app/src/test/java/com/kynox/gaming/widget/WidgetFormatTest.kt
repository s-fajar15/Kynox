package com.kynox.gaming.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetFormatTest {
    @Test fun percentHandlesMissing() {
        assertEquals("--", WidgetFormat.percent(null))
        assertEquals("--", WidgetFormat.percent(-1))
        assertEquals("87%", WidgetFormat.percent(87))
    }

    @Test fun batteryPercentFromLevelAndScale() {
        assertEquals(50, WidgetFormat.batteryPercent(50, 100))
        assertEquals(100, WidgetFormat.batteryPercent(255, 255))
        assertNull(WidgetFormat.batteryPercent(-1, 100))
        assertNull(WidgetFormat.batteryPercent(10, 0))
    }

    @Test fun ramPercentIsUsedShare() {
        assertEquals(75, WidgetFormat.ramPercent(250, 1000))
        assertNull(WidgetFormat.ramPercent(0, 0))
    }

    @Test fun temperatureCelsiusAndFahrenheit() {
        assertEquals("--", WidgetFormat.temperature(null, false))
        assertEquals("--", WidgetFormat.temperature(Int.MIN_VALUE, false))
        assertEquals("36.5°C".replace('.', java.text.DecimalFormatSymbols.getInstance().decimalSeparator), WidgetFormat.temperature(365, false))
        assertEquals("104°F", WidgetFormat.temperature(400, true))
    }

    @Test fun chargeStatusLabels() {
        assertEquals("MENGISI", WidgetFormat.chargeStatus(2, true))
        assertEquals("PENUH", WidgetFormat.chargeStatus(5, true))
        assertEquals("TERPAKAI", WidgetFormat.chargeStatus(3, false))
        assertEquals("TIDAK MENGISI", WidgetFormat.chargeStatus(3, true))
        assertEquals("TIDAK MENGISI", WidgetFormat.chargeStatus(4, true))
        assertEquals("TERPAKAI", WidgetFormat.chargeStatus(-1, false))
        assertEquals("TERHUBUNG", WidgetFormat.chargeStatus(-1, true))
    }

    @Test fun currentIsAbsoluteAndUnitNormalised() {
        assertEquals(1419, WidgetFormat.currentMilliAmps(-1419432L))
        assertEquals(808, WidgetFormat.currentMilliAmps(808L))
        assertEquals(808, WidgetFormat.currentMilliAmps(-808L))
        assertNull(WidgetFormat.currentMilliAmps(0L))
        assertNull(WidgetFormat.currentMilliAmps(null))
        assertNull(WidgetFormat.currentMilliAmps(Long.MIN_VALUE))
    }

    @Test fun powerAndVoltageText() {
        assertEquals("--", WidgetFormat.milliAmps(null))
        assertEquals("808 mA", WidgetFormat.milliAmps(808))
        assertEquals("--", WidgetFormat.voltage(0))
        assertEquals("--", WidgetFormat.watts(null, 800))
        val sep = java.text.DecimalFormatSymbols.getInstance().decimalSeparator
        assertEquals("3${sep}69 V", WidgetFormat.voltage(3690))
        assertEquals("3${sep}0 W", WidgetFormat.watts(3750, 800))
    }

    @Test fun chargeStatusLabelUsesSentenceCase() {
        assertEquals("Mengisi", WidgetFormat.chargeStatusLabel(2, true))
        assertEquals("Tidak mengisi", WidgetFormat.chargeStatusLabel(4, true))
        assertEquals("Terpakai", WidgetFormat.chargeStatusLabel(3, false))
    }
}

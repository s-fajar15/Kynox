package com.kynox.gaming.data

import com.kynox.gaming.data.gaming.FpsReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FpsReaderParseTest {

    private fun layer(name: String, total: Int, dropped: Int = 0) = """
        layerName = $name
        totalFrames = $total
        droppedFrames = $dropped
        averageFPS = 59.8
        displayRefreshRate = 60.00 fps
        renderRate = 60.00 fps
    """.trimIndent()

    @Test
    fun fpsIsFramesDividedByWindow() {
        val reading = FpsReader.parse(layer("com.game/com.game.Main#0", 600, 12), "com.game", 10.0, false)
        assertNotNull(reading)
        assertEquals(60f, reading!!.fps, 0.01f)
        assertEquals(12L, reading.droppedFrames)
        assertEquals(60f, reading.displayRefreshRate!!, 0.01f)
    }

    @Test
    fun framesOfTheSameLayerListedTwiceAreSummed() {
        val dump = layer("com.game/com.game.Main#0", 300) + "\n" + layer("com.game/com.game.Main#0", 300)
        assertEquals(60f, FpsReader.parse(dump, "com.game", 10.0, false)!!.fps, 0.01f)
    }

    @Test
    fun layerWithMostFramesWins() {
        val dump = layer("com.game/com.game.Main#0", 600) + "\n" + layer("com.game/com.game.Overlay#0", 20)
        val reading = FpsReader.parse(dump, "com.game", 10.0, false)!!
        assertEquals("com.game/com.game.Main#0", reading.layerName)
    }

    @Test
    fun otherPackagesAreIgnored() {
        val dump = layer("com.other/com.other.Main#0", 600)
        assertNull(FpsReader.parse(dump, "com.game", 10.0, false))
    }

    @Test
    fun idleAppReportsZeroOnlyWhenAllowed() {
        val dump = layer("com.other/com.other.Main#0", 600)
        assertEquals(0f, FpsReader.parse(dump, "com.game", 10.0, true)!!.fps, 0.0f)
    }

    @Test
    fun tooFewFramesGiveNoReadingForSessions() {
        assertNull(FpsReader.parse(layer("com.game/com.game.Main#0", 2), "com.game", 10.0, false))
    }

    @Test
    fun implausibleRateIsRejected() {
        assertNull(FpsReader.parse(layer("com.game/com.game.Main#0", 5000), "com.game", 10.0, false))
    }

    @Test
    fun garbageInputGivesNoReading() {
        assertNull(FpsReader.parse("", "com.game", 10.0, false))
        assertNull(FpsReader.parse("not a dump\n=\nlayerName", "com.game", 10.0, false))
    }
}

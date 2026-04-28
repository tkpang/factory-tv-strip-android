package com.tkpang.tvstriptest.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DpCommandsTest {
    @Test
    fun setPidJson() {
        assertEquals("{\"d158\":111}", DpCommands.setPid(111))
    }

    @Test
    fun maxBrightnessJsonClampsToValidRange() {
        assertEquals("{\"d162\":1000}", DpCommands.setMaxBrightness(1000))
        assertEquals("{\"d162\":1000}", DpCommands.setMaxBrightness(1200))
        assertEquals("{\"d162\":0}", DpCommands.setMaxBrightness(-1))
    }

    @Test
    fun grooveStateJson() {
        assertEquals("{\"d161\":1}", DpCommands.grooveState(true))
        assertEquals("{\"d161\":0}", DpCommands.grooveState(false))
    }

    @Test
    fun solidGrooveCommandUsesLedCountAndRgb() {
        assertEquals(
            "N01:P1000212abef12abef;",
            DpCommands.solidColorGroove(ledCount = 2, rgb = 0x12ABEF),
        )
    }

    @Test
    fun solidGrooveAcceptsLedCountBoundaries() {
        assertEquals(
            "N01:P10001000000;",
            DpCommands.solidColorGroove(ledCount = 1, rgb = 0x000000),
        )

        val maxLedCommand = DpCommands.solidColorGroove(ledCount = 0xFF, rgb = 0x000000)
        assertEquals("N01:P100ff", maxLedCommand.take("N01:P100ff".length))
        assertEquals(';', maxLedCommand.last())
    }

    @Test
    fun solidGrooveAcceptsRgbBoundaries() {
        assertEquals(
            "N01:P10001000000;",
            DpCommands.solidColorGroove(ledCount = 1, rgb = 0x000000),
        )
        assertEquals(
            "N01:P10001ffffff;",
            DpCommands.solidColorGroove(ledCount = 1, rgb = 0xFFFFFF),
        )
    }

    @Test
    fun grooveHandleWrapsGrooveInD160Json() {
        assertEquals(
            "{\"d160\":\"N01:P10001000000;\"}",
            DpCommands.grooveHandle("N01:P10001000000;"),
        )
    }

    @Test
    fun grooveHandleEscapesJsonStringCharacters() {
        assertEquals(
            "{\"d160\":\"\\\\\\\"\\n\\r\\t\\b\\f\\u0001\\u001f\"}",
            DpCommands.grooveHandle("\\\"\n\r\t\b\u000C\u0001\u001F"),
        )
    }

    @Test
    fun solidGrooveRejectsLedCountsOutsideTwoDigitHexRange() {
        assertThrows(IllegalArgumentException::class.java) {
            DpCommands.solidColorGroove(ledCount = 0, rgb = 0x000000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DpCommands.solidColorGroove(ledCount = 0x100, rgb = 0x000000)
        }
    }

    @Test
    fun solidGrooveRejectsRgbOutsideRgb24Range() {
        assertThrows(IllegalArgumentException::class.java) {
            DpCommands.solidColorGroove(ledCount = 1, rgb = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DpCommands.solidColorGroove(ledCount = 1, rgb = 0x1000000)
        }
    }

    @Test
    fun highestPowerSequenceReturnsStateBrightnessAndGrooveCommands() {
        assertEquals(
            listOf(
                "{\"d161\":1}",
                "{\"d162\":1000}",
                "{\"d160\":\"N01:P1000212abef12abef;\"}",
            ),
            DpCommands.highestPowerSequence(ledCount = 2, rgb = 0x12ABEF, brightness = 1200),
        )
    }
}

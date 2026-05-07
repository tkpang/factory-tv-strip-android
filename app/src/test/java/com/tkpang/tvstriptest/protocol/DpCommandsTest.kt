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
        assertEquals("{\"d162\":0}",    DpCommands.setMaxBrightness(-1))
    }

    @Test
    fun grooveStateJson() {
        assertEquals("{\"d161\":1}", DpCommands.grooveState(true))
        assertEquals("{\"d161\":0}", DpCommands.grooveState(false))
    }

    @Test
    fun solidColorGrooveProducesP10001Format() {
        assertEquals("N01:P10001ff0000;", DpCommands.solidColorGroove(0xFF0000))
        assertEquals("N01:P1000100ff00;", DpCommands.solidColorGroove(0x00FF00))
        assertEquals("N01:P100010000ff;", DpCommands.solidColorGroove(0x0000FF))
        assertEquals("N01:P10001ffffff;", DpCommands.solidColorGroove(0xFFFFFF))
        assertEquals("N01:P10001000000;", DpCommands.solidColorGroove(0x000000))
    }

    @Test
    fun solidColorGrooveRejectsRgbOutsideRange() {
        assertThrows(IllegalArgumentException::class.java) {
            DpCommands.solidColorGroove(rgb = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DpCommands.solidColorGroove(rgb = 0x1000000)
        }
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
            DpCommands.grooveHandle("\\\"\n\r\t\b"),
        )
    }

    @Test
    fun maxPowerCommandIsHardcodedFirmwareString() {
        assertEquals("N01:B21001200E5018003E80064;", DpCommands.MAX_POWER_COMMAND)
    }
}

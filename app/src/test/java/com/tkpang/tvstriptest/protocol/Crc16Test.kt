package com.tkpang.tvstriptest.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class Crc16Test {
    @Test
    fun crc16IbmKnownVector() {
        assertEquals(0xBB3D, Crc16.modbus("123456789".encodeToByteArray()))
    }
}

// AdvDataParserTest.kt
package com.tkpang.tvstriptest.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdvDataParserTest {
    @Test
    fun parsesValidUnbondedAdv() {
        val bytes = buildAdv(
            magic = "LP",
            bondByte = 0x20.toByte(),
            pid = 144,
        )
        val parsed = AdvDataParser.parse(bytes)
        assertEquals(144, parsed?.pid)
        assertEquals(false, parsed?.isBonded)
    }

    @Test
    fun parsesValidBondedAdv() {
        val bytes = buildAdv(magic = "LP", bondByte = 0xA0.toByte(), pid = 111)
        val parsed = AdvDataParser.parse(bytes)
        assertEquals(111, parsed?.pid)
        assertEquals(true, parsed?.isBonded)
    }

    @Test
    fun returnsNullWhenLengthTooShort() {
        assertNull(AdvDataParser.parse(byteArrayOf(0x4C, 0x50, 0x20)))
    }

    @Test
    fun returnsNullWhenMagicHeaderWrong() {
        val bytes = buildAdv(magic = "XX", bondByte = 0x20, pid = 144)
        assertNull(AdvDataParser.parse(bytes))
    }

    @Test
    fun treatsUnknownBondByteAsUnbonded() {
        val bytes = buildAdv(magic = "LP", bondByte = 0x00.toByte(), pid = 144)
        val parsed = AdvDataParser.parse(bytes)
        assertEquals(false, parsed?.isBonded)
    }

    @Test
    fun parsesPidZero() {
        val bytes = buildAdv(magic = "LP", bondByte = 0x20, pid = 0)
        assertEquals(0, AdvDataParser.parse(bytes)?.pid)
    }

    @Test
    fun parsesAllStvPids() {
        listOf(111, 112, 143, 144, 145, 158).forEach { pid ->
            val bytes = buildAdv(magic = "LP", bondByte = 0x20, pid = pid)
            assertEquals(pid, AdvDataParser.parse(bytes)?.pid)
        }
    }

    @Test
    fun parseStrippedReadsBondAndPidFromShortPayload() {
        val bytes = buildStripped(bondByte = 0xA0.toByte(), pid = 144)
        val parsed = AdvDataParser.parseStripped(bytes)
        assertEquals(144, parsed?.pid)
        assertEquals(true, parsed?.isBonded)
    }

    @Test
    fun parseStrippedReturnsNullWhenTooShort() {
        assertNull(AdvDataParser.parseStripped(byteArrayOf(0x20, 0x01)))
    }

    @Test
    fun parseStrippedHandlesAllStvPids() {
        listOf(111, 112, 143, 144, 145, 158).forEach { pid ->
            val bytes = buildStripped(bondByte = 0x20, pid = pid)
            assertEquals(pid, AdvDataParser.parseStripped(bytes)?.pid)
        }
    }

    private fun buildStripped(bondByte: Byte, pid: Int): ByteArray {
        // Android 把 manufacturer ID ('LP') 剥掉后给的 13 字节
        val data = ByteArray(13)
        data[0] = bondByte
        data[1] = 0x01  // version
        data[2] = 0x00  // encrypt
        // bytes 3..8 MAC (zero ok); PID 大端：高字节在前
        data[9]  = ((pid ushr 24) and 0xFF).toByte()
        data[10] = ((pid ushr 16) and 0xFF).toByte()
        data[11] = ((pid ushr 8)  and 0xFF).toByte()
        data[12] = (pid and 0xFF).toByte()
        return data
    }

    private fun buildAdv(magic: String, bondByte: Byte, pid: Int): ByteArray {
        val data = ByteArray(15)
        data[0] = magic[0].code.toByte()
        data[1] = magic[1].code.toByte()
        data[2] = bondByte
        data[3] = 0x01  // version
        data[4] = 0x00  // encrypt
        // bytes 5..10 MAC (zero ok); PID 大端：高字节在前
        data[11] = ((pid ushr 24) and 0xFF).toByte()
        data[12] = ((pid ushr 16) and 0xFF).toByte()
        data[13] = ((pid ushr 8)  and 0xFF).toByte()
        data[14] = (pid and 0xFF).toByte()
        return data
    }
}

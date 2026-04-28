package com.tkpang.tvstriptest.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LeMessageCodecTest {
    @Test
    fun encodesAndDecodesSingleFrame() {
        val payload = byteArrayOf(1, 2, 3, 4)
        val frame = LeMessageCodec.encode(cmd = LeConstants.LE_CMD_UNBOND, sn = 7, payload = payload)
        val decoded = LeMessageCodec.decode(frame)

        assertEquals(LeConstants.LE_CMD_UNBOND, decoded.cmd)
        assertEquals(7, decoded.sn)
        assertArrayEquals(payload, decoded.payload)
    }

    @Test
    fun encodesHeaderFieldsInBigEndianOrder() {
        val frame = LeMessageCodec.encode(
            cmd = LeConstants.LE_CMD_UNBOND,
            sn = 0x0102,
            payload = byteArrayOf(1, 2, 3, 4),
        )

        assertEquals(0x01, frame[4].toInt() and 0xFF)
        assertEquals(0x02, frame[5].toInt() and 0xFF)
        assertEquals(0x10, frame[6].toInt() and 0xFF)
        assertEquals(0x04, frame[7].toInt() and 0xFF)
        assertEquals(0x00, frame[8].toInt() and 0xFF)
        assertEquals(0x04, frame[9].toInt() and 0xFF)
    }

    @Test
    fun buildsBondPayloadWithMark() {
        assertArrayEquals(
            byteArrayOf(0x5a, 0x5a, 0xa5.toByte(), 0xa5.toByte(), 0, 0, 0, 0),
            LeMessageCodec.bondPayload(),
        )
    }

    @Test
    fun rejectsPayloadsLongerThanUint16() {
        assertThrows(IllegalArgumentException::class.java) {
            LeMessageCodec.encode(
                cmd = LeConstants.LE_CMD_UNBOND,
                sn = 7,
                payload = ByteArray(0x10000),
            )
        }
    }

    @Test
    fun rejectsNegativeSequenceNumber() {
        assertThrows(IllegalArgumentException::class.java) {
            LeMessageCodec.encode(
                cmd = LeConstants.LE_CMD_UNBOND,
                sn = -1,
                payload = byteArrayOf(),
            )
        }
    }

    @Test
    fun rejectsCommandsLongerThanUint16() {
        assertThrows(IllegalArgumentException::class.java) {
            LeMessageCodec.encode(
                cmd = 0x10000,
                sn = 7,
                payload = byteArrayOf(),
            )
        }
    }

    @Test
    fun rejectsUnsupportedCtrlWithValidCrc() {
        val frame = LeMessageCodec.encode(cmd = LeConstants.LE_CMD_UNBOND, sn = 7, payload = byteArrayOf())
        frame[3] = 0x51
        val crc = Crc16.modbus(frame.copyOfRange(2, frame.size))
        frame[0] = (crc ushr 8).toByte()
        frame[1] = crc.toByte()

        assertThrows(IllegalArgumentException::class.java) {
            LeMessageCodec.decode(frame)
        }
    }
}

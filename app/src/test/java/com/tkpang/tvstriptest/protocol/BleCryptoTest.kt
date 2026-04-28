package com.tkpang.tvstriptest.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BleCryptoTest {
    @Test
    fun derivesHandshakeKeyFromMacSuffix() {
        val crypto = BleCrypto.fromMac("AA:BB:CC:DD:EE:FF", random = 0x01020304)

        assertArrayEquals(
            bytes(0xdd, 0xee, 0xff, 0x35, 0x22, 0x13, 0x0e, 0xcf, 0xf4, 0xd2, 0x52, 0x5e, 0x6c, 0x29, 0x0b, 0xf9),
            crypto.key1,
        )
    }

    @Test
    fun derivesSessionKeyFromMacSuffixAndBigEndianRandom() {
        val crypto = BleCrypto.fromMac("AA:BB:CC:DD:EE:FF", random = 0x01020304)

        assertArrayEquals(
            bytes(0xdd, 0xee, 0xff, 0x01, 0x02, 0x03, 0x04, 0xcf, 0xf4, 0xd2, 0x52, 0x5e, 0x6c, 0x29, 0x0b, 0xf9),
            crypto.key2,
        )
    }

    @Test
    fun encryptsDevInfoGetWithKey1AndLaterCommandsWithKey2() {
        val crypto = BleCrypto.fromMac("AA:BB:CC:DD:EE:FF", random = 0x01020304)
        val devInfoPayload = LeMessageCodec.devInfoGetPayload(mtu = 247, random = 0x01020304, utcSeconds = 0x11223344)
        val devInfoFrame = LeMessageCodec.encodeEncrypted(LeConstants.LE_CMD_DEV_INFO_GET, 7, devInfoPayload, crypto)
        val bondFrame = LeMessageCodec.encodeEncrypted(LeConstants.LE_CMD_BOND, 8, LeMessageCodec.bondPayload(), crypto)

        val encryptedDevInfo = LeMessageCodec.decode(devInfoFrame).payload
        val encryptedBond = LeMessageCodec.decode(bondFrame).payload

        assertArrayEquals(devInfoPayload, AesCbc.decrypt(crypto.key1, BleCrypto.IV1, encryptedDevInfo))
        assertArrayEquals(LeMessageCodec.bondPayload(), AesCbc.decrypt(crypto.key2, BleCrypto.IV2, encryptedBond))
    }

    @Test
    fun parsesDevInfoResponseWithoutTreatingPidAsProductType() {
        val payload = byteArrayOf(
            0, 1, 0x01, 6,
            0, 0, 0, 111,
            0x12, 0x34, 0x56, 0x78,
            0, 0, 0, 9,
            0, 0, 0, 247.toByte(),
            0xf0.toByte(), 0xe0.toByte(),
            0, 0, 0, 0,
        ) + ByteArray(6) + ByteArray(33) + byteArrayOf(1, 9, 8, 7, 1, 2, 3)

        val info = LeMessageCodec.parseDevInfo(payload)

        assertEquals(111, info.pid)
        assertEquals(0x12345678L, info.did)
        assertEquals("9.8.7", info.firmwareVersion)
    }

    private fun bytes(vararg values: Int): ByteArray = values.map { it.toByte() }.toByteArray()
}

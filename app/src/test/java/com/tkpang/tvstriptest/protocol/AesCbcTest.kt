package com.tkpang.tvstriptest.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class AesCbcTest {
    @Test
    fun encryptsAndDecryptsRoundTrip() {
        val encrypted = AesCbc.encrypt(KEY, IV, PLAIN)
        val decrypted = AesCbc.decrypt(KEY, IV, encrypted)

        assertArrayEquals(PLAIN, decrypted)
    }

    @Test
    fun encryptsToStableCiphertext() {
        assertArrayEquals(EXPECTED_ENCRYPTED, AesCbc.encrypt(KEY, IV, PLAIN))
    }

    private companion object {
        val KEY = bytes(
            0x00, 0x01, 0x02, 0x03,
            0x04, 0x05, 0x06, 0x07,
            0x08, 0x09, 0x0a, 0x0b,
            0x0c, 0x0d, 0x0e, 0x0f,
        )
        val IV = bytes(
            0x10, 0x11, 0x12, 0x13,
            0x14, 0x15, 0x16, 0x17,
            0x18, 0x19, 0x1a, 0x1b,
            0x1c, 0x1d, 0x1e, 0x1f,
        )
        val PLAIN = "factory-tv-strip".encodeToByteArray()
        val EXPECTED_ENCRYPTED = bytes(
            0xa4, 0x1b, 0x5e, 0xab,
            0x55, 0xcd, 0x7e, 0x67,
            0x15, 0x21, 0x9e, 0xd7,
            0xd9, 0x5c, 0x64, 0xe8,
            0x8f, 0x95, 0x9e, 0xaf,
            0x2d, 0xe1, 0xe1, 0x33,
            0x55, 0xc8, 0x93, 0x71,
            0x6e, 0x12, 0xd0, 0x43,
        )

        fun bytes(vararg values: Int): ByteArray = values.map { it.toByte() }.toByteArray()
    }
}

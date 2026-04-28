package com.tkpang.tvstriptest.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class BleCrypto(
    val key1: ByteArray,
    val key2: ByteArray,
) {
    fun encrypt(cmd: Int, payload: ByteArray): ByteArray = if (payload.isEmpty()) {
        payload
    } else if (cmd == LeConstants.LE_CMD_DEV_INFO_GET) {
        AesCbc.encrypt(key1, IV1, payload)
    } else {
        AesCbc.encrypt(key2, IV2, payload)
    }

    fun decrypt(cmd: Int, payload: ByteArray): ByteArray = if (payload.isEmpty()) {
        payload
    } else if (cmd == LeConstants.LE_CMD_DEV_INFO_GET) {
        AesCbc.decrypt(key1, IV1, payload)
    } else {
        AesCbc.decrypt(key2, IV2, payload)
    }

    companion object {
        val IV1 = bytes(0x2c, 0x7e, 0xef, 0xe2, 0x94, 0x7e, 0x63, 0x5d, 0xe0, 0x9c, 0x73, 0xbd, 0x32, 0xed, 0xf3, 0xe3)
        val IV2 = bytes(0x50, 0xe2, 0x3b, 0x11, 0x1c, 0xc0, 0xae, 0x87, 0x31, 0xf5, 0xf3, 0xe7, 0x73, 0xab, 0xc4, 0x76)

        private val INIT_KEY = bytes(0x34, 0xae, 0xb4, 0x35, 0x22, 0x13, 0x0e, 0xcf, 0xf4, 0xd2, 0x52, 0x5e, 0x6c, 0x29, 0x0b, 0xf9)

        fun fromMac(macAddress: String, random: Int): BleCrypto {
            val mac = parseMac(macAddress)
            val suffix = mac.copyOfRange(3, 6)
            val key1 = INIT_KEY.copyOf().also { suffix.copyInto(it, destinationOffset = 0) }
            val key2 = INIT_KEY.copyOf().also { key ->
                suffix.copyInto(key, destinationOffset = 0)
                ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(random).array().copyInto(key, destinationOffset = 3)
            }
            return BleCrypto(key1, key2)
        }

        private fun parseMac(macAddress: String): ByteArray {
            val parts = macAddress.split(':')
            require(parts.size == 6) { "Invalid BLE MAC address: $macAddress" }
            return parts.map { it.toInt(16).toByte() }.toByteArray()
        }

        private fun bytes(vararg values: Int): ByteArray = values.map { it.toByte() }.toByteArray()
    }
}

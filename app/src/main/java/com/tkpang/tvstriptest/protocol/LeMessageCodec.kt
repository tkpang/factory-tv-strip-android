package com.tkpang.tvstriptest.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class LeMessage(val cmd: Int, val sn: Int, val payload: ByteArray)

data class BleDeviceInfo(
    val pid: Int,
    val did: Long,
    val firmwareVersion: String?,
)

object LeMessageCodec {
    fun encode(cmd: Int, sn: Int, payload: ByteArray): ByteArray {
        require(sn in 0..0xFFFF) { "Sequence number out of range: $sn" }
        require(cmd in 0..0xFFFF) { "Command out of range: $cmd" }
        require(payload.size <= 0xFFFF) { "Payload too long: ${payload.size}" }

        val withoutCrc = ByteBuffer.allocate(1 + 1 + 2 + 2 + 2 + payload.size)
            .order(ByteOrder.BIG_ENDIAN)
            .put(LeConstants.HDR_VER.toByte())
            .put(LeConstants.CTRL_NONE.toByte())
            .putShort(sn.toShort())
            .putShort(cmd.toShort())
            .putShort(payload.size.toShort())
            .put(payload)
            .array()
        val crc = Crc16.modbus(withoutCrc)
        return ByteBuffer.allocate(2 + withoutCrc.size)
            .order(ByteOrder.BIG_ENDIAN)
            .putShort(crc.toShort())
            .put(withoutCrc)
            .array()
    }

    fun decode(frame: ByteArray): LeMessage {
        require(frame.size >= 10) { "Frame too short" }
        val expectedCrc = ByteBuffer.wrap(frame, 0, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
        val actualCrc = Crc16.modbus(frame.copyOfRange(2, frame.size))
        require(expectedCrc == actualCrc) { "CRC mismatch expected=$expectedCrc actual=$actualCrc" }

        val buffer = ByteBuffer.wrap(frame).order(ByteOrder.BIG_ENDIAN)
        buffer.short
        val version = buffer.get().toInt() and 0xFF
        require(version == LeConstants.HDR_VER) { "Unexpected header version $version" }
        val ctrl = buffer.get().toInt() and 0xFF
        require(ctrl == LeConstants.CTRL_NONE) { "Unsupported ctrl: $ctrl" }
        val sn = buffer.short.toInt() and 0xFFFF
        val cmd = buffer.short.toInt() and 0xFFFF
        val len = buffer.short.toInt() and 0xFFFF
        require(frame.size == 10 + len) { "Unexpected payload length $len" }
        val payload = ByteArray(len)
        buffer.get(payload)
        return LeMessage(cmd, sn, payload)
    }

    fun encodeEncrypted(cmd: Int, sn: Int, payload: ByteArray, crypto: BleCrypto): ByteArray =
        encode(cmd, sn, crypto.encrypt(cmd, payload))

    fun decodeEncrypted(frame: ByteArray, crypto: BleCrypto): LeMessage {
        val message = decode(frame)
        return message.copy(payload = crypto.decrypt(message.cmd, message.payload))
    }

    fun devInfoGetPayload(mtu: Int, random: Int, utcSeconds: Int): ByteArray = ByteBuffer.allocate(16)
        .order(ByteOrder.BIG_ENDIAN)
        .putInt(LeConstants.LE_CMD_DEV_INFO_GET_MARK)
        .putInt(mtu)
        .putInt(random)
        .putInt(utcSeconds)
        .array()

    fun bondPayload(): ByteArray = ByteBuffer.allocate(8)
        .order(ByteOrder.BIG_ENDIAN)
        .putInt(LeConstants.LE_CMD_DEV_INFO_GET_MARK)
        .putInt(0)
        .array()

    fun responseCode(payload: ByteArray): Int {
        if (payload.size < 4) return -1
        return ByteBuffer.wrap(payload, 0, 4).order(ByteOrder.BIG_ENDIAN).int
    }

    fun parseDevInfo(payload: ByteArray): BleDeviceInfo {
        require(payload.size >= DEV_INFO_FIXED_LEN) { "DEV_INFO_GETR too short: ${payload.size}" }
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        buffer.position(4)
        val pid = buffer.int
        val did = buffer.int.toLong() and 0xFFFF_FFFFL
        buffer.position(65)
        val fwInfoCount = buffer.get().toInt() and 0xFF
        val firmwareVersion = if (fwInfoCount > 0 && payload.size >= DEV_INFO_FIXED_LEN + FW_HW_INFO_LEN) {
            val x = payload[66].toInt() and 0xFF
            val y = payload[67].toInt() and 0xFF
            val z = payload[68].toInt() and 0xFF
            "$x.$y.$z"
        } else {
            null
        }
        return BleDeviceInfo(pid = pid, did = did, firmwareVersion = firmwareVersion)
    }

    private const val DEV_INFO_FIXED_LEN = 66
    private const val FW_HW_INFO_LEN = 6
}

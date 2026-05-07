// AdvDataParser.kt
package com.tkpang.tvstriptest.protocol

object AdvDataParser {
    private const val MIN_LEN = 15
    private const val MAGIC_L = 'L'.code.toByte()
    private const val MAGIC_P = 'P'.code.toByte()

    data class Parsed(val pid: Int, val isBonded: Boolean)

    fun parse(bytes: ByteArray): Parsed? {
        if (bytes.size < MIN_LEN) return null
        if (bytes[0] != MAGIC_L || bytes[1] != MAGIC_P) return null

        val isBonded = (bytes[2].toInt() and 0x80) != 0  // 0xA0 高位 1 = 已绑
        val pid = (bytes[11].toInt() and 0xFF) or
            ((bytes[12].toInt() and 0xFF) shl 8) or
            ((bytes[13].toInt() and 0xFF) shl 16) or
            ((bytes[14].toInt() and 0xFF) shl 24)
        return Parsed(pid = pid, isBonded = isBonded)
    }
}

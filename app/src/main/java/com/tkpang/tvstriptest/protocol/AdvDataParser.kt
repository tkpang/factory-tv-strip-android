// AdvDataParser.kt
package com.tkpang.tvstriptest.protocol

/**
 * 解析 ESP32 固件 (le_ble.c) 在 BLE manufacturer-specific data 段写入的
 * factory_data 结构。
 *
 * 完整布局 (15 bytes)：
 *   [0..1]   'L' 'P'           (magic header / 也被当作 manufacturer ID)
 *   [2]      bond status       (0x20 unbonded / 0xA0 bonded; bit 7 = bonded)
 *   [3]      adv version
 *   [4]      encrypt type
 *   [5..10]  BT MAC (6 bytes)
 *   [11..14] PID (uint32 little-endian)
 *
 * Android BLE stack 把 'L' 'P' 作为 manufacturer ID (= 0x504C 小端)，
 * 然后 SparseArray.valueAt() 给出的字节数组**不含** 'LP' 前缀，长度 13。
 * 所以提供两个入口：
 *   - parse(bytes)         按完整布局（含 LP 前缀）解析，调用方可能从一些
 *                          不解析 mfg id 的栈拿到这种格式
 *   - parseStripped(bytes) 已剥掉 LP 前缀的 13 字节布局
 */
object AdvDataParser {
    private const val MAGIC_L = 'L'.code.toByte()
    private const val MAGIC_P = 'P'.code.toByte()

    data class Parsed(val pid: Int, val isBonded: Boolean)

    /** 完整 15 字节（含 'LP' 前缀）布局解析 */
    fun parse(bytes: ByteArray): Parsed? {
        if (bytes.size < 15) return null
        if (bytes[0] != MAGIC_L || bytes[1] != MAGIC_P) return null
        return parseAt(bytes, offset = 2)
    }

    /** 已剥掉 'LP' 前缀的 13 字节布局解析（Android `manufacturerSpecificData.get()` 拿到的形式）*/
    fun parseStripped(bytes: ByteArray): Parsed? {
        if (bytes.size < 13) return null
        return parseAt(bytes, offset = 0)
    }

    /** 通用解析：offset 指向 bond status 字节 */
    private fun parseAt(bytes: ByteArray, offset: Int): Parsed? {
        // 需要至少 13 字节从 offset 算起：bond + version + encrypt + 6 mac + 4 pid
        if (bytes.size - offset < 13) return null
        val isBonded = (bytes[offset].toInt() and 0x80) != 0
        val pid = (bytes[offset + 9].toInt() and 0xFF) or
            ((bytes[offset + 10].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 11].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 12].toInt() and 0xFF) shl 24)
        return Parsed(pid = pid, isBonded = isBonded)
    }
}

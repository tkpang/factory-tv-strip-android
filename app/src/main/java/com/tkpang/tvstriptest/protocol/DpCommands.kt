package com.tkpang.tvstriptest.protocol

object DpCommands {
    private const val MAX_GROOVE_LED_COUNT = 0xFF
    val basicColorTemplates: List<Int> = listOf(0xFF0000, 0x00FF00, 0x0000FF, 0xFFFFFF, 0x000000)
    val emcColorTemplates: List<Int> = basicColorTemplates

    fun setPid(pid: Int): String = "{\"d158\":$pid}"

    fun setMaxBrightness(value: Int): String = "{\"d162\":${value.coerceIn(0, 1000)}}"

    fun grooveState(enabled: Boolean): String = "{\"d161\":${if (enabled) 1 else 0}}"

    fun grooveHandle(groove: String): String = "{\"d160\":\"${escapeJson(groove)}\"}"

    fun solidColorGroove(ledCount: Int, rgb: Int): String {
        require(ledCount in 1..MAX_GROOVE_LED_COUNT) { "Invalid LED count: $ledCount" }

        val color = rgbHex(rgb)
        return "N01:P10001$color;"
    }

    fun highestPowerSequence(ledCount: Int, rgb: Int, brightness: Int): List<String> = listOf(
        grooveState(true),
        setMaxBrightness(brightness),
        grooveHandle(solidColorGroove(ledCount, rgb)),
    )

    private fun rgbHex(rgb: Int): String {
        require(rgb in 0x000000..0xFFFFFF) { "Invalid RGB value: $rgb" }
        return rgb.toString(16).padStart(6, '0')
    }

    private fun escapeJson(value: String): String = buildString {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                in '\u0000'..'\u001F' -> append("\\u00${char.code.toString(16).padStart(2, '0')}")
                else -> append(char)
            }
        }
    }
}

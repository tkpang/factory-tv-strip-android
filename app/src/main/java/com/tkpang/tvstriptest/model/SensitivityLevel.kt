package com.tkpang.tvstriptest.model

enum class SensitivityLevel(
    val displayName: String,
    val rssiThreshold: Int,
    val zoneRadiusFraction: Float,
) {
    VERY_CLOSE("紧贴", -45, 0.18f),
    CLOSE     ("很近", -55, 0.24f),
    NEAR      ("近",   -65, 0.33f),
    MEDIUM    ("中",   -75, 0.45f),
    FAR       ("较远", -85, 0.60f);

    companion object {
        val DEFAULT = NEAR
    }
}

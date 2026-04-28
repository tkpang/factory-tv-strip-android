package com.tkpang.tvstriptest.model

data class PidOption(
    val pid: Int,
    val displayName: String,
    val ledCount: Int?,
)

data class ProductType(
    val devName: String,
    val displayName: String,
    val pidOptions: List<PidOption>,
)

object ProductCatalog {
    val productTypes: List<ProductType> = listOf(
        ProductType(
            devName = "STV1",
            displayName = "STV1-摄像头灯带",
            pidOptions = listOf(
                PidOption(111, "STV1 Online 3M", 36),
                PidOption(112, "STV1 Online 5M", 50),
                PidOption(143, "STV1 Offline 3M", 48),
                PidOption(144, "STV1 Offline 5M", 68),
                PidOption(145, "STV1 Offline 3M High Density", 72),
                PidOption(158, "STV1 Online 2M", 24),
            ),
        ),
        ProductType("S2", "S2-RGBCW灯带", emptyList()),
        ProductType("SW1", "SW1-防水灯带", emptyList()),
        ProductType("S1_V2", "S1-V2-灯带", emptyList()),
    )

    fun requireLedCount(devName: String, pid: Int): Int {
        val product = productTypes.firstOrNull { it.devName == devName }
            ?: error("Unknown product type: $devName")
        val option = product.pidOptions.firstOrNull { it.pid == pid }
            ?: error("Unknown PID $pid for $devName")
        return option.ledCount ?: error("No LED count for $devName/$pid")
    }
}

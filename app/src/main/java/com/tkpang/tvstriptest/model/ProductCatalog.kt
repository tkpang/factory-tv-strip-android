package com.tkpang.tvstriptest.model

data class PidOption(
    val pid: Int,
    val displayName: String,
    val ledCount: Int? = null,  // 保留字段但全 null，未来可能用
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
            displayName = "STV1 摄像头灯带",
            pidOptions = listOf(
                PidOption(158, "线上 2 米"),
                PidOption(111, "线上 3 米"),
                PidOption(112, "线上 5 米"),
                PidOption(143, "线下 3 米"),
                PidOption(144, "线下 5 米"),
                PidOption(145, "线下 3 米 高密"),
            ),
        ),
        ProductType("S2",    "S2 RGBCW 灯带", emptyList()),
        ProductType("SW1",   "SW1 防水灯带",   emptyList()),
        ProductType("S1_V2", "S1-V2 灯带",     emptyList()),
    )

    // 临时保留以兼容现有 CommandDispatcher / FactoryViewModel 调用，Task 5 会删
    fun requireLedCount(devName: String, pid: Int): Int = 1
}

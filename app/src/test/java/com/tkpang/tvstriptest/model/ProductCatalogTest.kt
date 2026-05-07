package com.tkpang.tvstriptest.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ProductCatalogTest {
    @Test
    fun stv1HasAllSixPidOptionsInChinese() {
        val stv1 = ProductCatalog.productTypes.first { it.devName == "STV1" }
        val map = stv1.pidOptions.associateBy { it.pid }
        assertEquals("线上 3 米", map[111]?.displayName)
        assertEquals("线上 5 米", map[112]?.displayName)
        assertEquals("线上 2 米", map[158]?.displayName)
        assertEquals("线下 3 米", map[143]?.displayName)
        assertEquals("线下 5 米", map[144]?.displayName)
        assertEquals("线下 3 米 高密", map[145]?.displayName)
    }

    @Test
    fun otherProductTypesArePresentButEmpty() {
        listOf("S2", "SW1", "S1_V2").forEach { devName ->
            val product = ProductCatalog.productTypes.firstOrNull { it.devName == devName }
            assertNotNull("$devName missing", product)
            assertEquals(emptyList<PidOption>(), product?.pidOptions)
        }
    }

    @Test
    fun stv1DisplayNameIsChinese() {
        val stv1 = ProductCatalog.productTypes.first { it.devName == "STV1" }
        assertEquals("STV1 摄像头灯带", stv1.displayName)
    }
}

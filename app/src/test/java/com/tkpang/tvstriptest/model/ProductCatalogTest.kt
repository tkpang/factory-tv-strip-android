package com.tkpang.tvstriptest.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductCatalogTest {
    @Test
    fun stv1IsProductTypeAndPidsAreSkuOptions() {
        val stv1 = ProductCatalog.productTypes.first { it.devName == "STV1" }
        assertEquals("STV1-摄像头灯带", stv1.displayName)
        assertTrue(stv1.pidOptions.map { it.pid }.containsAll(listOf(111, 112, 143, 144, 145, 158)))
    }

    @Test
    fun ledCountForStv1Pid() {
        assertEquals(36, ProductCatalog.requireLedCount("STV1", 111))
        assertEquals(50, ProductCatalog.requireLedCount("STV1", 112))
        assertEquals(48, ProductCatalog.requireLedCount("STV1", 143))
        assertEquals(68, ProductCatalog.requireLedCount("STV1", 144))
        assertEquals(72, ProductCatalog.requireLedCount("STV1", 145))
        assertEquals(24, ProductCatalog.requireLedCount("STV1", 158))
    }
}

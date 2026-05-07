package com.tkpang.tvstriptest.factory

import com.tkpang.tvstriptest.model.PidFilter
import com.tkpang.tvstriptest.model.ScanDevice
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanFilterUseCaseTest {
    private val devices = listOf(
        d("AA:01", pid = 144, bonded = false),
        d("AA:02", pid = 144, bonded = true),
        d("AA:03", pid = 111, bonded = false),
        d("AA:04", pid = null, bonded = false),
    )

    @Test
    fun anyPidPlusOnlyUnbondedKeeps01And03() {
        val out = ScanFilterUseCase.apply(devices, PidFilter.Any, onlyUnbonded = true)
        assertEquals(listOf("AA:01", "AA:03"), out.map { it.address })
    }

    @Test
    fun anyPidPlusBondedAllowedKeepsAll() {
        val out = ScanFilterUseCase.apply(devices, PidFilter.Any, onlyUnbonded = false)
        assertEquals(listOf("AA:01", "AA:02", "AA:03", "AA:04"), out.map { it.address })
    }

    @Test
    fun specificPidPlusOnlyUnbondedKeepsOnly01() {
        val out = ScanFilterUseCase.apply(devices, PidFilter.Specific(144), onlyUnbonded = true)
        assertEquals(listOf("AA:01"), out.map { it.address })
    }

    @Test
    fun specificPidPlusBondedAllowedKeeps01And02() {
        val out = ScanFilterUseCase.apply(devices, PidFilter.Specific(144), onlyUnbonded = false)
        assertEquals(listOf("AA:01", "AA:02"), out.map { it.address })
    }

    @Test
    fun nullPidIsExcludedWhenSpecificFilterUsed() {
        val out = ScanFilterUseCase.apply(devices, PidFilter.Specific(144), onlyUnbonded = false)
        assertEquals(false, out.any { it.address == "AA:04" })
    }

    @Test
    fun nullPidIsKeptWhenAnyFilter() {
        val out = ScanFilterUseCase.apply(devices, PidFilter.Any, onlyUnbonded = false)
        assertEquals(true, out.any { it.address == "AA:04" })
    }

    private fun d(addr: String, pid: Int?, bonded: Boolean) = ScanDevice(
        address = addr, name = "LP", rssi = -60, pid = pid, isBonded = bonded,
    )
}

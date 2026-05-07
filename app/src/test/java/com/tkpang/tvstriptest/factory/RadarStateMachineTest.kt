package com.tkpang.tvstriptest.factory

import com.tkpang.tvstriptest.model.ScanDevice
import com.tkpang.tvstriptest.model.SensitivityLevel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RadarStateMachineTest {

    @Test
    fun deviceInZoneFor1500msEmitsPairingTrigger() = runTest {
        val source = MutableStateFlow(listOf(d("AA:01", rssi = -50)))
        val sm = RadarStateMachine(
            sensitivity = MutableStateFlow(SensitivityLevel.NEAR),
            source = source,
            scope = backgroundScope,
            stableMillis = 1500L,
        )
        val triggers = mutableListOf<String>()
        backgroundScope.launchCollectTriggers(sm, triggers)

        advanceTimeBy(1499)
        assertEquals(emptyList<String>(), triggers)
        advanceTimeBy(2)
        assertEquals(listOf("AA:01"), triggers)
    }

    @Test
    fun deviceLeavingZoneCancelsTimer() = runTest {
        val source = MutableStateFlow(listOf(d("AA:01", rssi = -50)))
        val sm = RadarStateMachine(
            sensitivity = MutableStateFlow(SensitivityLevel.NEAR),
            source = source,
            scope = backgroundScope,
            stableMillis = 1500L,
        )
        val triggers = mutableListOf<String>()
        backgroundScope.launchCollectTriggers(sm, triggers)

        advanceTimeBy(1000)
        source.value = listOf(d("AA:01", rssi = -80))
        advanceTimeBy(2000)

        assertEquals(emptyList<String>(), triggers)
    }

    @Test
    fun multipleDevicesInZoneFireBothTriggers() = runTest {
        val source = MutableStateFlow(
            listOf(d("AA:01", rssi = -50), d("AA:02", rssi = -55)),
        )
        val sm = RadarStateMachine(
            sensitivity = MutableStateFlow(SensitivityLevel.NEAR),
            source = source,
            scope = backgroundScope,
            stableMillis = 1500L,
        )
        val triggers = mutableListOf<String>()
        backgroundScope.launchCollectTriggers(sm, triggers)

        advanceTimeBy(1600)
        assertEquals(setOf("AA:01", "AA:02"), triggers.toSet())
    }

    @Test
    fun deviceDisappearingFromScanCancelsTimer() = runTest {
        val source = MutableStateFlow(listOf(d("AA:01", rssi = -50)))
        val sm = RadarStateMachine(
            sensitivity = MutableStateFlow(SensitivityLevel.NEAR),
            source = source,
            scope = backgroundScope,
            stableMillis = 1500L,
        )
        val triggers = mutableListOf<String>()
        backgroundScope.launchCollectTriggers(sm, triggers)

        advanceTimeBy(1000)
        source.value = emptyList()
        advanceTimeBy(2000)
        assertEquals(emptyList<String>(), triggers)
    }

    private fun d(addr: String, rssi: Int) = ScanDevice(
        address = addr, name = "LP", rssi = rssi, pid = 144, isBonded = false,
    )
}

private fun kotlinx.coroutines.CoroutineScope.launchCollectTriggers(
    sm: RadarStateMachine,
    sink: MutableList<String>,
) {
    kotlinx.coroutines.launch {
        sm.pairingTriggers.collect { sink += it }
    }
}

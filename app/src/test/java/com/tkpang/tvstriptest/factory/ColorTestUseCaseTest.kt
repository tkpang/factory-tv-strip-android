package com.tkpang.tvstriptest.factory

import com.tkpang.tvstriptest.model.FactoryDevice
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorTestUseCaseTest {

    @Test
    fun redDispatchesSetColorRedToAllDevices() = runTest {
        val dispatcher = RecordingDispatcher()
        val useCase = ColorTestUseCase(dispatcher)

        val result = useCase.execute(
            devices = listOf(dev("AA:01"), dev("AA:02"), dev("AA:03")),
            command = ColorTestUseCase.Command.RED,
        )

        assertEquals(3, result.success)
        assertEquals(0, result.failed.size)
        assertEquals(listOf(0xFF0000, 0xFF0000, 0xFF0000), dispatcher.colors)
    }

    @Test
    fun offDispatchesLightOff() = runTest {
        val dispatcher = RecordingDispatcher()
        val useCase = ColorTestUseCase(dispatcher)
        useCase.execute(listOf(dev("AA:01")), ColorTestUseCase.Command.OFF)
        assertEquals(listOf("lightOff"), dispatcher.calls)
    }

    @Test
    fun maxPowerDispatchesSetMaxPower() = runTest {
        val dispatcher = RecordingDispatcher()
        val useCase = ColorTestUseCase(dispatcher)
        useCase.execute(listOf(dev("AA:01")), ColorTestUseCase.Command.MAX_POWER)
        assertEquals(listOf("setMaxPower"), dispatcher.calls)
    }

    @Test
    fun mixedSuccessFailureProducesPartialResult() = runTest {
        val dispatcher = RecordingDispatcher(failAddresses = setOf("AA:02"))
        val useCase = ColorTestUseCase(dispatcher)
        val result = useCase.execute(
            listOf(dev("AA:01"), dev("AA:02"), dev("AA:03")),
            ColorTestUseCase.Command.GREEN,
        )
        assertEquals(2, result.success)
        assertEquals(setOf("AA:02"), result.failed.map { it.address }.toSet())
    }

    private fun dev(addr: String) = FactoryDevice(address = addr, name = null)

    private class RecordingDispatcher(
        private val failAddresses: Set<String> = emptySet(),
    ) : CommandDispatcher {
        val colors = mutableListOf<Int>()
        val calls = mutableListOf<String>()
        override suspend fun connect(device: FactoryDevice) = ok(device)
        override suspend fun setPid(device: FactoryDevice, pid: Int) = ok(device)
        override suspend fun setColor(device: FactoryDevice, rgb: Int): CommandResult {
            colors += rgb
            calls += "setColor"
            return result(device)
        }
        override suspend fun setMaxPower(device: FactoryDevice): CommandResult {
            calls += "setMaxPower"; return result(device)
        }
        override suspend fun lightOff(device: FactoryDevice): CommandResult {
            calls += "lightOff"; return result(device)
        }
        override suspend fun setRainbowScene(device: FactoryDevice): CommandResult {
            calls += "setRainbowScene"; return result(device)
        }
        override suspend fun setBrightness(device: FactoryDevice, value: Int): CommandResult {
            calls += "setBrightness"; return result(device)
        }
        override suspend fun unbindAndDelete(device: FactoryDevice) = ok(device)
        override suspend fun closeAll() = Unit
        private fun result(d: FactoryDevice) =
            if (d.address in failAddresses) CommandResult(d.address, false, "boom")
            else CommandResult(d.address, true, "ok")
        private fun ok(d: FactoryDevice) = CommandResult(d.address, true, "ok")
    }
}

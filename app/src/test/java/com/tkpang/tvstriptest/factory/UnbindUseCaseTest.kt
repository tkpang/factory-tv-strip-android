package com.tkpang.tvstriptest.factory

import com.tkpang.tvstriptest.model.FactoryDevice
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UnbindUseCaseTest {

    @Test
    fun unbindsAllDevicesAndEmitsProgress() = runTest {
        val dispatcher = RecordingDispatcher()
        val useCase = UnbindUseCase(dispatcher)
        val devices = listOf(d("AA:01"), d("AA:02"), d("AA:03"))

        val progress = useCase.execute(devices).toList()
        assertEquals(3, progress.size)
        assertEquals(3, progress.last().completed)
        assertEquals(0, progress.last().failed.size)
    }

    @Test
    fun reportsFailedDevicesWhenSomeFail() = runTest {
        val dispatcher = RecordingDispatcher(failAddresses = setOf("AA:02"))
        val useCase = UnbindUseCase(dispatcher)
        val progress = useCase.execute(listOf(d("AA:01"), d("AA:02"), d("AA:03"))).toList()
        val final = progress.last()
        assertEquals(2, final.completed)
        assertEquals(setOf("AA:02"), final.failed.map { it.address }.toSet())
    }

    private fun d(addr: String) = FactoryDevice(address = addr, name = null)

    private class RecordingDispatcher(
        private val failAddresses: Set<String> = emptySet(),
    ) : CommandDispatcher {
        override suspend fun connect(device: FactoryDevice) = ok(device)
        override suspend fun setPid(device: FactoryDevice, pid: Int) = ok(device)
        override suspend fun setColor(device: FactoryDevice, rgb: Int) = ok(device)
        override suspend fun setMaxPower(device: FactoryDevice) = ok(device)
        override suspend fun lightOff(device: FactoryDevice) = ok(device)
        override suspend fun unbindAndDelete(device: FactoryDevice) =
            if (device.address in failAddresses) CommandResult(device.address, false, "boom")
            else ok(device)
        override suspend fun closeAll() = Unit
        private fun ok(d: FactoryDevice) = CommandResult(d.address, true, "ok")
    }
}

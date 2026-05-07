package com.tkpang.tvstriptest.factory

import com.tkpang.tvstriptest.model.DeviceConnectionState
import com.tkpang.tvstriptest.model.FactoryDevice
import com.tkpang.tvstriptest.model.PidFilter
import com.tkpang.tvstriptest.protocol.BleDeviceInfo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingExecutorTest {

    @Test
    fun successfulConnectAndPidMatchProducesPaired() = runTest {
        val dispatcher = FakeDispatcher(connectInfo = BleDeviceInfo(pid = 144, did = 1, firmwareVersion = null))
        val exec = PairingExecutorImpl(dispatcher, pidFilter = PidFilter.Specific(144))
        val outcome = exec.pair("AA:01")
        assertTrue(outcome is PairingOutcome.Paired)
        assertEquals(144, (outcome as PairingOutcome.Paired).pid)
    }

    @Test
    fun pidMismatchReportsPidMismatch() = runTest {
        val dispatcher = FakeDispatcher(connectInfo = BleDeviceInfo(pid = 111, did = 1, firmwareVersion = null))
        val exec = PairingExecutorImpl(dispatcher, pidFilter = PidFilter.Specific(144))
        val outcome = exec.pair("AA:01")
        assertEquals(PairingOutcome.PidMismatch("AA:01", actualPid = 111), outcome)
    }

    @Test
    fun connectFailureReportsConnectFailed() = runTest {
        val dispatcher = FakeDispatcher(connectInfo = null)
        val exec = PairingExecutorImpl(dispatcher, pidFilter = PidFilter.Any)
        val outcome = exec.pair("AA:01")
        assertTrue(outcome is PairingOutcome.ConnectFailed)
    }

    @Test
    fun anyPidFilterAcceptsMismatchPid() = runTest {
        val dispatcher = FakeDispatcher(connectInfo = BleDeviceInfo(pid = 999, did = 1, firmwareVersion = null))
        val exec = PairingExecutorImpl(dispatcher, pidFilter = PidFilter.Any)
        val outcome = exec.pair("AA:01")
        assertTrue(outcome is PairingOutcome.Paired)
    }

    private class FakeDispatcher(private val connectInfo: BleDeviceInfo?) : CommandDispatcher {
        override suspend fun connect(device: FactoryDevice): CommandResult = CommandResult(
            address = device.address,
            success = connectInfo != null,
            message = if (connectInfo != null) "ok" else "connect failed",
            deviceInfo = connectInfo,
        )
        override suspend fun setPid(device: FactoryDevice, pid: Int) = ok(device)
        override suspend fun setColor(device: FactoryDevice, rgb: Int) = ok(device)
        override suspend fun setMaxPower(device: FactoryDevice) = ok(device)
        override suspend fun lightOff(device: FactoryDevice) = ok(device)
        override suspend fun unbindAndDelete(device: FactoryDevice) = ok(device)
        override suspend fun closeAll() = Unit
        private fun ok(d: FactoryDevice) = CommandResult(d.address, true, "ok")
    }
}

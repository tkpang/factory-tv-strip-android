package com.tkpang.tvstriptest.factory

import com.tkpang.tvstriptest.model.DeviceConnectionState
import com.tkpang.tvstriptest.model.FactoryDevice
import com.tkpang.tvstriptest.model.FactorySettings
import com.tkpang.tvstriptest.protocol.LeConstants
import com.tkpang.tvstriptest.protocol.LeMessageCodec
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandDispatcherTest {
    @Test
    fun setPidConnectsAndWritesDpSetFrame() = runTest {
        val session = RecordingSession()
        val dispatcher = PerDeviceBleDispatcher { session }

        val result = dispatcher.setPid(device(), FactorySettings(pid = 144))

        assertTrue(result.success)
        assertEquals(listOf("connect", "write"), session.calls)
        val message = LeMessageCodec.decode(session.writes.single())
        assertEquals(LeConstants.LE_CMD_DP_PRP_SET, message.cmd)
        assertEquals("{\"d158\":144}\u0000", message.payload.decodeToString())
    }

    @Test
    fun commandsReuseConnectedSessionUntilUnbind() = runTest {
        val sessions = mutableListOf<RecordingSession>()
        val dispatcher = PerDeviceBleDispatcher {
            RecordingSession().also { sessions += it }
        }

        assertTrue(dispatcher.connect(device()).success)
        assertTrue(dispatcher.setPid(device(), FactorySettings(pid = 111)).success)
        assertTrue(dispatcher.setColor(device(), FactorySettings(pid = 111), rgb = 0xFF0000).success)

        assertEquals(1, sessions.size)
        assertEquals(listOf("connect", "write", "write"), sessions.single().calls)

        assertTrue(dispatcher.unbindAndDelete(device()).success)
        assertEquals(listOf("connect", "write", "write", "write", "close"), sessions.single().calls)
    }

    @Test
    fun closeAllClosesRetainedSessions() = runTest {
        val session = RecordingSession()
        val dispatcher = PerDeviceBleDispatcher { session }

        assertTrue(dispatcher.connect(device()).success)
        dispatcher.closeAll()

        assertEquals(listOf("connect", "close"), session.calls)
    }

    @Test
    fun highestPowerColorWritesBrightnessSequenceInOrder() = runTest {
        val session = RecordingSession()
        val dispatcher = PerDeviceBleDispatcher { session }

        val result = dispatcher.setHighestPowerColor(
            device(),
            FactorySettings(pid = 111, maxPowerColor = 0x12ABEF, maxBrightness = 1200),
        )

        assertTrue(result.success)
        val payloads = session.writes.map { LeMessageCodec.decode(it).payload.decodeToString() }
        assertEquals(
            listOf(
                "{\"d161\":1}\u0000",
                "{\"d162\":1000}\u0000",
                "{\"d160\":\"N01:P1000112abef;\"}\u0000",
            ),
            payloads,
        )
    }

    @Test
    fun setColorTurnsOnLightAndWritesFactorySolidColorCommand() = runTest {
        val session = RecordingSession()
        val dispatcher = PerDeviceBleDispatcher { session }

        val result = dispatcher.setColor(device(), FactorySettings(pid = 111, maxBrightness = 1000), rgb = 0xFF0000)

        assertTrue(result.success)
        val messages = session.writes.map { LeMessageCodec.decode(it) }
        assertEquals(listOf(LeConstants.LE_CMD_DP_PRP_SET, LeConstants.LE_CMD_DP_PRP_SET, LeConstants.LE_CMD_DP_PRP_SET), messages.map { it.cmd })
        assertEquals(
            listOf(
                "{\"d161\":1}\u0000",
                "{\"d162\":1000}\u0000",
                "{\"d160\":\"N01:P10001ff0000;\"}\u0000",
            ),
            messages.map { it.payload.decodeToString() },
        )
    }

    @Test
    fun dpSetPayloadsAreNullTerminatedForFirmwareStringParser() = runTest {
        val session = RecordingSession()
        val dispatcher = PerDeviceBleDispatcher { session }

        val result = dispatcher.setPid(device(), FactorySettings(pid = 144))

        assertTrue(result.success)
        val message = LeMessageCodec.decode(session.writes.single())
        assertEquals(0, message.payload.last().toInt())
    }

    @Test
    fun unbindUsesBondPayloadMarkAndReserve() = runTest {
        val session = RecordingSession()
        val dispatcher = PerDeviceBleDispatcher { session }

        val result = dispatcher.unbindAndDelete(device())

        assertTrue(result.success)
        val message = LeMessageCodec.decode(session.writes.single())
        assertEquals(LeConstants.LE_CMD_UNBOND, message.cmd)
        assertEquals(LeMessageCodec.bondPayload().toList(), message.payload.toList())
    }

    @Test
    fun missingSessionFailsWithoutThrowing() = runTest {
        val dispatcher = PerDeviceBleDispatcher { null }

        val result = dispatcher.unbindAndDelete(device())

        assertEquals(false, result.success)
        assertEquals("No BLE session", result.message)
    }

    private fun device() = FactoryDevice(
        address = "AA:BB:CC:DD:EE:FF",
        name = "LP",
        rssi = -42,
        state = DeviceConnectionState.Discovered,
    )

    private class RecordingSession : DeviceCommandSession {
        val calls = mutableListOf<String>()
        val writes = mutableListOf<ByteArray>()

        override suspend fun connect(): Boolean {
            calls += "connect"
            return true
        }

        override suspend fun write(payload: ByteArray): Boolean {
            calls += "write"
            writes += payload
            return true
        }

        override suspend fun close() {
            calls += "close"
        }
    }
}

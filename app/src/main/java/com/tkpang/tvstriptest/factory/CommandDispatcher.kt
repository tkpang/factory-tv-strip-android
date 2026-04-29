package com.tkpang.tvstriptest.factory

import com.tkpang.tvstriptest.model.FactoryDevice
import com.tkpang.tvstriptest.model.FactorySettings
import com.tkpang.tvstriptest.model.ProductCatalog
import com.tkpang.tvstriptest.protocol.BleDeviceInfo
import com.tkpang.tvstriptest.protocol.DpCommands
import com.tkpang.tvstriptest.protocol.LeConstants
import com.tkpang.tvstriptest.protocol.LeMessageCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface DeviceCommandSession {
    val connectFailureMessage: String? get() = null
    val deviceInfo: BleDeviceInfo? get() = null
    suspend fun connect(): Boolean
    suspend fun write(payload: ByteArray): Boolean
    suspend fun transact(payload: ByteArray, expectedResponseCmd: Int, timeoutMillis: Long = 10_000L): Boolean = write(payload)
    suspend fun close()

    companion object {
        const val RESPONSE_TIMEOUT_MS = 10_000L
    }
}

data class CommandResult(
    val address: String,
    val success: Boolean,
    val message: String,
    val deviceInfo: BleDeviceInfo? = null,
)

interface CommandDispatcher {
    suspend fun connect(device: FactoryDevice): CommandResult
    suspend fun setPid(device: FactoryDevice, settings: FactorySettings): CommandResult
    suspend fun setHighestPowerColor(device: FactoryDevice, settings: FactorySettings): CommandResult
    suspend fun setColor(device: FactoryDevice, settings: FactorySettings, rgb: Int): CommandResult
    suspend fun unbindAndDelete(device: FactoryDevice): CommandResult
    suspend fun closeAll()
}

// Per-device BLE dispatch stays behind CommandDispatcher so a future GroupDispatcher can batch commands differently.
class PerDeviceBleDispatcher(
    private val sessionFactory: (FactoryDevice) -> DeviceCommandSession? = { null },
) : CommandDispatcher {
    private var sequenceNumber = 1
    private val sessions = mutableMapOf<String, DeviceCommandSession>()

    override suspend fun connect(device: FactoryDevice): CommandResult = withContext(Dispatchers.IO) {
        val lookup = getOrConnectSession(device)
        val session = lookup.session
            ?: return@withContext CommandResult(device.address, success = false, message = lookup.errorMessage)
        CommandResult(
            device.address,
            success = true,
            message = session.connectFailureMessage ?: "Connected",
            deviceInfo = session.deviceInfo,
        )
    }

    override suspend fun setPid(device: FactoryDevice, settings: FactorySettings): CommandResult = withSession(
        device = device,
        commands = listOf(DpCommands.setPid(settings.pid)),
        successMessage = "PID set to ${settings.pid}",
    )

    override suspend fun setHighestPowerColor(device: FactoryDevice, settings: FactorySettings): CommandResult {
        val ledCount = try {
            ProductCatalog.requireLedCount(settings.productDevName, settings.pid)
        } catch (error: IllegalArgumentException) {
            return CommandResult(device.address, success = false, message = error.message ?: "Invalid product")
        } catch (error: IllegalStateException) {
            return CommandResult(device.address, success = false, message = error.message ?: "Invalid product")
        }
        return withSession(
            device = device,
            commands = DpCommands.highestPowerSequence(
                ledCount = ledCount,
                rgb = settings.maxPowerColor,
                brightness = settings.maxBrightness,
            ),
            successMessage = "Highest power color set",
        )
    }

    override suspend fun setColor(device: FactoryDevice, settings: FactorySettings, rgb: Int): CommandResult {
        val ledCount = try {
            ProductCatalog.requireLedCount(settings.productDevName, settings.pid)
        } catch (error: IllegalArgumentException) {
            return CommandResult(device.address, success = false, message = error.message ?: "Invalid product")
        } catch (error: IllegalStateException) {
            return CommandResult(device.address, success = false, message = error.message ?: "Invalid product")
        }
        return withSession(
            device = device,
            commands = listOf(DpCommands.grooveHandle(DpCommands.solidColorGroove(ledCount, rgb))),
            successMessage = "Color set",
        )
    }

    override suspend fun unbindAndDelete(device: FactoryDevice): CommandResult = withRawPayload(
        device = device,
        cmd = LeConstants.LE_CMD_UNBOND,
        expectedResponseCmd = LeConstants.LE_CMD_UNBONDR,
        payload = LeMessageCodec.bondPayload(),
        successMessage = "Unbound and deleted",
        closeAfterSuccess = true,
    )

    override suspend fun closeAll() = withContext(Dispatchers.IO) {
        val retainedSessions = synchronized(sessions) {
            sessions.values.toList().also { sessions.clear() }
        }
        retainedSessions.forEach { it.close() }
    }

    private suspend fun withSession(
        device: FactoryDevice,
        commands: List<String>,
        successMessage: String,
    ): CommandResult = withContext(Dispatchers.IO) {
        val lookup = getOrConnectSession(device)
        val session = lookup.session
            ?: return@withContext CommandResult(device.address, success = false, message = lookup.errorMessage)
        try {
            for (command in commands) {
                if (!session.transact(encodeDpCommand(command), LeConstants.LE_CMD_DP_PRP_SETR)) {
                    return@withContext CommandResult(device.address, success = false, message = "Write failed")
                }
            }
            CommandResult(device.address, success = true, message = successMessage)
        } catch (error: IllegalArgumentException) {
            CommandResult(device.address, success = false, message = error.message ?: "Invalid command")
        }
    }

    private suspend fun withRawPayload(
        device: FactoryDevice,
        cmd: Int,
        expectedResponseCmd: Int,
        payload: ByteArray,
        successMessage: String,
        closeAfterSuccess: Boolean = false,
    ): CommandResult = withContext(Dispatchers.IO) {
        val lookup = getOrConnectSession(device)
        val session = lookup.session
            ?: return@withContext CommandResult(device.address, success = false, message = lookup.errorMessage)
        if (!session.transact(encode(cmd, payload), expectedResponseCmd)) {
            return@withContext CommandResult(device.address, success = false, message = "Write failed")
        }
        if (closeAfterSuccess) {
            removeAndCloseSession(device.address)
        }
        CommandResult(device.address, success = true, message = successMessage)
    }

    private suspend fun getOrConnectSession(device: FactoryDevice): SessionLookup {
        synchronized(sessions) { sessions[device.address] }?.let { return SessionLookup(session = it) }
        val session = sessionFactory(device) ?: return SessionLookup(errorMessage = "No BLE session")
        if (!session.connect()) {
            session.close()
            return SessionLookup(errorMessage = session.connectFailureMessage ?: "Connect failed")
        }
        synchronized(sessions) { sessions[device.address] = session }
        return SessionLookup(session = session)
    }

    private suspend fun removeAndCloseSession(address: String) {
        val session = synchronized(sessions) { sessions.remove(address) } ?: return
        session.close()
    }

    private data class SessionLookup(
        val session: DeviceCommandSession? = null,
        val errorMessage: String = "No BLE session",
    )

    private fun encodeDpCommand(command: String): ByteArray = encode(
        cmd = LeConstants.LE_CMD_DP_PRP_SET,
        payload = command.encodeToByteArray() + byteArrayOf(0),
    )

    private fun encode(cmd: Int, payload: ByteArray): ByteArray = LeMessageCodec.encode(
        cmd = cmd,
        sn = nextSequenceNumber(),
        payload = payload,
    )

    private fun nextSequenceNumber(): Int {
        val current = sequenceNumber
        sequenceNumber = if (sequenceNumber == 0xFFFF) 1 else sequenceNumber + 1
        return current
    }
}

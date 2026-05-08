package com.tkpang.tvstriptest.factory

import com.tkpang.tvstriptest.model.FactoryDevice
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
    suspend fun setPid(device: FactoryDevice, pid: Int): CommandResult
    suspend fun setColor(device: FactoryDevice, rgb: Int): CommandResult
    suspend fun setMaxPower(device: FactoryDevice): CommandResult
    suspend fun lightOff(device: FactoryDevice): CommandResult
    suspend fun setRainbowScene(device: FactoryDevice): CommandResult
    /** 单独调亮度 (d52)，色盘/亮度条拖动时高频下发用 */
    suspend fun setBrightness(device: FactoryDevice, value: Int): CommandResult
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

    override suspend fun setPid(device: FactoryDevice, pid: Int): CommandResult = withSession(
        device = device,
        commands = listOf(DpCommands.setPid(pid)),
        successMessage = "PID set to $pid",
    )

    override suspend fun setColor(device: FactoryDevice, rgb: Int): CommandResult = withSession(
        device = device,
        // 设备出厂可能在 TV/未定义模式，d160 静态色不响应。固件 do_test_3_loop
        // 强制 d2=WORK_MODE_SCENE(2) 后才调 P10001<rgb>，我们这里也照做。
        commands = listOf(
            DpCommands.grooveState(true),
            DpCommands.setWorkMode(2),
            DpCommands.setMaxBrightness(1000),
            DpCommands.grooveHandle(DpCommands.solidColorGroove(rgb)),
        ),
        successMessage = "Color set",
    )

    override suspend fun setMaxPower(device: FactoryDevice): CommandResult = withSession(
        device = device,
        commands = listOf(
            DpCommands.grooveState(true),
            DpCommands.setWorkMode(2),
            DpCommands.grooveHandle(DpCommands.MAX_POWER_COMMAND),
        ),
        successMessage = "Max power set",
    )

    override suspend fun lightOff(device: FactoryDevice): CommandResult = withSession(
        device = device,
        commands = listOf(DpCommands.grooveState(false)),
        successMessage = "Light off",
    )

    override suspend fun setBrightness(device: FactoryDevice, value: Int): CommandResult = withSession(
        device = device,
        commands = listOf(DpCommands.setMaxBrightness(value)),
        successMessage = "Brightness $value",
    )

    override suspend fun setRainbowScene(device: FactoryDevice): CommandResult = withSession(
        device = device,
        // 用固件 aging test 里那条 RGB 三段彩虹 groove 命令
        // (P10003 程序 + RGB 三色 + 时序 + 重复)，比 d6 scene 数据更稳。
        commands = listOf(
            DpCommands.grooveState(true),
            DpCommands.setWorkMode(2),
            DpCommands.grooveHandle(DpCommands.GROOVE_RAINBOW_RGB3),
        ),
        successMessage = "Rainbow scene set",
    )

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

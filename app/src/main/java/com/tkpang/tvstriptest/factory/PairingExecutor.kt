package com.tkpang.tvstriptest.factory

import com.tkpang.tvstriptest.model.DeviceConnectionState
import com.tkpang.tvstriptest.model.FactoryDevice
import com.tkpang.tvstriptest.model.PidFilter

sealed interface PairingOutcome {
    val address: String
    data class Paired(override val address: String, val pid: Int) : PairingOutcome
    data class PidMismatch(override val address: String, val actualPid: Int) : PairingOutcome
    data class ConnectFailed(override val address: String, val attempt: Int) : PairingOutcome
    data class BondFailed(override val address: String, val attempt: Int) : PairingOutcome
    data class PermanentlyFailed(override val address: String) : PairingOutcome
}

interface PairingExecutor {
    suspend fun pair(address: String): PairingOutcome
}

class PairingExecutorImpl(
    private val dispatcher: CommandDispatcher,
    private val pidFilter: PidFilter,
) : PairingExecutor {
    private val attempts = mutableMapOf<String, Int>()

    override suspend fun pair(address: String): PairingOutcome {
        val device = FactoryDevice(address = address, name = null, rssi = 0, state = DeviceConnectionState.Discovered)
        val result = dispatcher.connect(device)

        if (!result.success || result.deviceInfo == null) {
            val attempt = (attempts[address] ?: 0) + 1
            attempts[address] = attempt
            return if (attempt >= 3) {
                PairingOutcome.PermanentlyFailed(address)
            } else {
                PairingOutcome.ConnectFailed(address, attempt)
            }
        }

        val info = result.deviceInfo!!
        val pidOk = when (pidFilter) {
            is PidFilter.Any -> true
            is PidFilter.Specific -> info.pid == pidFilter.pid
        }
        if (!pidOk) {
            return PairingOutcome.PidMismatch(address, actualPid = info.pid)
        }

        attempts.remove(address)
        return PairingOutcome.Paired(address, pid = info.pid)
    }
}

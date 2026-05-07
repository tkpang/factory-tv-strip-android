package com.tkpang.tvstriptest.factory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tkpang.tvstriptest.ble.BleScanner
import com.tkpang.tvstriptest.model.FactoryDevice
import com.tkpang.tvstriptest.model.ScanDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WritePidViewModel(
    private val scanner: BleScanner,
    private val dispatcher: CommandDispatcher,
) : ViewModel() {

    sealed interface Phase {
        data object Idle : Phase
        data object Scanning : Phase
        data class Writing(val device: ScanDevice) : Phase
        data class Success(val pid: Int) : Phase
        data class Failed(val message: String) : Phase
    }

    private val _selectedPid = MutableStateFlow<Int?>(null)
    val selectedPid = _selectedPid.asStateFlow()

    private val _phase = MutableStateFlow<Phase>(Phase.Idle)
    val phase = _phase.asStateFlow()

    fun selectPid(pid: Int) { _selectedPid.value = pid }

    fun start() {
        val pid = _selectedPid.value ?: return
        viewModelScope.launch { runFlow(pid) }
    }

    private suspend fun runFlow(pid: Int) {
        _phase.value = Phase.Scanning
        @Suppress("MissingPermission")
        scanner.start()
        try {
            val target = waitForStrongest(timeoutMillis = 5000) ?: run {
                _phase.value = Phase.Failed("请把 1 台设备靠近后重试")
                return
            }
            _phase.value = Phase.Writing(target)
            val device = FactoryDevice(target.address, target.name)
            val connected = dispatcher.connect(device)
            if (!connected.success) {
                _phase.value = Phase.Failed("连接失败：${connected.message}")
                return
            }
            val written = dispatcher.setPid(device, pid)
            if (!written.success) {
                _phase.value = Phase.Failed("写入失败：${written.message}")
                return
            }
            val verify = dispatcher.connect(device)
            val ok = verify.success && verify.deviceInfo?.pid == pid
            _phase.value = if (ok) Phase.Success(pid) else Phase.Failed("校验失败")
        } finally {
            @Suppress("MissingPermission")
            scanner.stop()
            dispatcher.closeAll()
        }
    }

    private suspend fun waitForStrongest(timeoutMillis: Long): ScanDevice? {
        var elapsed = 0L
        while (elapsed < timeoutMillis) {
            val devices = scanner.devices.value
            if (devices.isNotEmpty()) {
                return devices.maxByOrNull { it.rssi }
            }
            delay(200)
            elapsed += 200
        }
        return null
    }

    fun reset() { _phase.value = Phase.Idle }
}

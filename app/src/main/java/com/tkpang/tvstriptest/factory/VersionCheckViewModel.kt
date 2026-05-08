package com.tkpang.tvstriptest.factory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tkpang.tvstriptest.ble.BleScanner
import com.tkpang.tvstriptest.model.FactoryDevice
import com.tkpang.tvstriptest.model.PidFilter
import com.tkpang.tvstriptest.model.ScanDevice
import com.tkpang.tvstriptest.model.SensitivityLevel
import com.tkpang.tvstriptest.protocol.BleDeviceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 「查看版本号」工具：扫描周围设备 → 用户点击一个 → 连接 + 读 DEV_INFO →
 * 显示 PID + 所有 fw_hw_info 版本（ESP32 / T23 app / T23 sys 等）。
 *
 * 不写、不解绑，只读。读完保留连接，方便用户换台时点别的 chip 立刻读。
 */
class VersionCheckViewModel(
    private val scanner: BleScanner,
    private val dispatcher: CommandDispatcher,
) : ViewModel() {

    sealed interface Phase {
        data object Scanning : Phase
        data class Reading(val address: String) : Phase
        data class Loaded(val address: String, val pid: Int, val versions: List<String>) : Phase
        data class Failed(val address: String, val message: String) : Phase
    }

    private val _phase = MutableStateFlow<Phase>(Phase.Scanning)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _sensitivity = MutableStateFlow(SensitivityLevel.NEAR)
    val sensitivity: StateFlow<SensitivityLevel> = _sensitivity.asStateFlow()

    private val _onlyUnbonded = MutableStateFlow(false)
    val onlyUnbonded: StateFlow<Boolean> = _onlyUnbonded.asStateFlow()

    private val _pidFilter = MutableStateFlow<PidFilter>(PidFilter.Any)
    val pidFilter: StateFlow<PidFilter> = _pidFilter.asStateFlow()

    val devices: StateFlow<List<ScanDevice>> = combine(
        scanner.devices, _pidFilter, _onlyUnbonded,
    ) { raw, f, only -> ScanFilterUseCase.apply(raw, f, only) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setSensitivity(level: SensitivityLevel) { _sensitivity.value = level }
    fun setOnlyUnbonded(value: Boolean) { _onlyUnbonded.value = value }
    fun setPidFilter(filter: PidFilter) { _pidFilter.value = filter }

    fun start() {
        @Suppress("MissingPermission")
        scanner.start()
        _phase.value = Phase.Scanning
    }

    fun pickAndRead(address: String) {
        if (_phase.value is Phase.Reading) return
        viewModelScope.launch {
            _phase.value = Phase.Reading(address)
            val device = FactoryDevice(address, name = null)
            val result = dispatcher.connect(device)
            val info: BleDeviceInfo? = result.deviceInfo
            if (result.success && info != null) {
                _phase.value = Phase.Loaded(
                    address = address,
                    pid = info.pid,
                    versions = info.allFirmwareVersions,
                )
                // 读完版本号后主动 unbond，让设备回到出厂未配状态。
                // 失败也只是 best-effort，不挡前台 UI 显示。
                runCatching { dispatcher.unbindAndDelete(device) }
            } else {
                _phase.value = Phase.Failed(address, message = result.message.ifBlank { "读取失败" })
            }
        }
    }

    fun backToScan() {
        _phase.value = Phase.Scanning
    }

    fun exit() {
        @Suppress("MissingPermission")
        scanner.stop()
        viewModelScope.launch { dispatcher.closeAll() }
    }
}

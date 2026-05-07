package com.tkpang.tvstriptest.factory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tkpang.tvstriptest.ble.BleScanner
import com.tkpang.tvstriptest.model.FactoryDevice
import com.tkpang.tvstriptest.model.ScanDevice
import com.tkpang.tvstriptest.model.SensitivityLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 写 PID 工具 VM。流程改成「持续扫描 + 用户在雷达上手动点选」：
 * 1. 用户选好目标 PID 后进入 Scanning 阶段（持续扫描）
 * 2. 用户点击雷达上的一台设备 → Writing → 连接 + 写入 + 校验 + 主动 unbond
 *    （unbond 让设备回到出厂未配状态，避免下一次写入因「已绑定」失败）
 * 3. 成功显示 Success，用户点「再写一台」回到 Scanning；失败回到 Scanning 允许重试
 */
class WritePidViewModel(
    private val scanner: BleScanner,
    private val dispatcher: CommandDispatcher,
) : ViewModel() {

    sealed interface Phase {
        /** 还没选 PID，或主动取消 */
        data object Idle : Phase
        /** 已选 PID，雷达持续扫描，等用户点选 */
        data object Scanning : Phase
        /** 正在对某设备执行写入 + 校验 + 解绑 */
        data class Writing(val address: String, val pid: Int) : Phase
        /** 写入校验成功，并已主动 unbond，设备回到出厂状态 */
        data class Success(val pid: Int, val address: String) : Phase
        /** 失败，附带原因 */
        data class Failed(val message: String) : Phase
    }

    private val _selectedPid = MutableStateFlow<Int?>(null)
    val selectedPid: StateFlow<Int?> = _selectedPid.asStateFlow()

    private val _phase = MutableStateFlow<Phase>(Phase.Idle)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _sensitivity = MutableStateFlow(SensitivityLevel.NEAR)
    val sensitivity: StateFlow<SensitivityLevel> = _sensitivity.asStateFlow()

    /** 雷达展示的设备：未绑定（写 PID 工具不接已绑设备）*/
    val devices: StateFlow<List<ScanDevice>> = combine(
        scanner.devices, _sensitivity,
    ) { raw, _ ->
        raw.filter { !it.isBonded }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun selectPid(pid: Int) { _selectedPid.value = pid }

    fun setSensitivity(level: SensitivityLevel) { _sensitivity.value = level }

    /** 选完 PID → 开始扫描，等待用户点选设备 */
    fun startScanning() {
        if (_selectedPid.value == null) return
        @Suppress("MissingPermission")
        scanner.start()
        _phase.value = Phase.Scanning
    }

    /** 用户在雷达点选某台设备 → 开始写入流程 */
    fun pickDeviceAndWrite(address: String) {
        val pid = _selectedPid.value ?: return
        if (_phase.value is Phase.Writing) return  // 防重复
        viewModelScope.launch { runWrite(address, pid) }
    }

    private suspend fun runWrite(address: String, pid: Int) {
        _phase.value = Phase.Writing(address, pid)
        val device = FactoryDevice(address, name = null)
        try {
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
            // 校验：再次读 DEV_INFO 看 PID 是否真的写入
            val verify = dispatcher.connect(device)
            val verifyOk = verify.success && verify.deviceInfo?.pid == pid
            if (!verifyOk) {
                _phase.value = Phase.Failed("校验失败：PID 未生效")
                return
            }
            // 主动 unbond，让设备回到出厂未配状态，便于下次再写或正式产线绑定
            val unbound = dispatcher.unbindAndDelete(device)
            if (!unbound.success) {
                _phase.value = Phase.Failed("写入成功但解绑失败：${unbound.message}")
                return
            }
            _phase.value = Phase.Success(pid = pid, address = address)
        } finally {
            dispatcher.closeAll()
        }
    }

    /** 「再写一台」/「重试」→ 回到 Scanning（扫描器仍在运行） */
    fun continueScanning() {
        _phase.value = Phase.Scanning
        @Suppress("MissingPermission")
        scanner.start()
    }

    /** 退出写 PID 模式：停扫 + 重置 */
    fun exit() {
        @Suppress("MissingPermission")
        scanner.stop()
        _phase.value = Phase.Idle
    }
}

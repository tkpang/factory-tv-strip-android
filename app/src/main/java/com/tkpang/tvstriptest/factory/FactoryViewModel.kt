package com.tkpang.tvstriptest.factory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tkpang.tvstriptest.ble.BleScanner
import com.tkpang.tvstriptest.model.ErrorBanner
import com.tkpang.tvstriptest.model.FactoryDevice
import com.tkpang.tvstriptest.model.FactorySettings
import com.tkpang.tvstriptest.model.FactoryUiState
import com.tkpang.tvstriptest.model.PidFilter
import com.tkpang.tvstriptest.model.ScanDevice
import com.tkpang.tvstriptest.model.SensitivityLevel
import com.tkpang.tvstriptest.model.WizardStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FactoryViewModel(
    private val scanner: BleScanner,
    private val dispatcher: CommandDispatcher,
) : ViewModel() {
    private val _settings = MutableStateFlow(FactorySettings())
    private val _step = MutableStateFlow(WizardStep.PRODUCT)
    private val _pairedDevices = MutableStateFlow<List<FactoryDevice>>(emptyList())
    private val _pairingMessage = MutableStateFlow<String?>(null)
    private val _colorTestResult = MutableStateFlow<ColorTestUseCase.Result?>(null)
    private val _unbindProgress = MutableStateFlow<UnbindUseCase.Progress?>(null)
    private val _errorBanner = MutableStateFlow<ErrorBanner?>(null)

    // 雷达上展示的设备：原始扫描结果按 step1 配置（PID 筛选 + 仅未绑定）过滤，
    // 再剔除已经配对的，避免重复点击。
    private val filteredDevices: StateFlow<List<ScanDevice>> = combine(
        scanner.devices, _settings, _pairedDevices,
    ) { raw, s, paired ->
        val pairedAddrs = paired.map { it.address }.toSet()
        ScanFilterUseCase.apply(raw, s.pidFilter, s.onlyUnbonded)
            .filterNot { it.address in pairedAddrs }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val uiState: StateFlow<FactoryUiState> = run {
        val groupA = combine(_step, _settings, filteredDevices, _pairedDevices) {
            a, b, c, d -> listOf<Any?>(a, b, c, d)
        }
        val groupB = combine(_pairingMessage, _colorTestResult, _unbindProgress, _errorBanner) {
            e, f, g, h -> listOf<Any?>(e, f, g, h)
        }
        combine(groupA, groupB) { a, b ->
            @Suppress("UNCHECKED_CAST")
            FactoryUiState(
                step = a[0] as WizardStep,
                settings = a[1] as FactorySettings,
                visibleDevices = a[2] as List<ScanDevice>,
                pairedDevices = a[3] as List<FactoryDevice>,
                pairingMessage = b[0] as String?,
                colorTestResult = b[1] as ColorTestUseCase.Result?,
                unbindProgress = b[2] as UnbindUseCase.Progress?,
                errorBanner = b[3] as ErrorBanner?,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, FactoryUiState())
    }

    private val pairingInFlight = MutableStateFlow(false)

    /** 用户在雷达上点击设备 → 手动触发配对。已配/进行中时忽略。 */
    fun pairDevice(address: String) {
        if (pairingInFlight.value) {
            _pairingMessage.value = "正在处理上一台设备…"
            return
        }
        if (_pairedDevices.value.any { it.address == address }) return
        viewModelScope.launch {
            pairingInFlight.value = true
            try {
                val executor = PairingExecutorImpl(dispatcher, _settings.value.pidFilter)
                when (val outcome = executor.pair(address)) {
                    is PairingOutcome.Paired -> {
                        val device = FactoryDevice(address, name = null)
                        _pairedDevices.value = _pairedDevices.value + device
                        _pairingMessage.value = "✓ ${address.takeLast(5)} 已配上"
                        // 配对成功后给设备点一个彩虹色（情景模式 + RGB 跳变），
                        // 工厂工人远距离也能立刻看到「这台被配上了」。下发失败不影响配对结果。
                        runCatching { dispatcher.setRainbowScene(device) }
                    }
                    is PairingOutcome.PidMismatch -> {
                        _pairingMessage.value = "⚠ ${address.takeLast(5)} PID 不符（实际 ${outcome.actualPid}）"
                    }
                    is PairingOutcome.ConnectFailed -> {
                        _pairingMessage.value = "✗ ${address.takeLast(5)} 连接失败，请重试"
                    }
                    is PairingOutcome.BondFailed -> {
                        _pairingMessage.value = "✗ ${address.takeLast(5)} 绑定失败，请重试"
                    }
                    is PairingOutcome.PermanentlyFailed -> {
                        _pairingMessage.value = "✗ ${address.takeLast(5)} 多次失败，已跳过"
                    }
                }
            } finally {
                pairingInFlight.value = false
            }
        }
    }

    // === Step navigation ===
    fun goToStep(step: WizardStep) { _step.value = step }
    fun next() { WizardStep.values().getOrNull(_step.value.index + 1)?.let { _step.value = it } }
    fun back() { WizardStep.values().getOrNull(_step.value.index - 1)?.let { _step.value = it } }
    fun resetToFirstStep() {
        _step.value = WizardStep.PRODUCT
        _pairedDevices.value = emptyList()
        _colorTestResult.value = null
        _unbindProgress.value = null
        _pairingMessage.value = null
    }

    // === Step 1 ===
    fun setProductType(devName: String) {
        _settings.update { it.copy(productDevName = devName) }
    }
    fun setPidFilter(filter: PidFilter) {
        _settings.update { it.copy(pidFilter = filter) }
    }
    fun setOnlyUnbonded(value: Boolean) {
        _settings.update { it.copy(onlyUnbonded = value) }
    }

    // === Step 2 ===
    fun setSensitivity(level: SensitivityLevel) {
        _settings.update { it.copy(sensitivity = level) }
    }
    fun startScan() {
        @Suppress("MissingPermission")
        if (!scanner.start()) {
            _errorBanner.value = ErrorBanner.MissingPermission
        } else {
            _errorBanner.value = null
        }
    }
    fun stopScan() {
        @Suppress("MissingPermission")
        scanner.stop()
    }

    // === Step 3 ===
    fun runColorTest(command: ColorTestUseCase.Command) {
        viewModelScope.launch {
            val result = ColorTestUseCase(dispatcher).execute(_pairedDevices.value, command)
            _colorTestResult.value = result
        }
    }

    // === Step 4 ===
    fun runUnbind() {
        viewModelScope.launch {
            UnbindUseCase(dispatcher).execute(_pairedDevices.value).collect {
                _unbindProgress.value = it
            }
        }
    }
}

private fun MutableStateFlow<FactorySettings>.update(transform: (FactorySettings) -> FactorySettings) {
    value = transform(value)
}

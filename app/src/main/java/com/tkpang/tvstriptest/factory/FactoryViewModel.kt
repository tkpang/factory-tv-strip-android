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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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

    private val sensitivityFlow: StateFlow<SensitivityLevel> =
        _settings.map { it.sensitivity }.stateIn(
            viewModelScope, SharingStarted.Eagerly, SensitivityLevel.NEAR,
        )

    private val filteredDevices: StateFlow<List<ScanDevice>> = combine(
        scanner.devices, _settings,
    ) { raw, s -> ScanFilterUseCase.apply(raw, s.pidFilter, s.onlyUnbonded) }.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList(),
    )

    private val radarStateMachine = RadarStateMachine(
        sensitivity = sensitivityFlow,
        source = filteredDevices,
        scope = viewModelScope,
    )

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

    init {
        viewModelScope.launch {
            radarStateMachine.pairingTriggers.collect { addr -> handlePairingTrigger(addr) }
        }
    }

    private suspend fun handlePairingTrigger(addr: String) {
        val executor = PairingExecutorImpl(dispatcher, _settings.value.pidFilter)
        val outcome = executor.pair(addr)
        when (outcome) {
            is PairingOutcome.Paired -> {
                _pairedDevices.value = _pairedDevices.value + FactoryDevice(addr, name = null)
                _pairingMessage.value = "✓ ${addr.takeLast(4)} 已配上"
                radarStateMachine.markHandled(addr)
            }
            is PairingOutcome.PidMismatch -> {
                _pairingMessage.value = "⚠ ${addr.takeLast(4)} PID 不符 · 跳过"
                radarStateMachine.markHandled(addr)
            }
            is PairingOutcome.PermanentlyFailed -> {
                radarStateMachine.markHandled(addr)
            }
            is PairingOutcome.ConnectFailed,
            is PairingOutcome.BondFailed -> {
                viewModelScope.launch {
                    delay(2000)
                    radarStateMachine.resetDevice(addr)
                }
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

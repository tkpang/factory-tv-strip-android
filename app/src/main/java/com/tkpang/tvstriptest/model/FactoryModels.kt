package com.tkpang.tvstriptest.model

import com.tkpang.tvstriptest.factory.ColorTestUseCase
import com.tkpang.tvstriptest.factory.UnbindUseCase

enum class DeviceConnectionState { Discovered, Connecting, Connected, Ready, Failed, Unbound }

data class ScanDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    val pid: Int? = null,
    val isBonded: Boolean = false,
    val pairingState: PairingState = PairingState.DETECTED,
    // Legacy fields — kept for backward compatibility
    @Deprecated("Superseded by pairingState logic in use-cases")
    val autoSelected: Boolean = false,
    @Deprecated("Superseded by pairingState logic in use-cases")
    val selected: Boolean = false,
)

data class FactoryDevice(
    val address: String,
    val name: String?,
    val rssi: Int = 0,
    val state: DeviceConnectionState = DeviceConnectionState.Discovered,
    val did: Long? = null,
    val pid: Int? = null,
    val firmwareVersion: String? = null,
    val lastResult: String = "",
    val lastError: String = "",
)

data class FactorySettings(
    val productDevName: String = "STV1",
    val pidFilter: PidFilter = PidFilter.Any,
    // 默认关：很多工厂测试场景的设备已经走过产线绑定（广播 0xA0），
    // 默认开启会让用户以为「扫不到设备」。需要时手动打开。
    val onlyUnbonded: Boolean = false,
    val sensitivity: SensitivityLevel = SensitivityLevel.NEAR,
)

enum class WizardStep(val index: Int, val displayName: String) {
    PRODUCT(0, "选条件"),
    SCAN(1, "扫描配对"),
    COLOR(2, "颜色测试"),
    UNBIND(3, "解绑"),
}

sealed interface ErrorBanner {
    data object BluetoothOff : ErrorBanner
    data object MissingPermission : ErrorBanner
}

data class FactoryUiState(
    val step: WizardStep = WizardStep.PRODUCT,
    val settings: FactorySettings = FactorySettings(),
    val visibleDevices: List<ScanDevice> = emptyList(),
    val pairedDevices: List<FactoryDevice> = emptyList(),
    val pairingMessage: String? = null,
    val colorTestResult: ColorTestUseCase.Result? = null,
    val unbindProgress: UnbindUseCase.Progress? = null,
    val errorBanner: ErrorBanner? = null,
)

package com.tkpang.tvstriptest.factory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tkpang.tvstriptest.model.DeviceConnectionState
import com.tkpang.tvstriptest.model.FactoryDevice
import com.tkpang.tvstriptest.model.FactorySettings
import com.tkpang.tvstriptest.model.ProductCatalog
import com.tkpang.tvstriptest.model.ScanDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicBoolean

interface FactoryScanner {
    val devices: StateFlow<List<ScanDevice>>
    fun start(targetCount: Int, rssiThreshold: Int): Boolean
    fun stop()
    fun setSelected(address: String, selected: Boolean)
}

data class FactoryUiState(
    val settings: FactorySettings = FactorySettings(),
    val scanDevices: List<ScanDevice> = emptyList(),
    val devices: List<FactoryDevice> = emptyList(),
    val isScanning: Boolean = false,
    val isBusy: Boolean = false,
    val statusMessage: String = "Ready",
) {
    val selectedCount: Int get() = scanDevices.count { it.selected }
    val connectedCount: Int get() = devices.count { it.state == DeviceConnectionState.Connected || it.state == DeviceConnectionState.Ready }
}

class FactoryViewModel(
    private val scanner: FactoryScanner? = null,
    private val dispatcher: CommandDispatcher = PerDeviceBleDispatcher(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(FactoryUiState())
    val uiState: StateFlow<FactoryUiState> = _uiState.asStateFlow()
    private val operationBusy = AtomicBoolean(false)

    init {
        scanner?.let { factoryScanner ->
            viewModelScope.launch {
                factoryScanner.devices.collect { scanDevices ->
                    _uiState.update { state ->
                        state.copy(
                            scanDevices = scanDevices,
                            devices = mergeScanDevices(state.devices, scanDevices),
                            statusMessage = if (scanDevices.isEmpty()) state.statusMessage else "Found ${scanDevices.size} devices",
                        )
                    }
                }
            }
        }
    }

    fun setProductType(devName: String) {
        val product = ProductCatalog.productTypes.firstOrNull { it.devName == devName } ?: return
        val nextPid = product.pidOptions.firstOrNull()?.pid ?: _uiState.value.settings.pid
        _uiState.update { state ->
            state.copy(settings = state.settings.copy(productDevName = devName, pid = nextPid))
        }
    }

    fun setPid(pid: Int) {
        _uiState.update { state -> state.copy(settings = state.settings.copy(pid = pid)) }
    }

    fun setRssiThreshold(value: Int) {
        _uiState.update { state -> state.copy(settings = state.settings.copy(rssiThreshold = value)) }
    }

    fun setTargetDeviceCount(value: Int) {
        _uiState.update { state ->
            state.copy(settings = state.settings.copy(targetDeviceCount = value.coerceAtLeast(1)))
        }
    }

    fun setMaxPowerColor(rgb: Int) {
        _uiState.update { state -> state.copy(settings = state.settings.copy(maxPowerColor = rgb.coerceIn(0x000000, 0xFFFFFF))) }
    }

    fun startScan() {
        val state = _uiState.value
        val started = scanner?.start(
            targetCount = state.settings.targetDeviceCount,
            rssiThreshold = state.settings.rssiThreshold,
        ) ?: false
        _uiState.update {
            it.copy(
                isScanning = started,
                statusMessage = if (started) "Scanning" else "Scan unavailable or permission missing",
            )
        }
    }

    fun stopScan() {
        scanner?.stop()
        _uiState.update { it.copy(isScanning = false, statusMessage = "Scan stopped") }
    }

    fun setSelected(address: String, selected: Boolean) {
        scanner?.setSelected(address, selected)
        _uiState.update { state ->
            state.copy(scanDevices = state.scanDevices.map {
                if (it.address == address) it.copy(selected = selected) else it
            })
        }
    }

    fun connectSelected() {
        runSelected("Connecting", DeviceConnectionState.Connecting) { device, _ -> dispatcher.connect(device) }
    }

    fun setLightPid() {
        runSelected("Setting PID", DeviceConnectionState.Connected) { device, settings ->
            dispatcher.setPid(device, settings)
        }
    }

    fun setColor(rgb: Int) {
        runSelected("Setting color", DeviceConnectionState.Connected) { device, settings ->
            dispatcher.setColor(device, settings, rgb)
        }
    }

    fun setHighestPowerColor() {
        runSelected("Setting highest power color", DeviceConnectionState.Connected) { device, settings ->
            dispatcher.setHighestPowerColor(device, settings)
        }
    }

    fun unbindAll() {
        val targetAddresses = _uiState.value.devices
            .filter { it.state == DeviceConnectionState.Connected || it.state == DeviceConnectionState.Ready }
            .map { it.address }
            .toSet()
        runDevices("Unbinding", targetAddresses, DeviceConnectionState.Connected) { device, _ ->
            dispatcher.unbindAndDelete(device)
        }
    }

    private fun runSelected(
        busyMessage: String,
        workingState: DeviceConnectionState,
        action: suspend (FactoryDevice, FactorySettings) -> CommandResult,
    ) {
        val targetAddresses = _uiState.value.scanDevices.filter { it.selected }.map { it.address }.toSet()
        runDevices(busyMessage, targetAddresses, workingState, action)
    }

    private fun runDevices(
        busyMessage: String,
        targetAddresses: Set<String>,
        workingState: DeviceConnectionState,
        action: suspend (FactoryDevice, FactorySettings) -> CommandResult,
    ) {
        if (targetAddresses.isEmpty()) {
            _uiState.update { it.copy(statusMessage = "No target devices") }
            return
        }
        if (!operationBusy.compareAndSet(false, true)) {
            _uiState.update { it.copy(statusMessage = "Busy") }
            return
        }

        _uiState.update { state ->
            state.copy(
                isBusy = true,
                statusMessage = busyMessage,
                devices = state.devices.map { device ->
                    if (device.address in targetAddresses) device.copy(state = workingState) else device
                },
            )
        }

        viewModelScope.launch {
            try {
                val settings = _uiState.value.settings
                val selectedDevices = _uiState.value.devices.filter { it.address in targetAddresses }
                val semaphore = Semaphore(MAX_CONCURRENT_DEVICE_TASKS)
                selectedDevices.map { device ->
                    async {
                        val result = semaphore.withPermit { action(device, settings) }
                        _uiState.update { state -> state.copy(devices = updateDeviceAfterResult(state.devices, device, result)) }
                    }
                }.awaitAll()

                _uiState.update { state -> state.copy(isBusy = false, statusMessage = summarizeSelected(state.devices, targetAddresses)) }
            } finally {
                operationBusy.set(false)
                _uiState.update { state -> if (state.isBusy) state.copy(isBusy = false) else state }
            }
        }
    }

    private fun mergeScanDevices(
        existingDevices: List<FactoryDevice>,
        scanDevices: List<ScanDevice>,
    ): List<FactoryDevice> {
        val existingByAddress = existingDevices.associateBy { it.address }
        return scanDevices.map { scanDevice ->
            val existing = existingByAddress[scanDevice.address]
            FactoryDevice(
                address = scanDevice.address,
                name = scanDevice.name,
                rssi = scanDevice.rssi,
                state = existing?.state ?: DeviceConnectionState.Discovered,
                did = existing?.did,
                pid = existing?.pid,
                firmwareVersion = existing?.firmwareVersion,
                lastResult = existing?.lastResult.orEmpty(),
                lastError = existing?.lastError.orEmpty(),
            )
        }
    }

    private fun updateDeviceAfterResult(
        devices: List<FactoryDevice>,
        device: FactoryDevice,
        result: CommandResult,
    ): List<FactoryDevice> = devices.map { current ->
        if (current.address != device.address) return@map current
        val nextState = when {
            !result.success -> DeviceConnectionState.Failed
            result.message == "Unbound and deleted" -> DeviceConnectionState.Unbound
            else -> DeviceConnectionState.Ready
        }
        current.copy(
            state = nextState,
            did = result.deviceInfo?.did ?: current.did,
            pid = result.deviceInfo?.pid ?: current.pid,
            firmwareVersion = result.deviceInfo?.firmwareVersion ?: current.firmwareVersion,
            lastResult = if (result.success) result.message else current.lastResult,
            lastError = if (result.success) "" else result.message,
        )
    }

    private fun summarizeSelected(devices: List<FactoryDevice>, selectedAddresses: Set<String>): String {
        val selected = devices.filter { it.address in selectedAddresses }
        val failed = selected.count { it.state == DeviceConnectionState.Failed }
        return if (failed == 0) "Completed ${selected.size} devices" else "Completed with $failed failures"
    }

    private companion object {
        const val MAX_CONCURRENT_DEVICE_TASKS = 3
    }
}

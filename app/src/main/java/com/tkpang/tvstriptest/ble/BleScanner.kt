package com.tkpang.tvstriptest.ble

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import com.tkpang.tvstriptest.model.ScanDevice
import com.tkpang.tvstriptest.protocol.LeConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BleScanner(
    private val context: Context,
) {
    private data class Candidate(
        val address: String,
        val name: String?,
        val rssi: Int,
    )

    private val bluetoothAdapter =
        context.getSystemService(BluetoothManager::class.java)?.adapter
    private val lock = Any()
    private val candidates = linkedMapOf<String, Candidate>()
    private val manualSelections = mutableMapOf<String, Boolean>()
    private val _devices = MutableStateFlow<List<ScanDevice>>(emptyList())

    val devices: StateFlow<List<ScanDevice>> = _devices.asStateFlow()

    private var rssiThreshold: Int = DEFAULT_RSSI_THRESHOLD
    private var targetCount: Int = DEFAULT_TARGET_COUNT
    private var scanning = false

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::handleResult)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun start(
        targetCount: Int,
        rssiThreshold: Int,
    ): Boolean {
        if (!hasScanPermission()) return false
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return false

        synchronized(lock) {
            this.targetCount = targetCount.coerceAtLeast(0)
            this.rssiThreshold = rssiThreshold
            candidates.clear()
            manualSelections.clear()
            publishDevicesLocked()
        }

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(LeConstants.SERVICE_UUID))
                .build(),
            ScanFilter.Builder()
                .setDeviceName(LeConstants.ADV_NAME)
                .build(),
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(filters, settings, scanCallback)
        synchronized(lock) {
            scanning = true
        }
        return true
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stop() {
        val shouldStop = synchronized(lock) { scanning }
        if (!shouldStop || !hasScanPermission()) return
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        synchronized(lock) {
            scanning = false
        }
    }

    fun setSelected(address: String, selected: Boolean) {
        synchronized(lock) {
            manualSelections[address] = selected
            publishDevicesLocked()
        }
    }

    fun clearManualSelection(address: String) {
        synchronized(lock) {
            manualSelections.remove(address)
            publishDevicesLocked()
        }
    }

    private fun handleResult(result: ScanResult) {
        val name = result.scanRecord?.deviceName
        val serviceUuids = result.scanRecord?.serviceUuids.orEmpty()
        val matchesService = serviceUuids.any { it.uuid == LeConstants.SERVICE_UUID }
        val matchesName = name == LeConstants.ADV_NAME

        if (!matchesService && !matchesName) return

        val address = result.device.address ?: return
        synchronized(lock) {
            candidates[address] = Candidate(
                address = address,
                name = name,
                rssi = result.rssi,
            )
            publishDevicesLocked()
        }
    }

    private fun publishDevicesLocked() {
        val sorted = candidates.values.sortedByDescending { it.rssi }
        val autoSelectedAddresses = sorted
            .asSequence()
            .filter { it.rssi >= rssiThreshold }
            .take(targetCount)
            .map { it.address }
            .toSet()

        _devices.value = sorted.map { candidate ->
            val autoSelected = candidate.address in autoSelectedAddresses
            ScanDevice(
                address = candidate.address,
                name = candidate.name,
                rssi = candidate.rssi,
                autoSelected = autoSelected,
                selected = manualSelections[candidate.address] ?: autoSelected,
            )
        }
    }

    private fun hasScanPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val DEFAULT_RSSI_THRESHOLD = -65
        private const val DEFAULT_TARGET_COUNT = 1
    }
}

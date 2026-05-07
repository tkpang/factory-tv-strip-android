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
import com.tkpang.tvstriptest.model.PairingState
import com.tkpang.tvstriptest.model.ScanDevice
import com.tkpang.tvstriptest.protocol.AdvDataParser
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
        val pid: Int?,
        val isBonded: Boolean,
    )

    private val bluetoothAdapter =
        context.getSystemService(BluetoothManager::class.java)?.adapter
    private val lock = Any()
    private val candidates = linkedMapOf<String, Candidate>()
    private val _devices = MutableStateFlow<List<ScanDevice>>(emptyList())

    val devices: StateFlow<List<ScanDevice>> = _devices.asStateFlow()

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
    fun start(): Boolean {
        if (!hasScanPermission()) return false
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return false

        synchronized(lock) {
            candidates.clear()
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
        synchronized(lock) { scanning = true }
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

    private fun handleResult(result: ScanResult) {
        val name = result.scanRecord?.deviceName
        val serviceUuids = result.scanRecord?.serviceUuids.orEmpty()
        val matchesService = serviceUuids.any { it.uuid == LeConstants.SERVICE_UUID }
        val matchesName = name == LeConstants.ADV_NAME

        if (!matchesService && !matchesName) return

        val address = result.device.address ?: return

        val mfgArray: ByteArray? = result.scanRecord?.manufacturerSpecificData?.let { sparse ->
            if (sparse.size() == 0) null else sparse.valueAt(0)
        }
        val parsed = mfgArray?.let { AdvDataParser.parse(it) }

        synchronized(lock) {
            candidates[address] = Candidate(
                address = address,
                name = name,
                rssi = result.rssi,
                pid = parsed?.pid,
                isBonded = parsed?.isBonded ?: false,
            )
            publishDevicesLocked()
        }
    }

    private fun publishDevicesLocked() {
        _devices.value = candidates.values.sortedByDescending { it.rssi }.map { c ->
            ScanDevice(
                address = c.address,
                name = c.name,
                rssi = c.rssi,
                pid = c.pid,
                isBonded = c.isBonded,
                pairingState = PairingState.DETECTED,
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
}

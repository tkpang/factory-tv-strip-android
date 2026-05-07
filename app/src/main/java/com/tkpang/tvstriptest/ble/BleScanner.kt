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

        // 固件把 'L''P' 当成 manufacturer ID 写到广播包里（LE 序：0x4C 0x50 → ID = 0x504C），
        // Android 解析时会把这两个字节作为 key，剩下的 13 字节才是 SparseArray 的 value。
        // 所以这里要么按 ID 直接查 0x504C，要么遍历所有 entry 找一段长度对得上的。
        val parsed: AdvDataParser.Parsed? = run {
            val sparse = result.scanRecord?.manufacturerSpecificData ?: return@run null
            val direct = sparse.get(LP_MFG_ID)
            if (direct != null) {
                AdvDataParser.parseStripped(direct)
            } else {
                // 兼容路径：有的 stack 不解析 mfg ID 而是把整段塞回来；尝试按完整布局解析。
                var found: AdvDataParser.Parsed? = null
                for (i in 0 until sparse.size()) {
                    val v = sparse.valueAt(i) ?: continue
                    found = AdvDataParser.parse(v)
                    if (found != null) break
                }
                found
            }
        }

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

    private companion object {
        // 'L'(0x4C) + 'P'(0x50) 在小端 uint16 中为 0x504C
        const val LP_MFG_ID = 0x504C
    }
}

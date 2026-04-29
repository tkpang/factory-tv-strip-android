package com.tkpang.tvstriptest.ble

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresPermission
import com.tkpang.tvstriptest.protocol.LeConstants
import com.tkpang.tvstriptest.protocol.BleCrypto
import com.tkpang.tvstriptest.protocol.BleDeviceInfo
import com.tkpang.tvstriptest.protocol.LeMessage
import com.tkpang.tvstriptest.protocol.LeMessageCodec
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

class BleDeviceSession(
    private val context: Context,
    private val device: BluetoothDevice,
) {
    enum class State {
        Idle,
        Connecting,
        Discovering,
        Ready,
        Failed,
        Closed,
    }

    private val _state = MutableStateFlow(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _notifications = MutableStateFlow<ByteArray?>(null)
    val notifications: StateFlow<ByteArray?> = _notifications.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    private var pendingNotifyDescriptor: BluetoothGattDescriptor? = null
    private val writeMutex = Mutex()
    private val pendingWriteLock = Any()
    private val pendingResponses = mutableMapOf<ResponseKey, CompletableDeferred<LeMessage>>()
    private val randomSource = SecureRandom()
    private var pendingWrite: CompletableDeferred<Boolean>? = null
    private var crypto: BleCrypto? = null
    var deviceInfo: BleDeviceInfo? = null
        private set

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.value = State.Failed
                closeGatt()
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _state.value = State.Discovering
                    if (!hasConnectPermission() || !gatt.discoverServices()) {
                        _state.value = State.Failed
                        closeGatt()
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    _state.value = State.Closed
                    closeGatt()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failAndClose()
                return
            }

            val service = gatt.getService(LeConstants.SERVICE_UUID)
            writeCharacteristic = service?.getCharacteristic(LeConstants.WRITE_UUID)
            notifyCharacteristic = service?.getCharacteristic(LeConstants.NOTIFY_UUID)

            if (writeCharacteristic == null || notifyCharacteristic == null || !subscribeToNotify()) {
                failAndClose()
                return
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (descriptor.uuid != CCCD_UUID || descriptor != pendingNotifyDescriptor) return

            pendingNotifyDescriptor = null
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _state.value = State.Ready
            } else {
                failAndClose()
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (characteristic.uuid != LeConstants.WRITE_UUID) return
            completePendingWrite(status == BluetoothGatt.GATT_SUCCESS)
        }

        @Deprecated("Deprecated in Android 13")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (characteristic.uuid == LeConstants.NOTIFY_UUID) {
                handleNotification(characteristic.value)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (characteristic.uuid == LeConstants.NOTIFY_UUID) {
                handleNotification(value)
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(): Boolean {
        if (!hasConnectPermission() || _state.value == State.Connecting) return false
        _state.value = State.Connecting
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        if (gatt == null) {
            _state.value = State.Failed
            return false
        }
        return true
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun write(payload: ByteArray): Boolean = writeMutex.withLock {
        if (!hasConnectPermission()) return false
        val currentGatt = gatt ?: return false
        val characteristic = writeCharacteristic ?: return false
        val completion = CompletableDeferred<Boolean>()

        val alreadyPending = synchronized(pendingWriteLock) {
            if (pendingWrite != null) {
                true
            } else {
                pendingWrite = completion
                false
            }
        }
        if (alreadyPending) {
            return false
        }

        val writeStarted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            currentGatt.writeCharacteristic(
                characteristic,
                payload,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
            ) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = payload
            @Suppress("DEPRECATION")
            currentGatt.writeCharacteristic(characteristic)
        }

        if (!writeStarted) {
            clearPendingWrite(completion)
            return false
        }

        val completed = withTimeoutOrNull(WRITE_TIMEOUT_MS) { completion.await() } ?: false
        clearPendingWrite(completion)
        completed
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun handshake(mtu: Int = DEFAULT_MTU): Boolean {
        val random = randomSource.nextInt()
        crypto = BleCrypto.fromMac(device.address, random)
        val devInfoPayload = LeMessageCodec.devInfoGetPayload(
            mtu = mtu,
            random = random,
            utcSeconds = (System.currentTimeMillis() / 1000L).toInt(),
        )
        val devInfo = transactMessage(
            cmd = LeConstants.LE_CMD_DEV_INFO_GET,
            sn = nextSn(),
            payload = devInfoPayload,
            expectedResponseCmd = LeConstants.LE_CMD_DEV_INFO_GETR,
        ) ?: return false
        deviceInfo = LeMessageCodec.parseDevInfo(devInfo.payload)

        val bond = transactMessage(
            cmd = LeConstants.LE_CMD_BOND,
            sn = nextSn(),
            payload = LeMessageCodec.bondPayload(),
            expectedResponseCmd = LeConstants.LE_CMD_BONDR,
        ) ?: return false
        return LeMessageCodec.responseCode(bond.payload) in setOf(
            LeConstants.LE_CODE_SUCCESS,
            LeConstants.LE_CODE_BONDED,
        )
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun transact(cmd: Int, sn: Int, payload: ByteArray, expectedResponseCmd: Int): Boolean {
        val response = transactMessage(cmd, sn, payload, expectedResponseCmd) ?: return false
        return LeMessageCodec.responseCode(response.payload) == LeConstants.LE_CODE_SUCCESS
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun close() {
        _state.value = State.Closed
        closeGatt()
    }

    private fun subscribeToNotify(): Boolean {
        if (!hasConnectPermission()) return false
        val currentGatt = gatt ?: return false
        val characteristic = notifyCharacteristic ?: return false
        if (!currentGatt.setCharacteristicNotification(characteristic, true)) return false

        val descriptor = characteristic.getDescriptor(CCCD_UUID) ?: return false
        pendingNotifyDescriptor = descriptor
        val writeStarted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            currentGatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
                BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            currentGatt.writeDescriptor(descriptor)
        }
        if (!writeStarted) pendingNotifyDescriptor = null
        return writeStarted
    }

    private fun failAndClose() {
        _state.value = State.Failed
        closeGatt()
    }

    private fun closeGatt() {
        completePendingWrite(false)
        failPendingResponses()
        if (hasConnectPermission()) {
            gatt?.close()
        }
        gatt = null
        writeCharacteristic = null
        notifyCharacteristic = null
        pendingNotifyDescriptor = null
    }

    private fun completePendingWrite(success: Boolean) {
        synchronized(pendingWriteLock) {
            pendingWrite?.complete(success)
        }
    }

    private suspend fun transactMessage(
        cmd: Int,
        sn: Int,
        payload: ByteArray,
        expectedResponseCmd: Int,
    ): LeMessage? {
        val currentCrypto = crypto ?: return null
        val response = CompletableDeferred<LeMessage>()
        val key = ResponseKey(expectedResponseCmd, sn)
        synchronized(pendingResponses) { pendingResponses[key] = response }
        val written = write(LeMessageCodec.encodeEncrypted(cmd, sn, payload, currentCrypto))
        if (!written) {
            synchronized(pendingResponses) { pendingResponses.remove(key) }
            return null
        }
        return runCatching { withTimeoutOrNull(RESPONSE_TIMEOUT_MS) { response.await() } }.getOrNull()
            .also { synchronized(pendingResponses) { pendingResponses.remove(key) } }
    }

    private fun failPendingResponses() {
        val pending = synchronized(pendingResponses) {
            pendingResponses.values.toList().also { pendingResponses.clear() }
        }
        pending.forEach { it.completeExceptionally(CancellationException("BLE session closed")) }
    }

    private fun handleNotification(value: ByteArray) {
        _notifications.value = value
        val currentCrypto = crypto ?: return
        val message = runCatching { LeMessageCodec.decodeEncrypted(value, currentCrypto) }.getOrNull() ?: return
        val completion = synchronized(pendingResponses) {
            pendingResponses[ResponseKey(message.cmd, message.sn)]
        }
        completion?.complete(message)
    }

    private fun nextSn(): Int = (randomSource.nextInt(0xFFFE) + 1) and 0xFFFF

    private fun clearPendingWrite(completion: CompletableDeferred<Boolean>) {
        synchronized(pendingWriteLock) {
            if (pendingWrite == completion) pendingWrite = null
        }
    }

    private fun hasConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        const val WRITE_TIMEOUT_MS = 10_000L
        const val RESPONSE_TIMEOUT_MS = 10_000L
        const val DEFAULT_MTU = 247
    }

    private data class ResponseKey(val cmd: Int, val sn: Int)
}

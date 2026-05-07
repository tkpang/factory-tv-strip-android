package com.tkpang.tvstriptest

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tkpang.tvstriptest.ble.BleDeviceSession
import com.tkpang.tvstriptest.ble.BleScanner
import com.tkpang.tvstriptest.factory.DeviceCommandSession
import com.tkpang.tvstriptest.factory.FactoryViewModel
import com.tkpang.tvstriptest.factory.PerDeviceBleDispatcher
import com.tkpang.tvstriptest.protocol.BleDeviceInfo
import com.tkpang.tvstriptest.protocol.LeMessageCodec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {
    private val viewModel: FactoryViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val scanner = BleScanner(this@MainActivity)
                val dispatcher = PerDeviceBleDispatcher { device ->
                    AndroidDeviceCommandSession.create(this@MainActivity, device.address)
                }
                return FactoryViewModel(scanner, dispatcher) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestFactoryPermissions()
        setContent {
            MaterialTheme {
                Surface {
                    // TODO Task 22: replace with wizard UI
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Wizard UI — Task 22")
                    }
                }
            }
        }
    }

    private fun requestFactoryPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), REQUEST_FACTORY_PERMISSIONS)
        }
    }

    private companion object {
        const val REQUEST_FACTORY_PERMISSIONS = 1001
    }
}

private class AndroidDeviceCommandSession(
    context: Context,
    device: android.bluetooth.BluetoothDevice,
) : DeviceCommandSession {
    private val session = BleDeviceSession(
        context = context,
        device = device,
    )

    override val deviceInfo: BleDeviceInfo?
        get() = session.deviceInfo

    @Suppress("MissingPermission")
    override suspend fun connect(): Boolean {
        if (!session.connect()) return false
        val ready = withTimeoutOrNull(10_000) {
            session.state.first { state ->
                state == BleDeviceSession.State.Ready ||
                    state == BleDeviceSession.State.Failed ||
                    state == BleDeviceSession.State.Closed
            } == BleDeviceSession.State.Ready
        } ?: false
        return ready && session.handshake()
    }

    @Suppress("MissingPermission")
    override suspend fun write(payload: ByteArray): Boolean {
        val message = LeMessageCodec.decode(payload)
        return session.transact(message.cmd, message.sn, message.payload, expectedResponseCmd = message.cmd + 1)
    }

    @Suppress("MissingPermission")
    override suspend fun transact(payload: ByteArray, expectedResponseCmd: Int, timeoutMillis: Long): Boolean {
        val message = LeMessageCodec.decode(payload)
        return session.transact(message.cmd, message.sn, message.payload, expectedResponseCmd)
    }

    @Suppress("MissingPermission")
    override suspend fun close() = session.close()

    companion object {
        fun create(context: Context, address: String): DeviceCommandSession? {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) {
                return PermissionDeniedSession("BLUETOOTH_CONNECT permission missing")
            }
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return null
            return AndroidDeviceCommandSession(context, adapter.getRemoteDevice(address))
        }
    }
}

private class PermissionDeniedSession(
    override val connectFailureMessage: String,
) : DeviceCommandSession {
    override suspend fun connect(): Boolean = false
    override suspend fun write(payload: ByteArray): Boolean = false
    override suspend fun close() = Unit
}

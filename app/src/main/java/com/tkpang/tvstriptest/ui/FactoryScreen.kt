package com.tkpang.tvstriptest.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tkpang.tvstriptest.factory.FactoryUiState
import com.tkpang.tvstriptest.model.FactoryDevice
import com.tkpang.tvstriptest.model.ProductCatalog
import com.tkpang.tvstriptest.model.ScanDevice

private data class ColorChoice(val label: String, val rgb: Int)

private val FACTORY_COLOR_CHOICES = listOf(
    ColorChoice("红", 0xFF0000),
    ColorChoice("绿", 0x00FF00),
    ColorChoice("蓝", 0x0000FF),
    ColorChoice("白", 0xFFFFFF),
    ColorChoice("黑", 0x000000),
)

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun FactoryScreen(
    state: FactoryUiState,
    onProductTypeChange: (String) -> Unit,
    onPidChange: (Int) -> Unit,
    onRssiThresholdChange: (Int) -> Unit,
    onTargetCountChange: (Int) -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onDeviceSelected: (String, Boolean) -> Unit,
    onConnectSelected: () -> Unit,
    onSetLightPid: () -> Unit,
    onSetColor: (Int) -> Unit,
    onMaxPowerColorChange: (Int) -> Unit,
    onSetHighestPowerColor: () -> Unit,
    onUnbindAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "TV Strip Factory",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(text = "Status: ${state.statusMessage}")
        }

        item {
            SettingsCard(
                state = state,
                onProductTypeChange = onProductTypeChange,
                onPidChange = onPidChange,
                onRssiThresholdChange = onRssiThresholdChange,
                onTargetCountChange = onTargetCountChange,
                onMaxPowerColorChange = onMaxPowerColorChange,
            )
        }

        item {
            OperationCard(
                state = state,
                onStartScan = onStartScan,
                onStopScan = onStopScan,
                onConnectSelected = onConnectSelected,
                onSetLightPid = onSetLightPid,
                onSetColor = onSetColor,
                onSetHighestPowerColor = onSetHighestPowerColor,
                onUnbindAll = onUnbindAll,
            )
        }

        item {
            Text(
                text = "Devices (${state.devices.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        val scanByAddress = state.scanDevices.associateBy { it.address }
        items(state.devices, key = { it.address }) { device ->
            DeviceRow(
                device = device,
                scanDevice = scanByAddress[device.address],
                onDeviceSelected = onDeviceSelected,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SettingsCard(
    state: FactoryUiState,
    onProductTypeChange: (String) -> Unit,
    onPidChange: (Int) -> Unit,
    onRssiThresholdChange: (Int) -> Unit,
    onTargetCountChange: (Int) -> Unit,
    onMaxPowerColorChange: (Int) -> Unit,
) {
    val product = ProductCatalog.productTypes.firstOrNull { it.devName == state.settings.productDevName }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Product Type / PID", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProductCatalog.productTypes.forEach { type ->
                    FilterChip(
                        selected = type.devName == state.settings.productDevName,
                        onClick = { onProductTypeChange(type.devName) },
                        label = { Text(type.displayName) },
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                product?.pidOptions.orEmpty().forEach { option ->
                    FilterChip(
                        selected = option.pid == state.settings.pid,
                        onClick = { onPidChange(option.pid) },
                        label = { Text("${option.displayName} (${option.pid})") },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.settings.rssiThreshold.toString(),
                    onValueChange = { it.toIntOrNull()?.let(onRssiThresholdChange) },
                    label = { Text("RSSI threshold") },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.settings.targetDeviceCount.toString(),
                    onValueChange = { it.toIntOrNull()?.let(onTargetCountChange) },
                    label = { Text("Target count") },
                    modifier = Modifier.weight(1f),
                )
            }
            Text("Max color: #${state.settings.maxPowerColor.toString(16).padStart(6, '0')}, brightness: ${state.settings.maxBrightness}")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FACTORY_COLOR_CHOICES.forEach { choice ->
                    FilterChip(
                        selected = state.settings.maxPowerColor == choice.rgb,
                        onClick = { onMaxPowerColorChange(choice.rgb) },
                        label = { Text("最高亮度${choice.label}") },
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun OperationCard(
    state: FactoryUiState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnectSelected: () -> Unit,
    onSetLightPid: () -> Unit,
    onSetColor: (Int) -> Unit,
    onSetHighestPowerColor: () -> Unit,
    onUnbindAll: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Flow", style = MaterialTheme.typography.titleMedium)
            Text("Selected: ${state.selectedCount} / target ${state.settings.targetDeviceCount}")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStartScan, enabled = !state.isScanning && !state.isBusy) { Text("Scan") }
                OutlinedButton(onClick = onStopScan, enabled = state.isScanning && !state.isBusy) { Text("Stop") }
                Button(onClick = onConnectSelected, enabled = state.selectedCount > 0 && !state.isBusy) { Text("Batch connect") }
                Button(onClick = onSetLightPid, enabled = state.selectedCount > 0 && !state.isBusy) { Text("设置 PID") }
                FACTORY_COLOR_CHOICES.forEach { choice ->
                    Button(onClick = { onSetColor(choice.rgb) }, enabled = state.selectedCount > 0 && !state.isBusy) { Text("设灯${choice.label}") }
                }
                Button(onClick = onSetHighestPowerColor, enabled = state.selectedCount > 0 && !state.isBusy) { Text("Highest brightness color") }
                OutlinedButton(onClick = onUnbindAll, enabled = state.connectedCount > 0 && !state.isBusy) { Text("一键解绑删除") }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: FactoryDevice,
    scanDevice: ScanDevice?,
    onDeviceSelected: (String, Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Checkbox(
                checked = scanDevice?.selected ?: false,
                onCheckedChange = { onDeviceSelected(device.address, it) },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name ?: "Unknown", fontWeight = FontWeight.SemiBold)
                Text(device.address)
                Spacer(modifier = Modifier.height(4.dp))
                Text("RSSI ${device.rssi} dBm, state ${device.state}")
                if (device.did != null || device.pid != null || device.firmwareVersion != null) {
                    Text("DID ${device.did ?: "-"}, PID ${device.pid ?: "-"}, FW ${device.firmwareVersion ?: "-"}")
                }
                if (device.lastResult.isNotBlank()) Text("Last: ${device.lastResult}")
                if (device.lastError.isNotBlank()) Text("Error: ${device.lastError}", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

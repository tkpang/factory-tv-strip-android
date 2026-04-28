package com.tkpang.tvstriptest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tkpang.tvstriptest.factory.FactoryUiState
import com.tkpang.tvstriptest.model.DeviceConnectionState
import com.tkpang.tvstriptest.model.FactoryDevice
import com.tkpang.tvstriptest.model.ProductCatalog
import com.tkpang.tvstriptest.model.ScanDevice

private data class ColorChoice(val label: String, val rgb: Int, val color: Color)

private val FACTORY_COLOR_CHOICES = listOf(
    ColorChoice("红色", 0xFF0000, Color(0xFFE53935)),
    ColorChoice("绿色", 0x00FF00, Color(0xFF43A047)),
    ColorChoice("蓝色", 0x0000FF, Color(0xFF1E88E5)),
    ColorChoice("白色", 0xFFFFFF, Color(0xFFF5F5F5)),
    ColorChoice("黑色", 0x000000, Color(0xFF212121)),
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
        modifier = modifier.fillMaxSize().background(Color(0xFFF5F7FB)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { HeaderCard(state) }
        item {
            StepProductAndScan(
                state = state,
                onProductTypeChange = onProductTypeChange,
                onPidChange = onPidChange,
                onRssiThresholdChange = onRssiThresholdChange,
                onTargetCountChange = onTargetCountChange,
                onStartScan = onStartScan,
                onStopScan = onStopScan,
            )
        }
        item {
            StepConnectDevices(
                state = state,
                onConnectSelected = onConnectSelected,
            )
        }
        val scanByAddress = state.scanDevices.associateBy { it.address }
        items(state.devices, key = { it.address }) { device ->
            DeviceCard(
                device = device,
                scanDevice = scanByAddress[device.address],
                onDeviceSelected = onDeviceSelected,
            )
        }
        item {
            StepTestLights(
                state = state,
                onSetLightPid = onSetLightPid,
                onSetColor = onSetColor,
                onMaxPowerColorChange = onMaxPowerColorChange,
                onSetHighestPowerColor = onSetHighestPowerColor,
            )
        }
        item {
            StepUnbind(
                state = state,
                onUnbindAll = onUnbindAll,
            )
        }
    }

    if (state.isScanning) {
        ScanDevicesDialog(
            state = state,
            onStopScan = onStopScan,
            onConnectSelected = onConnectSelected,
            onDeviceSelected = onDeviceSelected,
        )
    }
}

@Composable
private fun HeaderCard(state: FactoryUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B57D0)),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("工厂测试工具", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("按 1-2-3-4 步操作：先连接，再测试，最后解绑", color = Color(0xFFE8F0FE))
            Surface(color = Color.White.copy(alpha = 0.16f), shape = RoundedCornerShape(14.dp)) {
                Text(
                    text = state.statusMessage,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun StepProductAndScan(
    state: FactoryUiState,
    onProductTypeChange: (String) -> Unit,
    onPidChange: (Int) -> Unit,
    onRssiThresholdChange: (Int) -> Unit,
    onTargetCountChange: (Int) -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
) {
    val product = ProductCatalog.productTypes.firstOrNull { it.devName == state.settings.productDevName }
    StepCard(
        number = "1",
        title = "选择产品，扫描附近设备",
        hint = "设备越近，信号越强。系统会自动勾选信号好的设备。",
    ) {
        Text("产品类型", fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProductCatalog.productTypes.forEach { type ->
                FilterChip(
                    selected = type.devName == state.settings.productDevName,
                    onClick = { onProductTypeChange(type.devName) },
                    label = { Text(type.displayName) },
                )
            }
        }
        Text("产品型号", fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            product?.pidOptions.orEmpty().forEach { option ->
                FilterChip(
                    selected = option.pid == state.settings.pid,
                    onClick = { onPidChange(option.pid) },
                    label = { Text("${option.displayName}（${option.pid}）") },
                )
            }
        }
        SignalThresholdSlider(
            threshold = state.settings.rssiThreshold,
            onChange = onRssiThresholdChange,
        )
        TargetCountDial(
            count = state.settings.targetDeviceCount,
            onChange = onTargetCountChange,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onStartScan, enabled = !state.isScanning && !state.isBusy) { Text("开始扫描") }
            OutlinedButton(onClick = onStopScan, enabled = state.isScanning && !state.isBusy) { Text("停止扫描") }
        }
    }
}

@Composable
private fun SignalThresholdSlider(threshold: Int, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("自动勾选范围", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("信号 ${threshold} dBm", color = Color(0xFF0B57D0), fontWeight = FontWeight.Bold)
        }
        Slider(
            value = threshold.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = -90f..-35f,
            steps = 54,
        )
        Row {
            Text("远一些", color = Color(0xFF5F6368), modifier = Modifier.weight(1f))
            Text("近一些", color = Color(0xFF5F6368))
        }
        Text("推荐：只让本工位附近设备自动打勾，旁边工位的设备默认不选。", color = Color(0xFF5F6368), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TargetCountDial(count: Int, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("本次最多连接", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { onChange((count - 1).coerceAtLeast(1)) }) { Text("－") }
            Surface(color = Color(0xFFEAF2FF), shape = RoundedCornerShape(14.dp)) {
                Text("$count 台", modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp), color = Color(0xFF0B57D0), fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = { onChange((count + 1).coerceAtMost(20)) }) { Text("＋") }
        }
        Slider(
            value = count.toFloat(),
            onValueChange = { onChange(it.toInt().coerceIn(1, 20)) },
            valueRange = 1f..20f,
            steps = 18,
        )
        Text("滑动或点击加减号，设置这一批最多连接几台。", color = Color(0xFF5F6368), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ScanDevicesDialog(
    state: FactoryUiState,
    onStopScan: () -> Unit,
    onConnectSelected: () -> Unit,
    onDeviceSelected: (String, Boolean) -> Unit,
) {
    val strongDevices = state.scanDevices.filter { it.rssi >= state.settings.rssiThreshold }
    val weakDevices = state.scanDevices.filter { it.rssi < state.settings.rssiThreshold }
    val scanByAddress = state.scanDevices.associateBy { it.address }
    val devicesByAddress = state.devices.associateBy { it.address }

    Dialog(
        onDismissRequest = onStopScan,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("正在扫描附近灯带", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("请把手机靠近本工位灯带。绿色设备已自动勾选，橙色设备可手动选择。", color = Color(0xFF5F6368))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScanCounter("符合条件", strongDevices.size, Color(0xFF188038))
                    ScanCounter("信号较弱", weakDevices.size, Color(0xFFB06000))
                    ScanCounter("已选择", state.selectedCount, Color(0xFF0B57D0), suffix = "/${state.settings.targetDeviceCount}")
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { GroupTitle("符合条件，已自动选择", "这些设备距离合适，可以直接连接") }
                    if (strongDevices.isEmpty()) {
                        item { GroupEmpty("暂无符合条件设备，请靠近设备或调低范围要求。") }
                    }
                    items(strongDevices, key = { "strong-${it.address}" }) { scan ->
                        val device = devicesByAddress[scan.address] ?: scan.toFactoryDevice()
                        DeviceCard(device = device, scanDevice = scanByAddress[scan.address], onDeviceSelected = onDeviceSelected)
                    }
                    item { GroupTitle("信号较弱，可手动选择", "如果确认是本工位设备，可以手动打勾") }
                    if (weakDevices.isEmpty()) {
                        item { GroupEmpty("暂无信号较弱设备。") }
                    }
                    items(weakDevices, key = { "weak-${it.address}" }) { scan ->
                        val device = devicesByAddress[scan.address] ?: scan.toFactoryDevice()
                        DeviceCard(device = device, scanDevice = scanByAddress[scan.address], onDeviceSelected = onDeviceSelected)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onStopScan, modifier = Modifier.weight(1f)) { Text("停止扫描") }
                    Button(onClick = onConnectSelected, enabled = state.selectedCount > 0 && !state.isBusy, modifier = Modifier.weight(1f)) { Text("连接勾选设备") }
                }
            }
        }
    }
}

@Composable
private fun ScanCounter(label: String, count: Int, color: Color, suffix: String = "") {
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$count$suffix", color = color, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, color = color, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun GroupTitle(title: String, hint: String) {
    Column(Modifier.padding(top = 6.dp)) {
        Text(title, fontWeight = FontWeight.Bold)
        Text(hint, color = Color(0xFF5F6368), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun GroupEmpty(text: String) {
    Surface(color = Color(0xFFF1F3F4), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text, modifier = Modifier.padding(12.dp), color = Color(0xFF5F6368))
    }
}

@Composable
private fun StepConnectDevices(
    state: FactoryUiState,
    onConnectSelected: () -> Unit,
) {
    StepCard(
        number = "2",
        title = "确认勾选设备，点击连接",
        hint = "系统已按信号强弱排序。只连接打勾的设备，没打勾的不会操作。",
    ) {
        StatusChips(state)
        Button(
            onClick = onConnectSelected,
            enabled = state.selectedCount > 0 && !state.isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("连接勾选设备") }
        if (state.devices.isEmpty()) {
            EmptyDevicesHint()
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun StepTestLights(
    state: FactoryUiState,
    onSetLightPid: () -> Unit,
    onSetColor: (Int) -> Unit,
    onMaxPowerColorChange: (Int) -> Unit,
    onSetHighestPowerColor: () -> Unit,
) {
    val canControl = state.connectedCount > 0 && !state.isBusy
    StepCard(
        number = "3",
        title = "测试灯光颜色",
        hint = if (state.connectedCount > 0) "设备已连接，可以开始测试灯光。" else "请先完成第 2 步连接设备，否则这里不能操作。",
    ) {
        Button(onClick = onSetLightPid, enabled = canControl, modifier = Modifier.fillMaxWidth()) { Text("写入产品型号") }
        Text("普通设灯", fontWeight = FontWeight.SemiBold)
        ColorButtonRow(enabled = canControl, prefix = "设灯", onColorClick = onSetColor)
        Text("最高亮度颜色", fontWeight = FontWeight.SemiBold)
        ColorButtonRow(enabled = canControl, prefix = "选择", onColorClick = onMaxPowerColorChange)
        Text("当前最高亮度颜色：${colorName(state.settings.maxPowerColor)}")
        Button(onClick = onSetHighestPowerColor, enabled = canControl, modifier = Modifier.fillMaxWidth()) { Text("测试最高亮度") }
    }
}

@Composable
private fun StepUnbind(state: FactoryUiState, onUnbindAll: () -> Unit) {
    StepCard(
        number = "4",
        title = "测试完成，一键解绑",
        hint = "解绑后设备会从 App 删除，方便下一批继续测试。",
    ) {
        OutlinedButton(
            onClick = onUnbindAll,
            enabled = state.connectedCount > 0 && !state.isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("一键解绑删除") }
    }
}

@Composable
private fun StepCard(
    number: String,
    title: String,
    hint: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF0B57D0)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(number, color = Color.White, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(hint, color = Color(0xFF5F6368), style = MaterialTheme.typography.bodySmall)
                }
            }
            content()
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun StatusChips(state: FactoryUiState) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(onClick = {}, label = { Text("已发现 ${state.devices.size} 台") })
        AssistChip(onClick = {}, label = { Text("已勾选 ${state.selectedCount} 台") })
        AssistChip(onClick = {}, label = { Text("已连接 ${state.connectedCount} 台") })
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ColorButtonRow(enabled: Boolean, prefix: String, onColorClick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FACTORY_COLOR_CHOICES.forEach { choice ->
            OutlinedButton(onClick = { onColorClick(choice.rgb) }, enabled = enabled) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(choice.color))
                Spacer(Modifier.size(6.dp))
                Text("$prefix${choice.label}")
            }
        }
    }
}

@Composable
private fun EmptyDevicesHint() {
    Surface(color = Color(0xFFFFF7E0), shape = RoundedCornerShape(14.dp)) {
        Text(
            text = "还没有设备。请先点击“开始扫描”，把要测试的设备靠近手机。",
            modifier = Modifier.padding(12.dp),
            color = Color(0xFF7A4D00),
        )
    }
}

@Composable
private fun DeviceCard(
    device: FactoryDevice,
    scanDevice: ScanDevice?,
    onDeviceSelected: (String, Boolean) -> Unit,
) {
    val selected = scanDevice?.selected ?: false
    val stateText = deviceStateText(device.state)
    val stateColor = deviceStateColor(device.state)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFFEAF2FF) else Color.White),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Checkbox(checked = selected, onCheckedChange = { onDeviceSelected(device.address, it) })
            Text(deviceStateIcon(device.state), style = MaterialTheme.typography.headlineMedium)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(device.name ?: "灯带设备", fontWeight = FontWeight.Bold)
                    Surface(color = stateColor.copy(alpha = 0.14f), shape = RoundedCornerShape(12.dp)) {
                        Text(stateText, color = stateColor, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
                Text("信号 ${device.rssi} dBm  ·  地址 ${device.address}", color = Color(0xFF5F6368))
                if (device.did != null || device.pid != null || device.firmwareVersion != null) {
                    Text("DID ${device.did ?: "-"}  ·  PID ${device.pid ?: "-"}  ·  固件 ${device.firmwareVersion ?: "-"}", color = Color(0xFF5F6368))
                }
                if (device.lastResult.isNotBlank()) Text("上次结果：${device.lastResult}")
                if (device.lastError.isNotBlank()) Text("错误原因：${device.lastError}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun deviceStateText(state: DeviceConnectionState): String = when (state) {
    DeviceConnectionState.Discovered -> "待连接"
    DeviceConnectionState.Connecting -> "连接中"
    DeviceConnectionState.Connected -> "已连接"
    DeviceConnectionState.Ready -> "测试成功"
    DeviceConnectionState.Failed -> "失败"
    DeviceConnectionState.Unbound -> "已解绑"
}

private fun deviceStateIcon(state: DeviceConnectionState): String = when (state) {
    DeviceConnectionState.Discovered -> "📱"
    DeviceConnectionState.Connecting -> "🔄"
    DeviceConnectionState.Connected -> "✅"
    DeviceConnectionState.Ready -> "✅"
    DeviceConnectionState.Failed -> "⚠️"
    DeviceConnectionState.Unbound -> "🧹"
}

private fun deviceStateColor(state: DeviceConnectionState): Color = when (state) {
    DeviceConnectionState.Discovered -> Color(0xFF5F6368)
    DeviceConnectionState.Connecting -> Color(0xFFB06000)
    DeviceConnectionState.Connected -> Color(0xFF188038)
    DeviceConnectionState.Ready -> Color(0xFF188038)
    DeviceConnectionState.Failed -> Color(0xFFD93025)
    DeviceConnectionState.Unbound -> Color(0xFF5F6368)
}

private fun colorName(rgb: Int): String = FACTORY_COLOR_CHOICES.firstOrNull { it.rgb == rgb }?.label ?: "自定义颜色"

private fun ScanDevice.toFactoryDevice(): FactoryDevice = FactoryDevice(
    address = address,
    name = name,
    rssi = rssi,
    state = DeviceConnectionState.Discovered,
)

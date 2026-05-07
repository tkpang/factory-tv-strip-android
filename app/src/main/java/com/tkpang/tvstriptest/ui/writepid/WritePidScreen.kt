package com.tkpang.tvstriptest.ui.writepid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tkpang.tvstriptest.factory.WritePidViewModel
import com.tkpang.tvstriptest.model.ProductCatalog
import com.tkpang.tvstriptest.model.ScanDevice
import com.tkpang.tvstriptest.model.SensitivityLevel
import com.tkpang.tvstriptest.ui.wizard.step2.PulseRadar

@Composable
fun WritePidScreen(
    vm: WritePidViewModel,
    onBack: () -> Unit,
) {
    val pid by vm.selectedPid.collectAsStateWithLifecycle()
    val phase by vm.phase.collectAsStateWithLifecycle()
    val devices by vm.devices.collectAsStateWithLifecycle()
    val sensitivity by vm.sensitivity.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        onDispose { vm.exit() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header(onBack = { vm.exit(); onBack() })

        WarningBanner()

        when (val p = phase) {
            is WritePidViewModel.Phase.Idle -> SelectPidSection(
                selectedPid = pid,
                onSelect = vm::selectPid,
                onStart = vm::startScanning,
            )
            is WritePidViewModel.Phase.Scanning -> {
                val onlyUnbonded by vm.onlyUnbonded.collectAsStateWithLifecycle()
                val displayPidFilter by vm.displayPidFilter.collectAsStateWithLifecycle()
                ScanningSection(
                    devices = devices,
                    sensitivity = sensitivity,
                    selectedPid = pid,
                    onlyUnbonded = onlyUnbonded,
                    displayPidFilter = displayPidFilter,
                    onPick = vm::pickDeviceAndWrite,
                    onSensitivityChange = vm::setSensitivity,
                    onOnlyUnbondedChange = vm::setOnlyUnbonded,
                    onDisplayPidFilterChange = vm::setDisplayPidFilter,
                )
            }
            is WritePidViewModel.Phase.Writing -> WritingSection(
                address = p.address,
                pid = p.pid,
            )
            is WritePidViewModel.Phase.Success -> SuccessSection(
                pid = p.pid,
                address = p.address,
                onContinue = vm::continueScanning,
            )
            is WritePidViewModel.Phase.Failed -> FailedSection(
                message = p.message,
                onRetry = vm::continueScanning,
            )
        }
    }
}

@Composable
private fun Header(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("← 返回") }
        Spacer(Modifier.weight(1f))
        Text("写 PID 工具", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WarningBanner() {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFEF3C7))
            .padding(10.dp)
    ) {
        Text(
            "⚠ 仅供工厂出厂线使用 · 一次只配一台",
            color = Color(0xFF92400E),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SelectPidSection(
    selectedPid: Int?,
    onSelect: (Int) -> Unit,
    onStart: () -> Unit,
) {
    val options = ProductCatalog.productTypes.first { it.devName == "STV1" }.pidOptions
    Text("选择要写入的 PID", fontWeight = FontWeight.Bold)
    Card {
        Column {
            options.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selectedPid == option.pid,
                        onClick = { onSelect(option.pid) },
                    )
                    Text(option.displayName, modifier = Modifier.weight(1f))
                    Text("${option.pid}", color = Color(0xFF64748B))
                }
            }
        }
    }
    Button(
        onClick = onStart,
        enabled = selectedPid != null,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("开始扫描") }
}

@Composable
private fun ScanningSection(
    devices: List<ScanDevice>,
    sensitivity: SensitivityLevel,
    selectedPid: Int?,
    onlyUnbonded: Boolean,
    displayPidFilter: com.tkpang.tvstriptest.model.PidFilter,
    onPick: (String) -> Unit,
    onSensitivityChange: (SensitivityLevel) -> Unit,
    onOnlyUnbondedChange: (Boolean) -> Unit,
    onDisplayPidFilterChange: (com.tkpang.tvstriptest.model.PidFilter) -> Unit,
) {
    var sheetOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Text(
        "点击绿色设备开始写入（目标 PID = $selectedPid）",
        style = MaterialTheme.typography.labelMedium,
        color = Color(0xFF475569),
    )

    // 筛选条：显示 PID + 只看未绑定 + ⚙ 灵敏度
    WritePidFilterBar(
        displayPidFilter = displayPidFilter,
        onlyUnbonded = onlyUnbonded,
        onDisplayPidFilterChange = onDisplayPidFilterChange,
        onOnlyUnbondedChange = onOnlyUnbondedChange,
        onOpenSensitivity = { sheetOpen = true },
    )

    Box(
        modifier = Modifier.fillMaxWidth().height(360.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (devices.isEmpty()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🔍", style = MaterialTheme.typography.displayMedium)
                Text(
                    "扫描中…还没扫到设备",
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "确认设备已通电、距离 ≤ 5 米",
                    color = Color(0xFFCBD5E1),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        } else {
            PulseRadar(
                devices = devices,
                sensitivity = sensitivity,
                pairingStates = emptyMap(),
                onDeviceClick = onPick,
            )
        }
    }

    if (sheetOpen) {
        com.tkpang.tvstriptest.ui.wizard.step2.SensitivitySheet(
            current = sensitivity,
            onChange = onSensitivityChange,
            onDismiss = { sheetOpen = false },
        )
    }
}

@Composable
private fun WritePidFilterBar(
    displayPidFilter: com.tkpang.tvstriptest.model.PidFilter,
    onlyUnbonded: Boolean,
    onDisplayPidFilterChange: (com.tkpang.tvstriptest.model.PidFilter) -> Unit,
    onOnlyUnbondedChange: (Boolean) -> Unit,
    onOpenSensitivity: () -> Unit,
) {
    val pidOptions = ProductCatalog.productTypes.first { it.devName == "STV1" }.pidOptions
    var pidMenuOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val pidLabel = when (val f = displayPidFilter) {
        is com.tkpang.tvstriptest.model.PidFilter.Any -> "全部 PID"
        is com.tkpang.tvstriptest.model.PidFilter.Specific -> "PID ${f.pid}"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            androidx.compose.material3.OutlinedButton(onClick = { pidMenuOpen = true }) {
                Text(pidLabel)
            }
            androidx.compose.material3.DropdownMenu(
                expanded = pidMenuOpen,
                onDismissRequest = { pidMenuOpen = false },
            ) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("全部 PID") },
                    onClick = {
                        onDisplayPidFilterChange(com.tkpang.tvstriptest.model.PidFilter.Any)
                        pidMenuOpen = false
                    },
                )
                pidOptions.forEach { opt ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("${opt.displayName}（${opt.pid}）") },
                        onClick = {
                            onDisplayPidFilterChange(com.tkpang.tvstriptest.model.PidFilter.Specific(opt.pid))
                            pidMenuOpen = false
                        },
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Text(
            "只看未绑定",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF475569),
        )
        Spacer(Modifier.width(4.dp))
        androidx.compose.material3.Switch(
            checked = onlyUnbonded,
            onCheckedChange = onOnlyUnbondedChange,
        )

        Spacer(Modifier.width(8.dp))

        androidx.compose.material3.IconButton(onClick = onOpenSensitivity) {
            Text("⚙", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun WritingSection(address: String, pid: Int) {
    Box(
        Modifier.fillMaxWidth().height(360.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⚙", style = MaterialTheme.typography.displayMedium)
            Text(
                "正在写入 PID $pid 到 ${address.takeLast(5).replace(":", "")}",
                fontWeight = FontWeight.Bold,
            )
            Text(
                "连接 → 写入 → 校验 → 解绑",
                color = Color(0xFF64748B),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun SuccessSection(pid: Int, address: String, onContinue: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("✓", style = MaterialTheme.typography.displayLarge, color = Color(0xFF15803D))
        Text(
            "PID $pid 已写入并解绑",
            color = Color(0xFF15803D),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "设备 ${address.takeLast(5).replace(":", "")} 已回到出厂未配状态",
            color = Color(0xFF64748B),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text("再写一台")
        }
    }
}

@Composable
private fun FailedSection(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("✗", style = MaterialTheme.typography.displayLarge, color = Color(0xFFDC2626))
        Text(
            message,
            color = Color(0xFFDC2626),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text("重试")
        }
    }
}

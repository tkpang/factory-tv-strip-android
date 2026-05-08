package com.tkpang.tvstriptest.ui.version

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tkpang.tvstriptest.factory.VersionCheckViewModel
import com.tkpang.tvstriptest.ui.wizard.step2.PulseRadar

@Composable
fun VersionCheckScreen(
    vm: VersionCheckViewModel,
    onBack: () -> Unit,
) {
    val phase by vm.phase.collectAsStateWithLifecycle()
    val devices by vm.devices.collectAsStateWithLifecycle()
    val sensitivity by vm.sensitivity.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.start() }
    DisposableEffect(Unit) { onDispose { vm.exit() } }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.exit(); onBack() }) { Text("← 返回") }
            Spacer(Modifier.weight(1f))
            Text("查看版本号", fontWeight = FontWeight.Bold)
        }

        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFE0F2FE))
                .padding(10.dp)
        ) {
            Text(
                "点击雷达上的设备，连上后显示固件版本（不写、不解绑）",
                color = Color(0xFF0369A1),
                style = MaterialTheme.typography.labelMedium,
            )
        }

        when (val p = phase) {
            is VersionCheckViewModel.Phase.Scanning -> {
                Box(
                    modifier = Modifier.fillMaxWidth().height(420.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (devices.isEmpty()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🔍", style = MaterialTheme.typography.displayMedium)
                            Text(
                                "扫描中…",
                                color = Color(0xFF94A3B8),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    } else {
                        PulseRadar(
                            devices = devices,
                            sensitivity = sensitivity,
                            pairingStates = emptyMap(),
                            onDeviceClick = vm::pickAndRead,
                        )
                    }
                }
            }
            is VersionCheckViewModel.Phase.Reading -> {
                Box(
                    Modifier.fillMaxWidth().height(420.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⚙", style = MaterialTheme.typography.displayMedium)
                        Text(
                            "正在读取 ${p.address.takeLast(5).replace(":", "")} 的版本号",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            is VersionCheckViewModel.Phase.Loaded -> VersionResult(
                address = p.address,
                pid = p.pid,
                versions = p.versions,
                onContinue = vm::backToScan,
            )
            is VersionCheckViewModel.Phase.Failed -> Column(
                Modifier.fillMaxWidth().padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("✗", style = MaterialTheme.typography.displayLarge, color = Color(0xFFDC2626))
                Text(
                    "${p.address.takeLast(5).replace(":", "")} 读取失败：${p.message}",
                    color = Color(0xFFDC2626),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = vm::backToScan, modifier = Modifier.fillMaxWidth()) {
                    Text("返回扫描")
                }
            }
        }
    }
}

@Composable
private fun VersionResult(
    address: String,
    pid: Int,
    versions: List<String>,
    onContinue: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${address.takeLast(5).replace(":", "")}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "PID $pid",
                    color = Color(0xFF64748B),
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            // 按 fw_hw_info 顺序贴标签：索引 0 = ESP32 主固件，索引 1+ = MCU 子固件
            // T23 灯带的子固件按固件团队约定通常是 t23 app / t23 sys（数量取决于 mcu_fw_hw_info_num）
            val labels = listOf("主版本号 (ESP32)", "T23 app", "T23 sys", "子版本 4", "子版本 5", "子版本 6")
            if (versions.isEmpty()) {
                Text(
                    "（设备未返回版本号字段）",
                    color = Color(0xFF94A3B8),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            versions.forEachIndexed { i, v ->
                val name = labels.getOrElse(i) { "子版本 ${i + 1}" }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        v,
                        color = Color(0xFF2563EB),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
        Text("再读一台")
    }
}

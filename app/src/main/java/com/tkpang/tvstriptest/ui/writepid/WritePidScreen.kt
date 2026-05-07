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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tkpang.tvstriptest.factory.WritePidViewModel
import com.tkpang.tvstriptest.model.ProductCatalog

@Composable
fun WritePidScreen(
    vm: WritePidViewModel,
    onBack: () -> Unit,
) {
    val pid by vm.selectedPid.collectAsStateWithLifecycle()
    val phase by vm.phase.collectAsStateWithLifecycle()
    val options = ProductCatalog.productTypes.first { it.devName == "STV1" }.pidOptions

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Spacer(Modifier.weight(1f))
            Text("写 PID 工具", fontWeight = FontWeight.Bold)
        }

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
                            selected = pid == option.pid,
                            onClick = { vm.selectPid(option.pid) },
                        )
                        Text(option.displayName, modifier = Modifier.weight(1f))
                        Text("${option.pid}", color = Color(0xFF64748B))
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        when (val p = phase) {
            is WritePidViewModel.Phase.Idle -> Button(
                onClick = vm::start,
                enabled = pid != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("开始写入") }
            is WritePidViewModel.Phase.Scanning -> Text("正在扫描设备…")
            is WritePidViewModel.Phase.Writing -> Text("正在写入到 ${p.device.address}")
            is WritePidViewModel.Phase.Success -> Column {
                Text(
                    "✓ PID 已写入：${p.pid}",
                    color = Color(0xFF15803D),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = vm::reset, modifier = Modifier.fillMaxWidth()) {
                    Text("再写一台")
                }
            }
            is WritePidViewModel.Phase.Failed -> Column {
                Text("✗ ${p.message}", color = Color(0xFFDC2626))
                Spacer(Modifier.height(8.dp))
                Button(onClick = vm::reset, modifier = Modifier.fillMaxWidth()) {
                    Text("重试")
                }
            }
        }
    }
}

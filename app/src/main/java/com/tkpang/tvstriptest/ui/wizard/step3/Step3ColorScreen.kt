package com.tkpang.tvstriptest.ui.wizard.step3

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tkpang.tvstriptest.factory.ColorTestUseCase
import com.tkpang.tvstriptest.model.FactoryUiState

@Composable
fun Step3ColorScreen(
    state: FactoryUiState,
    onCommand: (ColorTestUseCase.Command) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tabIndex by remember { mutableStateOf(0) }

    Column(
        modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusBar(state.pairedDevices.size)

        TabRow(selectedTabIndex = tabIndex) {
            Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }) {
                Text("普通设灯", Modifier.padding(12.dp))
            }
            Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }) {
                Text("最高亮度", Modifier.padding(12.dp))
            }
        }

        if (tabIndex == 0) {
            ColorGrid(onCommand)
        } else {
            MaxPowerSection(onCommand)
        }

        FeedbackBar(state.colorTestResult)
    }
}

@Composable
private fun StatusBar(count: Int) {
    val empty = count == 0
    val bg = if (empty) Color(0xFFFEF3C7) else Color(0xFFECFDF5)
    val fg = if (empty) Color(0xFF92400E) else Color(0xFF065F46)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(10.dp)
    ) {
        Text(
            text = if (empty) "⚠ 还没有已配对设备，请回上一步" else "已配 $count 台",
            color = fg,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (!empty) Text("全部 Ready ✓", color = fg)
    }
}

@Composable
private fun ColorGrid(onCommand: (ColorTestUseCase.Command) -> Unit) {
    val items = listOf(
        Triple("红", Color(0xFFEF4444), ColorTestUseCase.Command.RED),
        Triple("绿", Color(0xFF22C55E), ColorTestUseCase.Command.GREEN),
        Triple("蓝", Color(0xFF3B82F6), ColorTestUseCase.Command.BLUE),
        Triple("白", Color(0xFFF8FAFC), ColorTestUseCase.Command.WHITE),
        Triple("黑", Color(0xFF1E293B), ColorTestUseCase.Command.BLACK),
        Triple("关", Color(0xFFE5E7EB), ColorTestUseCase.Command.OFF),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, color, cmd) ->
                    val text = if (label == "白" || label == "关") Color(0xFF0F172A) else Color.White
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { onCommand(cmd) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            color = text,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MaxPowerSection(onCommand: (ColorTestUseCase.Command) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = { onCommand(ColorTestUseCase.Command.MAX_POWER) },
            modifier = Modifier.fillMaxWidth().height(68.dp),
        ) {
            Text(
                "触发最大功率",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            "用于 EMC 最大功耗测试",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF64748B),
        )
    }
}

@Composable
private fun FeedbackBar(result: ColorTestUseCase.Result?) {
    if (result == null) return
    val ok = result.success
    val failed = result.failed
    val bg = if (failed.isEmpty()) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
    val titleColor = if (failed.isEmpty()) Color(0xFF15803D) else Color(0xFFB91C1C)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = if (failed.isEmpty()) "✓ 已下发到 $ok 台设备"
                   else "⚠ 成功 $ok 台 · 失败 ${failed.size} 台",
            color = titleColor,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
        )
        // 列出每台失败设备的错误信息，方便诊断
        failed.forEach { f ->
            Text(
                text = "${f.address.takeLast(5).replace(":", "")}: ${f.message}",
                color = Color(0xFF7F1D1D),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

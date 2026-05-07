package com.tkpang.tvstriptest.ui.wizard.step4

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tkpang.tvstriptest.model.FactoryUiState

@Composable
fun Step4UnbindScreen(
    state: FactoryUiState,
    onUnbind: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirming by remember { mutableStateOf(false) }
    val progress = state.unbindProgress

    Column(
        modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(70.dp).clip(CircleShape).background(Color(0xFFFEF2F2)),
            contentAlignment = Alignment.Center,
        ) {
            Text("🗑", style = MaterialTheme.typography.headlineMedium)
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = "测试完成，把这 ${state.pairedDevices.size} 台设备\n从 App 上一键解绑删除",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )

        Spacer(Modifier.height(20.dp))

        when {
            progress != null && progress.completed < progress.total -> {
                Text(
                    "正在解绑 ${progress.completed} / ${progress.total}",
                    fontWeight = FontWeight.SemiBold,
                )
                LinearProgressIndicator(
                    progress = { progress.completed.toFloat() / progress.total },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            progress != null && progress.completed == progress.total -> {
                Text(
                    "✓ 全部解绑成功（失败 ${progress.failed.size} 台）",
                    color = Color(0xFF15803D),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onRestart,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("完成 · 重新开始测试")
                }
            }
            else -> {
                Button(
                    onClick = { confirming = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text("全部解绑并删除", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "解绑后设备会回到出厂未配状态，下次扫描可以重新配",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("确认解绑？") },
            text = { Text("将对 ${state.pairedDevices.size} 台设备发送解绑命令，无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onUnbind()
                }) {
                    Text("确认解绑", color = Color(0xFFDC2626))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text("取消")
                }
            },
        )
    }
}

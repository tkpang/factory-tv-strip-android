package com.tkpang.tvstriptest.ui.wizard.step2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.tkpang.tvstriptest.model.ErrorBanner
import com.tkpang.tvstriptest.model.FactoryUiState
import com.tkpang.tvstriptest.model.SensitivityLevel

@Composable
fun Step2ScanScreen(
    state: FactoryUiState,
    onSensitivityChange: (SensitivityLevel) -> Unit,
    onOpenBluetooth: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onPairDevice: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sheetOpen by remember { mutableStateOf(false) }

    // 进入本页面立即开始扫描，离开（或重组销毁）时停止。
    DisposableEffect(Unit) {
        onStartScan()
        onDispose { onStopScan() }
    }

    Column(
        modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFF2563EB))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    "已配 ${state.pairedDevices.size}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "附近 ${state.visibleDevices.size} 台",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF64748B),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { sheetOpen = true }) {
                Text("⚙", style = MaterialTheme.typography.titleMedium)
            }
        }

        // 配对反馈横幅：成功 / 失败 / 信息三种色调，大块显眼，工厂工人远距离也能看清
        state.pairingMessage?.let { msg ->
            PairingFeedbackBanner(msg)
        }

        if (state.errorBanner != null) {
            ErrorBannerView(state.errorBanner, onOpenBluetooth)
        }

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            if (state.errorBanner == null && state.visibleDevices.isEmpty()) {
                EmptyScanState()
            } else {
                PulseRadar(
                    devices = state.visibleDevices,
                    sensitivity = state.settings.sensitivity,
                    pairingStates = emptyMap(),
                    onDeviceClick = onPairDevice,
                )
            }
        }

        Text(
            text = if (state.pairedDevices.isEmpty())
                "点击雷达上绿色设备开始连接"
            else
                "已配 ${state.pairedDevices.size} 台 · 继续点击下一台",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF64748B),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }

    if (sheetOpen) {
        SensitivitySheet(
            current = state.settings.sensitivity,
            onChange = onSensitivityChange,
            onDismiss = { sheetOpen = false },
        )
    }
}

@Composable
private fun PairingFeedbackBanner(message: String) {
    val (bg, fg) = when {
        message.startsWith("✓") -> Color(0xFFDCFCE7) to Color(0xFF15803D)  // success 绿
        message.startsWith("⚠") -> Color(0xFFFEF3C7) to Color(0xFF92400E)  // warn 黄
        message.startsWith("✗") -> Color(0xFFFEE2E2) to Color(0xFFB91C1C)  // error 红
        else -> Color(0xFFE0F2FE) to Color(0xFF0369A1)                     // info 蓝
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            message,
            color = fg,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun ErrorBannerView(banner: ErrorBanner, onOpen: () -> Unit) {
    val (msg, btn) = when (banner) {
        is ErrorBanner.BluetoothOff -> "📵 蓝牙未打开" to "打开蓝牙"
        is ErrorBanner.MissingPermission -> "需要蓝牙权限" to "去授权"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFEF2F2))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            msg,
            color = Color(0xFF991B1B),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = onOpen,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
        ) {
            Text(btn)
        }
    }
}

@Composable
private fun EmptyScanState() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🔍", style = MaterialTheme.typography.displayMedium)
        Text(
            "还没扫到设备",
            color = Color(0xFF94A3B8),
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "确认设备已通电、距离 ≤ 5 米",
            color = Color(0xFFCBD5E1),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

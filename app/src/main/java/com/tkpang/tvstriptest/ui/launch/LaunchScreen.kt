package com.tkpang.tvstriptest.ui.launch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun LaunchScreen(
    onTest: () -> Unit,
    onWritePid: () -> Unit,
    onCheckVersion: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(Color(0xFFF1F5F9)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        Text(
            "工厂测试 App",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "请选择要进入的功能",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF64748B),
        )
        Spacer(Modifier.height(20.dp))

        ModeCard(
            icon = "📡",
            iconBg = Color(0xFFDBEAFE),
            title = "测试设备",
            tag = "主功能",
            tagBg = Color(0xFF2563EB),
            subtitle = "扫描 → 配对 → 颜色测试 → 解绑",
            highlighted = true,
            onClick = onTest,
        )
        ModeCard(
            icon = "🔢",
            iconBg = Color(0xFFE0E7FF),
            title = "查看版本号",
            subtitle = "扫描 → 选设备 → 显示 ESP32 / T23 app / T23 sys 版本",
            highlighted = false,
            onClick = onCheckVersion,
        )
        ModeCard(
            icon = "✏️",
            iconBg = Color(0xFFFEF3C7),
            title = "写 PID（工厂出厂用）",
            subtitle = "单台扫描连接 → 写入指定 PID",
            highlighted = false,
            onClick = onWritePid,
        )
    }
}

@Composable
private fun ModeCard(
    icon: String,
    iconBg: Color,
    title: String,
    subtitle: String,
    highlighted: Boolean,
    tag: String? = null,
    tagBg: Color = Color.Gray,
    onClick: () -> Unit,
) {
    val borderColor = if (highlighted) Color(0xFF2563EB) else Color.Transparent
    val cardBg = if (highlighted) Color(0xFFEFF6FF) else Color.White
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .border(2.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Text(icon, style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (tag != null) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(tagBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            tag,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            Text(
                subtitle,
                color = Color(0xFF64748B),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

package com.tkpang.tvstriptest.ui.wizard.step2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tkpang.tvstriptest.model.SensitivityLevel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensitivitySheet(
    current: SensitivityLevel,
    onChange: (SensitivityLevel) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "配对灵敏度",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "越严格 = 设备要凑得越近才会自动配上",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF64748B),
            )
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFFE2E8F0))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                SensitivityLevel.values().forEach { level ->
                    val active = level == current
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (active) Color(0xFF2563EB) else Color.Transparent)
                            .clickable { onChange(level) }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            level.displayName,
                            color = if (active) Color.White else Color(0xFF475569),
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("完成")
            }
        }
    }
}

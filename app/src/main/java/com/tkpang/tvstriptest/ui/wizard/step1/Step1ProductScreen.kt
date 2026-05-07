package com.tkpang.tvstriptest.ui.wizard.step1

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tkpang.tvstriptest.model.PidFilter
import com.tkpang.tvstriptest.model.ProductCatalog

@Composable
fun Step1ProductScreen(
    selectedProductDevName: String,
    pidFilter: PidFilter,
    onlyUnbonded: Boolean,
    onProductChange: (String) -> Unit,
    onPidFilterChange: (PidFilter) -> Unit,
    onOnlyUnbondedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionLabel("产品类型")
        ProductGrid(selectedProductDevName, onProductChange)

        SectionLabel("PID 筛选")
        PidList(pidFilter = pidFilter, onChange = onPidFilterChange)

        SectionLabel("其他条件")
        OnlyUnbondedSwitch(onlyUnbonded, onOnlyUnbondedChange)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Color(0xFF475569),
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun ProductGrid(selected: String, onChange: (String) -> Unit) {
    val products = ProductCatalog.productTypes
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        products.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { p ->
                    val supported = p.pidOptions.isNotEmpty()
                    val isSelected = p.devName == selected
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = supported) { onChange(p.devName) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFFEFF6FF) else Color.White,
                        ),
                        border = BorderStroke(
                            width = 2.dp,
                            color = if (isSelected) Color(0xFF2563EB) else Color(0xFFE5E7EB),
                        ),
                    ) {
                        Column(
                            Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(p.devName, fontWeight = FontWeight.Bold)
                            Text(
                                if (supported) p.displayName else "暂未支持",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (supported) Color(0xFF64748B) else Color(0xFFCBD5E1),
                            )
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PidList(pidFilter: PidFilter, onChange: (PidFilter) -> Unit) {
    val product = ProductCatalog.productTypes.first { it.devName == "STV1" }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column {
            PidRow(
                label = "不筛选 PID（任意）",
                num = "—",
                selected = pidFilter is PidFilter.Any,
                onClick = { onChange(PidFilter.Any) },
                special = true,
            )
            product.pidOptions.forEach { option ->
                PidRow(
                    label = option.displayName,
                    num = option.pid.toString(),
                    selected = pidFilter is PidFilter.Specific && pidFilter.pid == option.pid,
                    onClick = { onChange(PidFilter.Specific(option.pid)) },
                )
            }
        }
    }
}

@Composable
private fun PidRow(
    label: String,
    num: String,
    selected: Boolean,
    onClick: () -> Unit,
    special: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            modifier = Modifier.weight(1f),
            fontWeight = if (special) FontWeight.Medium else FontWeight.Normal,
        )
        Text(num, color = Color(0xFF64748B), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun OnlyUnbondedSwitch(value: Boolean, onChange: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("只显示未绑定的设备", fontWeight = FontWeight.SemiBold)
                Text(
                    "过滤掉已被别人配过的灯带",
                    color = Color(0xFF64748B),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Switch(checked = value, onCheckedChange = onChange)
        }
    }
}

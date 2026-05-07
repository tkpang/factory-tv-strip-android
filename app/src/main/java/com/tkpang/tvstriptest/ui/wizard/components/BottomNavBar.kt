package com.tkpang.tvstriptest.ui.wizard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BottomNavBar(
    onBack: (() -> Unit)?,
    onNext: (() -> Unit)?,
    nextLabel: String = "下一步 →",
    backLabel: String = "← 上一步",
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onBack ?: {},
            enabled = onBack != null,
            modifier = Modifier.weight(1f),
        ) { Text(backLabel) }
        Button(
            onClick = onNext ?: {},
            enabled = onNext != null,
            modifier = Modifier.weight(1f),
        ) { Text(nextLabel) }
    }
}

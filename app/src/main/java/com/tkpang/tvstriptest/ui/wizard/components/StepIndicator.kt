package com.tkpang.tvstriptest.ui.wizard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tkpang.tvstriptest.model.WizardStep

@Composable
fun StepIndicator(currentStep: WizardStep, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = currentStep.displayName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            WizardStep.values().forEach { s ->
                val color = when {
                    s.index < currentStep.index -> Color(0xFF22C55E)
                    s.index == currentStep.index -> Color(0xFF2563EB)
                    else -> Color(0xFFCBD5E1)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color),
                )
            }
        }
    }
}

@Preview
@Composable
private fun StepIndicatorPreview() {
    StepIndicator(currentStep = WizardStep.SCAN, modifier = Modifier.padding(16.dp))
}

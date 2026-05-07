package com.tkpang.tvstriptest.ui.wizard.step2

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tkpang.tvstriptest.model.PairingState
import com.tkpang.tvstriptest.model.ScanDevice
import com.tkpang.tvstriptest.model.SensitivityLevel
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PulseRadar(
    devices: List<ScanDevice>,
    sensitivity: SensitivityLevel,
    pairingStates: Map<String, PairingState> = emptyMap(),
    radius: Dp = 110.dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(radius * 2), contentAlignment = Alignment.Center) {
        PulseRings(radius = radius)
        AutoPairZone(radius = radius, fraction = sensitivity.zoneRadiusFraction)
        CenterDot()
        devices.forEach { device ->
            val state = pairingStates[device.address] ?: device.pairingState
            DeviceBlip(device = device, state = state, radarRadiusPx = radius.value)
        }
    }
}

@Composable
private fun PulseRings(radius: Dp) {
    val transition = rememberInfiniteTransition(label = "pulse")
    repeat(3) { i ->
        val scale by transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2400, easing = LinearEasing),
                initialStartOffset = StartOffset(i * 800),
            ),
            label = "scale$i",
        )
        val alpha by transition.animateFloat(
            initialValue = 0.8f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(2400, easing = LinearEasing),
                initialStartOffset = StartOffset(i * 800),
            ),
            label = "alpha$i",
        )
        Box(
            Modifier
                .size(radius * 2)
                .scale(scale)
                .border(2.dp, Color(0xFF2563EB).copy(alpha = alpha), CircleShape),
        )
    }
}

@Composable
private fun AutoPairZone(radius: Dp, fraction: Float) {
    Box(
        Modifier
            .size(radius * 2 * fraction)
            .border(
                width = 1.5.dp,
                color = Color(0xFF60A5FA),
                shape = CircleShape,
            )
            .background(Color(0x1460A5FA), CircleShape),
    )
}

@Composable
private fun CenterDot() {
    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(listOf(Color(0xFF3B82F6), Color(0xFF1E3A8A))),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text("📱", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun DeviceBlip(device: ScanDevice, state: PairingState, radarRadiusPx: Float) {
    val (bg, fg, dot) = chipColors(state)
    val (xFrac, yFrac) = positionForDevice(device)
    val xDp = (xFrac * radarRadiusPx).dp
    val yDp = (yFrac * radarRadiusPx).dp
    val label = "${device.address.takeLast(5).replace(":", "")}${chipSuffix(state)}"

    Box(
        Modifier
            .offset(x = xDp, y = yDp)
            .background(bg, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(5.dp))
            Text(
                label,
                color = fg,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private fun chipColors(state: PairingState): Triple<Color, Color, Color> = when (state) {
    PairingState.DETECTED -> Triple(Color.White, Color(0xFF0F172A), Color(0xFF22C55E))
    PairingState.PREPARING -> Triple(Color.White, Color(0xFF15803D), Color(0xFF22C55E))
    PairingState.QUEUED, PairingState.CONNECTING ->
        Triple(Color(0xFFF1F5F9), Color(0xFF64748B), Color(0xFF94A3B8))
    PairingState.PID_MISMATCH, PairingState.FAILED, PairingState.PAIRED ->
        Triple(Color(0xFFFEF2F2), Color(0xFFDC2626), Color(0xFFEF4444))
}

private fun chipSuffix(state: PairingState) = when (state) {
    PairingState.PREPARING -> " 准备配对"
    PairingState.QUEUED -> " 队列中"
    PairingState.CONNECTING -> " 连接中"
    PairingState.PID_MISMATCH -> " PID 不符"
    PairingState.FAILED -> " 连接失败"
    else -> ""
}

private fun positionForDevice(device: ScanDevice): Pair<Float, Float> {
    val rssi = device.rssi.toFloat().coerceIn(-90f, -30f)
    val r = ((-30f - rssi) / 60f).coerceIn(0f, 1f) * 0.85f
    val angleDeg = (device.address.hashCode() and 0x1FF) % 360
    val angle = Math.toRadians(angleDeg.toDouble())
    return (r * cos(angle).toFloat()) to (r * sin(angle).toFloat())
}

@Preview
@Composable
private fun PulseRadarPreview() {
    val devices = listOf(
        ScanDevice("AA:01:02:03:04:05", "LP", -55, pid = 144, isBonded = false),
        ScanDevice("BB:11:22:33:44:55", "LP", -75, pid = 144, isBonded = false),
    )
    PulseRadar(
        devices = devices,
        sensitivity = SensitivityLevel.NEAR,
        pairingStates = mapOf("AA:01:02:03:04:05" to PairingState.PREPARING),
    )
}

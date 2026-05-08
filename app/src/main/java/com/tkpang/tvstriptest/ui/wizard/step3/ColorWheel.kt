package com.tkpang.tvstriptest.ui.wizard.step3

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * 圆形色盘。HSV：极角 = hue (0-360°)、半径占比 = saturation (0-1)、value 固定 = 1。
 * 点击或拖动改色 → onColorChange(rgb 0xRRGGBB)。
 *
 * 注意：ColorWheel 自己不做节流；调用方应该用 StateFlow.sample(200ms) 或类似机制
 * 抑制频率。我们把"无节流的连续值流"暴露给上层，节流策略由 VM 控制。
 */
@Composable
fun ColorWheel(
    onColorChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickerOffset by remember { mutableStateOf<Offset?>(null) }

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    pickerOffset = offset
                    emitColor(offset, size.width.toFloat(), size.height.toFloat(), onColorChange)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    pickerOffset = change.position
                    emitColor(change.position, size.width.toFloat(), size.height.toFloat(), onColorChange)
                }
            },
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = min(cx, cy)

        // sweep gradient = 色相环
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color.Red, Color.Yellow, Color.Green, Color.Cyan,
                    Color.Blue, Color.Magenta, Color.Red,
                ),
                center = Offset(cx, cy),
            ),
            radius = r,
            center = Offset(cx, cy),
        )
        // 中心白光过渡 = 饱和度从中心 0 到边缘 1
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color.Transparent),
                center = Offset(cx, cy),
                radius = r,
            ),
            radius = r,
            center = Offset(cx, cy),
        )
        // 当前选中的指示器
        pickerOffset?.let { p ->
            drawCircle(
                color = Color.White,
                center = p,
                radius = 14f,
                style = Stroke(width = 4f),
            )
            drawCircle(
                color = Color.Black,
                center = p,
                radius = 18f,
                style = Stroke(width = 2f),
            )
        }
    }
}

private fun emitColor(
    pos: Offset,
    w: Float,
    h: Float,
    callback: (Int) -> Unit,
) {
    val cx = w / 2f
    val cy = h / 2f
    val r = min(cx, cy)
    val dx = pos.x - cx
    val dy = pos.y - cy
    val dist = hypot(dx, dy)
    if (dist > r) return  // 圆外不响应
    // hue: atan2(dy, dx) -> -π..π，再转 0..360
    val angleRad = atan2(dy, dx)
    val hue = ((Math.toDegrees(angleRad.toDouble()).toFloat() + 360f) % 360f)
    val sat = (dist / r).coerceIn(0f, 1f)
    val rgb = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, 1f)) and 0xFFFFFF
    callback(rgb)
}

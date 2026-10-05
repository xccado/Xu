package com.xse.sesame.ui.miuix

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xse.sesame.util.StatHistory
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

/**
 * 近 7 天收取趋势卡（拟态）：Canvas 折线图 + 渐变填充 + 底部日期刻度。
 * 数据来自 StatHistory（日历史文件），缺失日按 0 画，时间轴连续。
 * dataVersion 变化时（统计刷新）重组重读。
 */
@Composable
fun EnergyTrendCard(dataVersion: Int) {
    @Suppress("UNUSED_VARIABLE")
    val revision = dataVersion

    val days: List<Pair<String, StatHistory.Day>> = remember(dataVersion) {
        StatHistory.getRecentDays(7).map { (k, v) -> k to v }
    }
    val maxVal = (days.maxOfOrNull { it.second.collected } ?: 0).coerceAtLeast(1)

    val primary = Color(0xFF7C5CFF)
    val pink = Color(0xFFF56A8C)
    val labelColor = Color(0xFF7B8698)

    Column(
        Modifier
            .fillMaxWidth()
            .neuRaised(RoundedCornerShape(24.dp), 5.dp)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Text(
            text = "近 7 天收取趋势",
            fontSize = 14.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(10.dp))

        // 折线图：上面曲线，底部日期刻度
        androidx.compose.foundation.Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            val w = size.width
            val h = size.height
            val step = if (days.size > 1) w / (days.size - 1) else w

            // 折线点：x 均分，y 按数值比例（顶部留 12% 边距）
            val pts = days.mapIndexed { i, e ->
                Offset(
                    x = i * step,
                    y = h * (1f - e.second.collected.toFloat() / maxVal) * 0.88f + h * 0.06f
                )
            }

            // 渐变填充
            val fill = Path().apply {
                moveTo(pts.first().x, h)
                pts.forEach { lineTo(it.x, it.y) }
                lineTo(pts.last().x, h)
                close()
            }
            drawPath(
                fill,
                brush = Brush.verticalGradient(
                    listOf(Color(0x667C5CFF), Color(0x00FFFFFF)),
                    startY = 0f, endY = h
                )
            )

            // 折线
            val line = Path().apply {
                moveTo(pts.first().x, pts.first().y)
                pts.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(
                line,
                brush = Brush.linearGradient(listOf(Color(0xFF7C5CFF), Color(0xFFF56A8C))),
                style = Stroke(width = 6f)
            )
            // 数据点
            pts.forEach { drawCircle(Color(0xFF7C5CFF), radius = 8f, center = it) }

            // 日期刻度
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = 0xFF7B8698.toInt()
                    textSize = 26f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
                days.forEachIndexed { i, e ->
                    val label = e.first.substring(5) // MM-dd
                    drawText(label, i * step, h + 30f, paint)
                }
            }
        }
    }
}

private fun Offset(x: Float, y: Float): Offset = Offset(x, y)

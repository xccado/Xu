package com.xse.sesame.ui.miuix

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.xse.sesame.R
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 首页桌宠展示位：一言下方 Q 版鲸鱼娘，会自己上下浮动 + 轻微摇摆，
 * 支持拖动移动位置；点击弹出对话气泡（互动台词）。
 */
@Composable
fun PetHomeImage() {
    val context = LocalContext.current
    val transition = rememberInfiniteTransition(label = "pet_home")
    val floatY by transition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pet_float_y"
    )
    val sway by transition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pet_sway"
    )
    var dragOffset by remember { mutableStateOf(IntOffset.Zero) }
    var showTalk by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, floatY.roundToInt()) + dragOffset }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    dragOffset += IntOffset(dragAmount.x.roundToInt(), dragAmount.y.roundToInt())
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { showTalk = true })
            }
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.pet_idle),
            contentDescription = "Q版鲸鱼娘桌宠",
            modifier = Modifier
                .size(132.dp)
                .graphicsLayer { rotationZ = sway }
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "鲸鱼娘桌宠 · 拖动我 · 点击和我说话",
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }

    if (showTalk) {
        val lines = remember {
            arrayOf(
                "老板好呀，我是鲸鱼娘～今天想让我跑点什么任务？",
                "辛苦了老板，喝口茶歇一歇，剩下的交给我就行～",
                "老板，庄园、森林、农场……我都记着呢，随时开工！",
                "哇，老板今天也元气满满！要我去干活了吗？"
            )
        }
        val line = remember { lines[(System.currentTimeMillis() % lines.size).toInt()] }
        Dialog(onDismissRequest = { showTalk = false }) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .neuRaised(RoundedCornerShape(24.dp), 10.dp)
                    .padding(20.dp)
            ) {
                Column {
                    Text("鲸鱼娘", color = MiuixTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(8.dp))
                    Text(line, color = MiuixTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(text = "知道了", onClick = { showTalk = false })
                    }
                }
            }
        }
    }
}

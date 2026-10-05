package com.xse.sesame.ui.miuix

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

/**
 * Sure-Xu 拟态（Neumorphism / Soft UI）设计系统 —— 未来拟态纯白版。
 *
 * 核心思想：界面元素与背景使用**同一纯白基色**（0xFFFFFF），
 * 完全依靠柔和的双向光照（左上高光 + 右下冷调阴影）塑造体积感——
 * 凸起 [neuRaised] 像从背景里"长出来"，凹陷 [neuPressed] 像被"按进去"。
 *
 * 与旧奶白版的区别：
 * - 基色由暖米白 → 纯白，阴影由暖灰棕 → 冷调蓝灰（未来感来源）；
 * - 描边由"白→阴影"改为"白→发丝灰"，卡片边缘更干净利落；
 * - 新增 [ItemCard] / [CardList]：**一个功能一张卡片**的标准容器。
 */
object Neu {

    /** 拟态基色：与窗口底色一致的纯白 */
    val base = Color(0xFFFFFFFF)

    /** 左上高光 */
    val highlight = Color(0xFFFFFFFF)

    /** 右下阴影（冷调蓝灰） */
    val shadow = Color(0xFFAFC1D9)

    /** 凹陷面底色（比基色深半档的冷白） */
    val pressed = Color(0xFFF3F7FC)

    /** 凹陷面深描边 */
    val pressedDark = Color(0xFFCFDAE9)

    /** 凸起面发丝描边（渐变收尾色） */
    val hairline = Color(0xFFE9EFF8)

    /** 默认圆角 */
    val defaultCorner: Dp = 22.dp

    /** 卡片圆角（单卡片样式） */
    val cardCorner: Dp = 20.dp
}

/**
 * 凸起拟态表面：右下柔影 + 左上→右下渐变发丝描边。
 *
 * @param shape 表面形状（决定阴影与描边轮廓）
 * @param elevation 阴影高度，越大越"浮"
 * @param base 表面底色，默认与背景同色（拟态精髓）
 */
fun Modifier.neuRaised(
    shape: Shape = RoundedCornerShape(Neu.defaultCorner),
    elevation: Dp = 6.dp,
    base: Color = Neu.base,
): Modifier = this
    .shadow(
        elevation = elevation,
        shape = shape,
        clip = false,
        ambientColor = Neu.shadow,
        spotColor = Neu.shadow,
    )
    .background(base, shape)
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(listOf(Neu.highlight, Neu.hairline)),
        shape = shape,
    )

/**
 * 凹陷拟态表面：深一档底色 + 反向渐变描边（左上暗、右下亮），
 * 模拟光线射入凹槽的内阴影效果。常用于选中态、输入槽、图标槽。
 */
fun Modifier.neuPressed(
    shape: Shape = RoundedCornerShape(Neu.defaultCorner),
    base: Color = Neu.pressed,
): Modifier = this
    .background(base, shape)
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(listOf(Neu.pressedDark, Neu.highlight)),
        shape = shape,
    )

/**
 * 单卡片列表容器：纵向排布若干 [ItemCard]，卡片之间保留固定间距。
 *
 * 「每个功能一张卡片」的标准写法：
 * ```
 * CardList {
 *     ItemCard { ArrowPreference(title = "功能 A", onClick = { ... }) }
 *     ItemCard { ArrowPreference(title = "功能 B", onClick = { ... }) }
 * }
 * ```
 */
@Composable
fun CardList(
    modifier: Modifier = Modifier,
    spacing: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/**
 * 单个功能卡片：纯白拟态凸起表面 + 统一内边距。
 *
 * @param horizontalPadding 左右内边距
 * @param verticalPadding 上下内边距（miuix 偏好项自带纵向内边距，默认已足够）
 */
@Composable
fun ItemCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Neu.cardCorner),
    elevation: Dp = 5.dp,
    horizontalPadding: Dp = 16.dp,
    verticalPadding: Dp = 5.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .neuRaised(shape, elevation)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        content = content,
    )
}

/**
 * 单卡片箭头偏好项：把 miuix 的 [ArrowPreference] 单独包进一张 [ItemCard]，
 * 用于「一个功能一张卡片」的列表（配合 [CardList] 使用）。
 *
 * 参数与 [ArrowPreference] 对齐，调用方写法基本不变。
 */
@Composable
fun CardArrowPreference(
    title: String,
    summary: String? = null,
    onClick: () -> Unit = {},
) {
    ItemCard {
        ArrowPreference(
            title = title,
            summary = summary,
            onClick = onClick,
        )
    }
}

/**
 * 单卡片开关偏好项：与 [CardArrowPreference] 同理，把开关包进独立卡片。
 */
@Composable
fun CardSwitchPreference(
    title: String,
    checked: Boolean,
    summary: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    ItemCard {
        SwitchPreference(
            title = title,
            summary = summary,
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

package com.xse.sesame.ui.miuix

import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 未来拟态纯白（Futuristic Neumorphic Pure White）配色。
 *
 * 设计要点：
 * - **纯白打底**：背景与卡片同为 0xFFFFFF，层次只由拟态阴影（见 [Neu]）与极浅冷灰容器区分；
 * - **冷调文字**：近黑偏蓝的冷灰，纯白底上不刺眼；
 * - **电光蓝主色 + 量子青辅色**：低饱和表面 + 高饱和强调色，形成"未来感"而非"暖奶白"。
 *
 * 不变式：本主题为固定浅色主题，不跟随系统深色（[MiuixBaseActivity.setAppContent] 强制 Light）。
 */
object PureWhiteTheme {

    /* ---------- 底色：纯白打底，层次由极浅冷灰承担 ---------- */
    val background = Color(0xFFFFFFFF)
    val surface = Color(0xFFFFFFFF)
    val surfaceVariant = Color(0xFFF3F7FC)
    val surfaceContainer = Color(0xFFF4F8FD)
    val surfaceContainerHigh = Color(0xFFEDF3FA)
    val surfaceContainerHighest = Color(0xFFE5EDF7)

    /* ---------- 文字：冷调近黑 ---------- */
    val onBackground = Color(0xFF0F141C)
    val onSurface = Color(0xFF1B2231)
    val onSurfaceSecondary = Color(0xFF535E73)
    val onSurfaceVariantSummary = Color(0xFF7B8698)
    val onSurfaceVariantActions = Color(0xFF98A3B8)

    /* ---------- 主色：电光蓝 ---------- */
    val primary = Color(0xFF7C5CFF)
    val onPrimary = Color(0xFFFFFFFF)
    val primaryVariant = Color(0xFF5A3ED6)
    val primaryContainer = Color(0xFFF0EBFF)
    val onPrimaryContainer = Color(0xFF2A1B7A)

    /* ---------- 辅色：量子青 ---------- */
    val secondary = Color(0xFFF56A8C)
    val onSecondary = Color(0xFFFFFFFF)
    val secondaryVariant = Color(0xFFD14E74)
    val secondaryContainer = Color(0xFFFEE8EE)
    val onSecondaryContainer = Color(0xFF6E1230)
    val secondaryContainerVariant = Color(0xFFFBD6E0)
    val onSecondaryContainerVariant = Color(0xFF8A2A46)

    /* ---------- 第三级容器：淡紫，用于强调块 ---------- */
    val tertiaryContainer = Color(0xFFE0F7F4)
    val onTertiaryContainer = Color(0xFF0A4A44)
    val tertiaryContainerVariant = Color(0xFFCCEFEB)

    /* ---------- 错误 / 危险 ---------- */
    val error = Color(0xFFE5484D)
    val onError = Color(0xFFFFFFFF)
    val errorContainer = Color(0xFFFFECEC)
    val onErrorContainer = Color(0xFF5C1418)

    /* ---------- 禁用态 ---------- */
    val disabledPrimary = Color(0xFFEDF2F9)
    val disabledOnPrimary = Color(0xFFB6C0D0)
    val disabledPrimaryButton = Color(0xFFF0F4FA)
    val disabledOnPrimaryButton = Color(0xFFB0BAC9)
    val disabledPrimarySlider = Color(0xFFE6ECF5)
    val disabledSecondary = Color(0xFFEDF3F3)
    val disabledOnSecondary = Color(0xFFAEBAC0)
    val disabledSecondaryVariant = Color(0xFFE9EFEF)
    val disabledOnSecondaryVariant = Color(0xFFA6B2B6)
    val disabledOnSurface = Color(0xFFBCC5D3)

    /* ---------- 描边 / 分隔 / 遮罩 ---------- */
    val outline = Color(0xFFE2E9F2)
    val dividerLine = Color(0xFFEDF2F8)
    val windowDimming = Color(0x66000000)

    /* ---------- 滑块 ---------- */
    val sliderKeyPoint = Color(0xFFFFFFFF)
    val sliderKeyPointForeground = Color(0xFFDCE5F1)
    val sliderBackground = Color(0xFFE9F0F8)

    /** 组装为 miuix 主题色板 */
    fun colors(): Colors = lightColorScheme().copy(
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceSecondary = onSurfaceSecondary,
        onSurfaceVariantSummary = onSurfaceVariantSummary,
        onSurfaceVariantActions = onSurfaceVariantActions,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,

        primary = primary,
        onPrimary = onPrimary,
        primaryVariant = primaryVariant,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,

        secondary = secondary,
        onSecondary = onSecondary,
        secondaryVariant = secondaryVariant,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        secondaryContainerVariant = secondaryContainerVariant,
        onSecondaryContainerVariant = onSecondaryContainerVariant,

        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        tertiaryContainerVariant = tertiaryContainerVariant,

        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,

        disabledPrimary = disabledPrimary,
        disabledOnPrimary = disabledOnPrimary,
        disabledPrimaryButton = disabledPrimaryButton,
        disabledOnPrimaryButton = disabledOnPrimaryButton,
        disabledPrimarySlider = disabledPrimarySlider,
        disabledSecondary = disabledSecondary,
        disabledOnSecondary = disabledOnSecondary,
        disabledSecondaryVariant = disabledSecondaryVariant,
        disabledOnSecondaryVariant = disabledOnSecondaryVariant,
        disabledOnSurface = disabledOnSurface,

        outline = outline,
        dividerLine = dividerLine,
        windowDimming = windowDimming,

        sliderKeyPoint = sliderKeyPoint,
        sliderKeyPointForeground = sliderKeyPointForeground,
        sliderBackground = sliderBackground,
    )
}

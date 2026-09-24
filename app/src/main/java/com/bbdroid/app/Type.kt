package com.bbdroid.app

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 排版：中文走系统 Noto Sans CJK SC（Android 系统自带），拉丁/数字走系统 Roboto。
 * 如需严格使用 Inter（拉丁/数字），把 Inter TTF 放入 res/font 后替换 FontFamily 即可。
 * 所有样式统一开启等宽数字（tnum），进度/速度/时间不跳字。
 */
private val AppFontFamily = FontFamily.SansSerif

private fun style(
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    letterSpacing: Float = 0f,
): TextStyle = TextStyle(
    fontFamily = AppFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
    fontFeatureSettings = "tnum",
)

val AppTypography = Typography(
    titleLarge = style(FontWeight.SemiBold, 22, 28),
    titleMedium = style(FontWeight.SemiBold, 16, 24, 0.15f),
    titleSmall = style(FontWeight.SemiBold, 14, 20, 0.1f),
    bodyLarge = style(FontWeight.Normal, 16, 24, 0.5f),
    bodyMedium = style(FontWeight.Normal, 14, 20, 0.25f),
    bodySmall = style(FontWeight.Normal, 12, 16, 0.4f),
    labelLarge = style(FontWeight.Medium, 14, 20, 0.1f),
    labelMedium = style(FontWeight.Medium, 12, 16, 0.5f),
    labelSmall = style(FontWeight.Medium, 11, 16, 0.5f),
)

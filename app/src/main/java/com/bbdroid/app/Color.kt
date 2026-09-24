package com.bbdroid.app

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 品牌亮粉：仅用于不承载文字、但需要品牌识别的图形场景，
 * 例如进度条、NavigationBar 激活指示器。承载文字的交互态一律用 primary(#D22B58)。
 */
val BrandPink = Color(0xFFFB7299)

// ---------- 浅色配色（B站粉压深，primary #D22B58 对白字对比 4.97:1，满足 WCAG AA） ----------
val LightColors = lightColorScheme(
    primary = Color(0xFFD22B58),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E2),
    onPrimaryContainer = Color(0xFF3E001D),
    secondary = Color(0xFF74575F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF6DDE4),
    onSecondaryContainer = Color(0xFF31101D),
    tertiary = Color(0xFF825500),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDBCA),
    onTertiaryContainer = Color(0xFF2C1600),
    surface = Color(0xFFFFF8F8),
    surfaceContainerLow = Color(0xFFFCF0F2),
    surfaceContainer = Color(0xFFF6EBED),
    surfaceContainerHigh = Color(0xFFF3E5E9),
    surfaceContainerHighest = Color(0xFFEEE0E3),
    onSurface = Color(0xFF201A1B),
    onSurfaceVariant = Color(0xFF524346),
    outline = Color(0xFF847377),
    outlineVariant = Color(0xFFD5C2C6),
    inverseSurface = Color(0xFF352F30),
    inverseOnSurface = Color(0xFFFAEEEF),
    inversePrimary = Color(0xFFFFB1C9),
    error = Color(0xFFB3213D),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBE9EC),
    onErrorContainer = Color(0xFF8A1730),
)

// ---------- 深色配色（与浅色 hue 保持一致） ----------
val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB1C9),
    onPrimary = Color(0xFF650032),
    primaryContainer = Color(0xFF7D2649),
    onPrimaryContainer = Color(0xFFFED9E4),
    secondary = Color(0xFFDFBEC7),
    onSecondary = Color(0xFF4A2F38),
    secondaryContainer = Color(0xFF5B3F48),
    onSecondaryContainer = Color(0xFFFCDAE3),
    tertiary = Color(0xFFF5BD6F),
    onTertiary = Color(0xFF422B00),
    tertiaryContainer = Color(0xFF5E402C),
    onTertiaryContainer = Color(0xFFFFDBC6),
    surface = Color(0xFF181213),
    surfaceContainerLow = Color(0xFF201A1C),
    surfaceContainer = Color(0xFF241E20),
    surfaceContainerHigh = Color(0xFF2E282A),
    surfaceContainerHighest = Color(0xFF393335),
    onSurface = Color(0xFFE8E1E3),
    onSurfaceVariant = Color(0xFFD4C2C7),
    outline = Color(0xFF9D8D91),
    outlineVariant = Color(0xFF514347),
    inverseSurface = Color(0xFFE8E1E3),
    inverseOnSurface = Color(0xFF352F30),
    inversePrimary = Color(0xFFD22B58),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

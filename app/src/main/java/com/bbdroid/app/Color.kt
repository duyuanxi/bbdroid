package com.bbdroid.app

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Bilibili brand accent for download progress.
val BrandPink = Color(0xFFFB7299)

// Neutral grouped surfaces and system-blue actions.
val LightColors = lightColorScheme(
    background = Color(0xFFF2F2F7),
    primary = Color(0xFF0066CC),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9EAFF),
    onPrimaryContainer = Color(0xFF002B57),
    secondary = Color(0xFF54657A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE7EBF2),
    onSecondaryContainer = Color(0xFF1C2A3A),
    tertiary = Color(0xFF825500),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDBCA),
    onTertiaryContainer = Color(0xFF2C1600),
    surface = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F2F7),
    surfaceContainer = Color(0xFFEAEAEE),
    surfaceContainerHigh = Color(0xFFE5E5EA),
    surfaceContainerHighest = Color(0xFFDDDDE3),
    onSurface = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF62626A),
    outline = Color(0xFF8E8E93),
    outlineVariant = Color(0xFFD1D1D6),
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
    background = Color(0xFF000000),
    primary = Color(0xFF9CCAFF),
    onPrimary = Color(0xFF00315D),
    primaryContainer = Color(0xFF194A78),
    onPrimaryContainer = Color(0xFFD9EAFF),
    secondary = Color(0xFFB7C5D8),
    onSecondary = Color(0xFF243245),
    secondaryContainer = Color(0xFF344155),
    onSecondaryContainer = Color(0xFFE0E8F4),
    tertiary = Color(0xFFF5BD6F),
    onTertiary = Color(0xFF422B00),
    tertiaryContainer = Color(0xFF5E402C),
    onTertiaryContainer = Color(0xFFFFDBC6),
    surface = Color(0xFF1C1C1E),
    surfaceContainerLow = Color(0xFF242426),
    surfaceContainer = Color(0xFF2C2C2E),
    surfaceContainerHigh = Color(0xFF323234),
    surfaceContainerHighest = Color(0xFF3A3A3C),
    onSurface = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFFB9B9C2),
    outline = Color(0xFF8E8E93),
    outlineVariant = Color(0xFF48484A),
    inverseSurface = Color(0xFFF2F2F7),
    inverseOnSurface = Color(0xFF352F30),
    inversePrimary = Color(0xFFD22B58),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

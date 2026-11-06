package com.bbdroid.app

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*

enum class ThemeMode { SYSTEM, LIGHT, DARK }
data class GlassConfig(val enabled: Boolean = true, val intensity: Float = 0.5f)
val LocalGlassEnabled = staticCompositionLocalOf { true }
val LocalGlassIntensity = staticCompositionLocalOf { 0.5f }
val LocalGlassDark = staticCompositionLocalOf { false }
val StandardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

@Composable
fun BbdroidTheme(mode: ThemeMode = ThemeMode.SYSTEM, glass: GlassConfig = GlassConfig(), content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    CompositionLocalProvider(LocalGlassEnabled provides glass.enabled, LocalGlassIntensity provides glass.intensity.coerceIn(0f, 1f), LocalGlassDark provides dark) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
